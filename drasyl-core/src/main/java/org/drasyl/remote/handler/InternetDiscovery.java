/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.remote.handler;

import com.google.common.cache.CacheBuilder;
import com.google.common.primitives.Ints;
import com.google.protobuf.MessageLite;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.remote.protocol.Nonce;
import org.drasyl.remote.protocol.Protocol.Acknowledgement;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.Protocol.Discovery;
import org.drasyl.remote.protocol.Protocol.Unite;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.drasyl.util.Pair;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.remote.protocol.Protocol.MessageType.ACKNOWLEDGEMENT;
import static org.drasyl.remote.protocol.Protocol.MessageType.APPLICATION;
import static org.drasyl.remote.protocol.Protocol.MessageType.DISCOVERY;
import static org.drasyl.remote.protocol.Protocol.MessageType.UNITE;
import static org.drasyl.util.LoggingUtil.sanitizeLogArg;
import static org.drasyl.util.RandomUtil.randomLong;

/**
 * This handler performs the following tasks, which help to communicate with nodes located in other
 * networks:
 * <ul>
 * <li>Joins one or more super peers or acts itself as a super peer (super peers act as registries of available nodes on the network. they can be used as message relays and help to traverse NATs).</li>
 * <li>Tracks which nodes are being communicated with and tries to establish direct connections to these nodes with the help of a super peer.</li>
 * <li>Routes messages to the recipient. If no route is known, the message is relayed to a super peer (our default gateway).</li>
 * </ul>
 */
@SuppressWarnings({ "java:S110", "java:S1192" })
public class InternetDiscovery extends SimpleDuplexHandler<RemoteEnvelope<? extends MessageLite>, RemoteEnvelope<Application>, Address> {
    private static final Logger LOG = LoggerFactory.getLogger(InternetDiscovery.class);
    private static final Object path = InternetDiscovery.class;
    private final Map<Nonce, Ping> openPingsCache;
    private final Map<Pair<IdentityPublicKey, IdentityPublicKey>, Boolean> uniteAttemptsCache;
    private final Map<IdentityPublicKey, Peer> peers;
    private final Set<IdentityPublicKey> directConnectionPeers;
    private final Set<IdentityPublicKey> superPeers;
    private Disposable heartbeatDisposable;
    private IdentityPublicKey bestSuperPeer;

    public InternetDiscovery(final DrasylConfig config) {
        openPingsCache = CacheBuilder.newBuilder()
                .maximumSize(config.getRemotePingMaxPeers())
                .expireAfterWrite(config.getRemotePingTimeout())
                .<Nonce, Ping>build()
                .asMap();
        directConnectionPeers = new HashSet<>();
        if (config.getRemoteUniteMinInterval().toMillis() > 0) {
            uniteAttemptsCache = CacheBuilder.newBuilder()
                    .maximumSize(1_000)
                    .expireAfterWrite(config.getRemoteUniteMinInterval())
                    .<Pair<IdentityPublicKey, IdentityPublicKey>, Boolean>build()
                    .asMap();
        }
        else {
            uniteAttemptsCache = null;
        }
        peers = new ConcurrentHashMap<>();
        superPeers = config.getRemoteSuperPeerEndpoints().stream().map(Endpoint::getIdentityPublicKey).collect(Collectors.toSet());
    }

    @SuppressWarnings("java:S2384")
    InternetDiscovery(final Map<Nonce, Ping> openPingsCache,
                      final Map<Pair<IdentityPublicKey, IdentityPublicKey>, Boolean> uniteAttemptsCache,
                      final Map<IdentityPublicKey, Peer> peers,
                      final Set<IdentityPublicKey> directConnectionPeers,
                      final Set<IdentityPublicKey> superPeers,
                      final IdentityPublicKey bestSuperPeer) {
        this.openPingsCache = openPingsCache;
        this.uniteAttemptsCache = uniteAttemptsCache;
        this.directConnectionPeers = directConnectionPeers;
        this.peers = peers;
        this.superPeers = superPeers;
        this.bestSuperPeer = bestSuperPeer;
    }

    @Override
    public void onEvent(final HandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        if (event instanceof NodeUpEvent) {
            startHeartbeat(ctx);
        }
        else if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            stopHeartbeat();
            openPingsCache.clear();
            uniteAttemptsCache.clear();
            removeAllPeers(ctx);
        }

        // passthrough event
        ctx.passEvent(event, future);
    }

    synchronized void startHeartbeat(final HandlerContext ctx) {
        if (heartbeatDisposable == null) {
            LOG.debug("Start heartbeat scheduler");
            final long pingInterval = ctx.config().getRemotePingInterval().toMillis();
            heartbeatDisposable = ctx.independentScheduler()
                    .schedulePeriodicallyDirect(() -> doHeartbeat(ctx), randomLong(pingInterval), pingInterval, MILLISECONDS);
        }
    }

    synchronized void stopHeartbeat() {
        if (heartbeatDisposable != null) {
            LOG.debug("Stop heartbeat scheduler");
            heartbeatDisposable.dispose();
            heartbeatDisposable = null;
        }
    }

    /**
     * This method sends ping messages to super peer and direct connection peers.
     *
     * @param ctx handler's context
     */
    void doHeartbeat(final HandlerContext ctx) {
        removeStalePeers(ctx);
        pingSuperPeers(ctx);
        pingDirectConnectionPeers(ctx);
    }

    /**
     * This method removes stale peers from the peer list, that not respond to ping messages.
     *
     * @param ctx the handler context
     */
    private void removeStalePeers(final HandlerContext ctx) {
        // check lastContactTimes
        new HashMap<>(peers).forEach(((publicKey, peer) -> {
            if (!peer.hasControlTraffic(ctx.config())) {
                LOG.debug("Last contact from {} is {}ms ago. Remove peer.", () -> publicKey, () -> System.currentTimeMillis() - peer.getLastInboundControlTrafficTime());
                if (superPeers.contains(publicKey)) {
                    ctx.peersManager().removeSuperPeerAndPath(publicKey, path);
                }
                else {
                    ctx.peersManager().removeChildrenAndPath(publicKey, path);
                }
                peers.remove(publicKey);
                directConnectionPeers.remove(publicKey);
            }
        }));
    }

    /**
     * If the node has configured super peers, a ping message is sent to them.
     *
     * @param ctx handler's context
     */
    private void pingSuperPeers(final HandlerContext ctx) {
        if (ctx.config().isRemoteSuperPeerEnabled()) {
            for (final Endpoint endpoint : ctx.config().getRemoteSuperPeerEndpoints()) {
                final InetSocketAddressWrapper address = new InetSocketAddressWrapper(endpoint.getHost(), endpoint.getPort());
                sendPing(ctx, endpoint.getIdentityPublicKey(), address, new CompletableFuture<>()).exceptionally(e -> {
                    //noinspection unchecked
                    LOG.warn("Unable to send ping for super peer `{}` to `{}`", endpoint::getIdentityPublicKey, () -> address, () -> e);
                    return null;
                });
            }
        }
    }

    /**
     * Sends ping messages to all peers with whom a direct connection should be kept open. Removes
     * peers that have not had application-level communication with you for a while.
     *
     * @param ctx handler's context
     */
    private void pingDirectConnectionPeers(final HandlerContext ctx) {
        for (final IdentityPublicKey publicKey : new HashSet<>(directConnectionPeers)) {
            final Peer peer = peers.get(publicKey);
            final InetSocketAddressWrapper address = peer.getAddress();
            if (address != null && peer.hasApplicationTraffic(ctx.config())) {
                sendPing(ctx, publicKey, address, new CompletableFuture<>()).exceptionally(e -> {
                    //noinspection unchecked
                    LOG.warn("Unable to send ping for peer `{}` to `{}`", () -> publicKey, () -> address, () -> e);
                    return null;
                });
            }
            // remove trivial communications, that does not send any user generated messages
            else {
                LOG.debug("Last application communication to {} is {}ms ago. Remove peer.", () -> publicKey, () -> System.currentTimeMillis() - peer.getLastApplicationTrafficTime());
                ctx.peersManager().removeChildrenAndPath(publicKey, path);
                directConnectionPeers.remove(publicKey);
            }
        }
    }

    private void removeAllPeers(final HandlerContext ctx) {
        new HashMap<>(peers).forEach(((publicKey, peer) -> {
            if (superPeers.contains(publicKey)) {
                ctx.peersManager().removeSuperPeerAndPath(publicKey, path);
            }
            else {
                ctx.peersManager().removeChildrenAndPath(publicKey, path);
            }
            peers.remove(publicKey);
            directConnectionPeers.remove(publicKey);
        }));
    }

    @Override
    protected void matchedOutbound(final HandlerContext ctx,
                                   final Address recipient,
                                   final RemoteEnvelope<Application> msg,
                                   final CompletableFuture<Void> future) {
        try {
            if (recipient instanceof IdentityPublicKey) {
                // record communication to keep active connections alive
                if (directConnectionPeers.contains(recipient)) {
                    final Peer peer = peers.computeIfAbsent((IdentityPublicKey) recipient, key -> new Peer());
                    peer.applicationTrafficOccurred();
                }

                if (!processMessage(ctx, (IdentityPublicKey) recipient, msg, future)) {
                    // passthrough message
                    ctx.passOutbound(recipient, msg, future);
                }
            }
            else {
                // passthrough message
                ctx.passOutbound(recipient, msg, future);
            }
        }
        catch (final IOException e) {
            LOG.warn("Unable to read '{}'.", () -> sanitizeLogArg(msg), () -> e);
            future.completeExceptionally(new Exception("Message could not be read.", e));
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    /**
     * This method tries to find the cheapest path to the {@code recipient}. If we're a relay, we
     * additionally try to initiate a rendezvous between the two corresponding nodes.
     *
     * @param ctx       the handler context
     * @param recipient the recipient of the message
     * @param msg       the message
     * @param future    the future
     * @return {@code true} if message could be processed. Otherwise {@code false}
     * @throws IOException if public header of {@code msg} cannot be read
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean processMessage(final HandlerContext ctx,
                                   final IdentityPublicKey recipient,
                                   final RemoteEnvelope<? extends MessageLite> msg,
                                   final CompletableFuture<Void> future) throws IOException {
        final Peer recipientPeer = peers.get(recipient);

        final Peer superPeerPeer;
        if (bestSuperPeer != null) {
            superPeerPeer = peers.get(bestSuperPeer);
        }
        else {
            superPeerPeer = null;
        }

        if (recipientPeer != null && recipientPeer.getAddress() != null && recipientPeer.isReachable(ctx.config())) {
            final InetSocketAddressWrapper recipientSocketAddress = recipientPeer.getAddress();
            final Peer senderPeer = peers.get(msg.getSender());

            // rendezvous? I'm a super peer?
            if (senderPeer != null && superPeerPeer == null && senderPeer.getAddress() != null) {
                final InetSocketAddressWrapper senderSocketAddress = senderPeer.getAddress();
                final IdentityPublicKey msgSender = msg.getSender();
                final IdentityPublicKey msgRecipient = msg.getRecipient();
                LOG.trace("Relay message from {} to {}.", msgSender, recipient);

                if (shouldTryUnite(msgSender, msgRecipient)) {
                    ctx.independentScheduler().scheduleDirect(() -> sendUnites(ctx, msgSender, msgRecipient, recipientSocketAddress, senderSocketAddress));
                }
            }

            LOG.trace("Send message to {} to {}.", recipient, recipientSocketAddress);
            ctx.passOutbound(recipientSocketAddress, msg, future);

            return true;
        }
        else if (superPeerPeer != null) {
            final InetSocketAddressWrapper superPeerSocketAddress = superPeerPeer.getAddress();
            LOG.trace("No connection to {}. Send message to super peer.", recipient);
            ctx.passOutbound(superPeerSocketAddress, msg, future);

            return true;
        }
        else {
            return false;
        }
    }

    /**
     * This method initiates a unite between the {@code sender} and {@code recipient}, by sending
     * both the required information to establish a direct (P2P) connection.
     *
     * @param ctx          the handler context
     * @param senderKey    the sender's public key
     * @param recipientKey the recipient's public key
     * @param recipient    the recipient socket address
     * @param sender       the sender socket address
     */
    private static void sendUnites(final HandlerContext ctx,
                                   final IdentityPublicKey senderKey,
                                   final IdentityPublicKey recipientKey,
                                   final InetSocketAddressWrapper recipient,
                                   final InetSocketAddressWrapper sender) {
        // send recipient's information to sender
        final RemoteEnvelope<Unite> senderRendezvousEnvelope = RemoteEnvelope.unite(ctx.config().getNetworkId(), ctx.identity().getIdentityPublicKey(), ctx.identity().getProofOfWork(), senderKey, recipientKey, recipient);
        LOG.trace("Send {} to {}", senderRendezvousEnvelope, sender);
        ctx.passOutbound(sender, senderRendezvousEnvelope, new CompletableFuture<>()).exceptionally(e -> {
            //noinspection unchecked
            LOG.warn("Unable to send unite message for peer `{}` to `{}`", () -> senderKey, () -> sender, () -> e);
            return null;
        });

        // send sender's information to recipient
        final RemoteEnvelope<Unite> recipientRendezvousEnvelope = RemoteEnvelope.unite(ctx.config().getNetworkId(), ctx.identity().getIdentityPublicKey(), ctx.identity().getProofOfWork(), recipientKey, senderKey, sender);
        LOG.trace("Send {} to {}", recipientRendezvousEnvelope, recipient);
        ctx.passOutbound(recipient, recipientRendezvousEnvelope, new CompletableFuture<>()).exceptionally(e -> {
            //noinspection unchecked
            LOG.warn("Unable to send unite message for peer `{}` to `{}`", () -> recipientKey, () -> recipient, () -> e);
            return null;
        });
    }

    private synchronized boolean shouldTryUnite(final IdentityPublicKey sender,
                                                final IdentityPublicKey recipient) {
        final Pair<IdentityPublicKey, IdentityPublicKey> key;
        if (sender.hashCode() > recipient.hashCode()) {
            key = Pair.of(sender, recipient);
        }
        else {
            key = Pair.of(recipient, sender);
        }
        return uniteAttemptsCache != null && uniteAttemptsCache.putIfAbsent(key, TRUE) == null;
    }

    @Override
    protected void matchedInbound(final HandlerContext ctx,
                                  final Address sender,
                                  final RemoteEnvelope<? extends MessageLite> msg,
                                  final CompletableFuture<Void> future) {
        try {
            if (sender instanceof InetSocketAddressWrapper && msg.getRecipient() != null) {
                // This message is for us and we will fully decode it
                if (ctx.identity().getIdentityPublicKey().equals(msg.getRecipient())) {
                    handleMessage(ctx, (InetSocketAddressWrapper) sender, msg, future);
                }
                else if (!ctx.config().isRemoteSuperPeerEnabled()) {
                    if (!processMessage(ctx, msg.getRecipient(), msg, future)) {
                        // passthrough message
                        ctx.passInbound(sender, msg, future);
                    }
                }
                else if (LOG.isDebugEnabled()) {
                    LOG.debug("We're not a super peer. Message `{}` from `{}` to `{}` for relaying was dropped.", msg, sender, msg.getRecipient());
                }
            }
            else {
                // passthrough message
                ctx.passInbound(sender, msg, future);
            }
        }
        catch (final IOException e) {
            LOG.warn("Unable to read '{}'.", () -> sanitizeLogArg(msg), () -> e);
            future.completeExceptionally(new Exception("Message could not be read.", e));
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(final HandlerContext ctx,
                               final InetSocketAddressWrapper sender,
                               final RemoteEnvelope<? extends MessageLite> msg,
                               final CompletableFuture<Void> future) throws IOException {
        if (msg.getPrivateHeader().getType() == DISCOVERY) {
            handlePing(ctx, sender, (RemoteEnvelope<Discovery>) msg, future);
        }
        else if (msg.getPrivateHeader().getType() == ACKNOWLEDGEMENT) {
            handlePong(ctx, sender, (RemoteEnvelope<Acknowledgement>) msg, future);
        }
        else if (msg.getPrivateHeader().getType() == UNITE && superPeers.contains(msg.getSender())) {
            handleUnite(ctx, (RemoteEnvelope<Unite>) msg, future);
        }
        else if (msg.getPrivateHeader().getType() == APPLICATION) {
            handleApplication(ctx, (RemoteEnvelope<Application>) msg, future);
        }
        else {
            msg.retain();
            // passthrough message
            ctx.passInbound(sender, msg, future);
        }
    }

    private void handlePing(final HandlerContext ctx,
                            final InetSocketAddressWrapper sender,
                            final RemoteEnvelope<Discovery> msg,
                            final CompletableFuture<Void> future) throws IOException {
        final IdentityPublicKey envelopeSender = msg.getSender();
        final Nonce id = msg.getNonce();
        final Discovery body = msg.getBodyAndRelease();
        final boolean childrenJoin = body.getChildrenTime() > 0;
        LOG.trace("Got {} from {}", msg, sender);
        final Peer peer = peers.computeIfAbsent(envelopeSender, key -> new Peer());
        peer.setAddress(sender);
        peer.inboundControlTrafficOccurred();

        if (childrenJoin) {
            peer.inboundPingOccurred();
            // store peer information
            if (LOG.isDebugEnabled() && !ctx.peersManager().getChildren().contains(envelopeSender) && !ctx.peersManager().getPaths(envelopeSender).contains(path)) {
                LOG.debug("PING! Add {} as children", envelopeSender);
            }
            ctx.peersManager().addPathAndChildren(envelopeSender, path);
        }

        // reply with pong
        final int networkId = ctx.config().getNetworkId();
        final IdentityPublicKey myPublicKey = ctx.identity().getIdentityPublicKey();
        final ProofOfWork myProofOfWork = ctx.identity().getProofOfWork();
        final RemoteEnvelope<Acknowledgement> responseEnvelope = RemoteEnvelope.acknowledgement(networkId, myPublicKey, myProofOfWork, envelopeSender, id);
        LOG.trace("Send {} to {}", responseEnvelope, sender);
        ctx.passOutbound(sender, responseEnvelope, future);
    }

    private void handlePong(final HandlerContext ctx,
                            final InetSocketAddressWrapper sender,
                            final RemoteEnvelope<Acknowledgement> msg,
                            final CompletableFuture<Void> future) throws IOException {
        final Acknowledgement body = msg.getBodyAndRelease();
        final Nonce correspondingId = requireNonNull(Nonce.of(body.getCorrespondingId()));
        final IdentityPublicKey envelopeSender = msg.getSender();
        LOG.trace("Got {} from {}", msg, sender);
        final Ping ping = openPingsCache.remove(correspondingId);
        if (ping != null) {
            final Peer peer = peers.computeIfAbsent(envelopeSender, key -> new Peer());
            peer.setAddress(sender);
            peer.inboundControlTrafficOccurred();
            peer.inboundPongOccurred(ping);
            if (superPeers.contains(msg.getSender())) {
                //noinspection unchecked
                LOG.trace("Latency to super peer `{}` ({}): {}ms", () -> envelopeSender, peer.getAddress()::getHostName, peer::getLatency);
                determineBestSuperPeer();

                // store peer information
                if (LOG.isDebugEnabled() && !ctx.peersManager().getChildren().contains(envelopeSender) && !ctx.peersManager().getPaths(envelopeSender).contains(path)) {
                    LOG.debug("PONG! Add {} as super peer", envelopeSender);
                }
                ctx.peersManager().addPathAndSuperPeer(envelopeSender, path);
            }
            else {
                // store peer information
                if (LOG.isDebugEnabled() && !ctx.peersManager().getPaths(envelopeSender).contains(path)) {
                    LOG.debug("PONG! Add {} as peer", envelopeSender);
                }
                ctx.peersManager().addPath(envelopeSender, path);
            }
        }
        future.complete(null);
    }

    private synchronized void determineBestSuperPeer() {
        long bestLatency = Long.MAX_VALUE;
        IdentityPublicKey newBestSuperPeer = null;
        for (final IdentityPublicKey superPeer : superPeers) {
            final Peer superPeerPeer = peers.get(superPeer);
            if (superPeerPeer != null) {
                final long latency = superPeerPeer.getLatency();
                if (bestLatency > latency) {
                    bestLatency = latency;
                    newBestSuperPeer = superPeer;
                }
            }
        }

        if (LOG.isDebugEnabled() && !Objects.equals(bestSuperPeer, newBestSuperPeer)) {
            LOG.debug("New best super peer ({}ms)! Replace `{}` with `{}`", bestLatency, bestSuperPeer, newBestSuperPeer);
        }

        bestSuperPeer = newBestSuperPeer;
    }

    private void handleUnite(final HandlerContext ctx,
                             final RemoteEnvelope<Unite> msg,
                             final CompletableFuture<Void> future) throws IOException {
        final Unite body = msg.getBodyAndRelease();
        final IdentityPublicKey publicKey = requireNonNull(IdentityPublicKey.of(body.getPublicKey()));

        final InetAddress address;
        if (body.getAddressV6().isEmpty()) {
            address = InetAddress.getByAddress(Ints.toByteArray(body.getAddressV4()));
        }
        else {
            address = InetAddress.getByAddress(body.getAddressV6().toByteArray());
        }
        final InetSocketAddressWrapper socketAddress = new InetSocketAddressWrapper(address, body.getPort());
        LOG.trace("Got {}", msg);

        final Peer peer = peers.computeIfAbsent(publicKey, key -> new Peer());
        peer.setAddress(socketAddress);
        peer.inboundControlTrafficOccurred();
        peer.applicationTrafficOccurred();
        directConnectionPeers.add(publicKey);
        sendPing(ctx, publicKey, socketAddress, future);
    }

    private void handleApplication(final HandlerContext ctx,
                                   final RemoteEnvelope<Application> msg,
                                   final CompletableFuture<Void> future) throws IOException {
        if (directConnectionPeers.contains(msg.getSender())) {
            final Peer peer = peers.computeIfAbsent(msg.getSender(), key -> new Peer());
            peer.applicationTrafficOccurred();
        }

        ctx.passInbound(msg.getSender(), msg, future);
    }

    private CompletableFuture<Void> sendPing(final HandlerContext ctx,
                                             final IdentityPublicKey recipient,
                                             final InetSocketAddressWrapper recipientAddress,
                                             final CompletableFuture<Void> future) {
        final int networkId = ctx.config().getNetworkId();
        final IdentityPublicKey sender = ctx.identity().getIdentityPublicKey();
        final ProofOfWork proofOfWork = ctx.identity().getProofOfWork();

        final boolean isChildrenJoin = superPeers.contains(recipient);
        RemoteEnvelope<Discovery> messageEnvelope = null;
        try {
            messageEnvelope = RemoteEnvelope.discovery(networkId, sender, proofOfWork, recipient, isChildrenJoin ? System.currentTimeMillis() : 0);
            openPingsCache.put(messageEnvelope.getNonce(), new Ping(recipientAddress));
            LOG.trace("Send {} to {}", messageEnvelope, recipientAddress);
            ctx.passOutbound(recipientAddress, messageEnvelope, future);
        }
        catch (final IOException e) {
            // should never occur as we build the message ourselves
            future.completeExceptionally(e);
            ReferenceCountUtil.safeRelease(messageEnvelope);
        }
        return future;
    }

    @SuppressWarnings("java:S2972")
    static class Peer {
        private InetSocketAddressWrapper address;
        private long lastInboundControlTrafficTime;
        private long lastInboundPongTime;
        private long lastApplicationTrafficTime;
        private long lastOutboundPingTime;

        Peer(final InetSocketAddressWrapper address,
             final long lastInboundControlTrafficTime,
             final long lastInboundPongTime,
             final long lastApplicationTrafficTime) {
            this.address = address;
            this.lastInboundControlTrafficTime = lastInboundControlTrafficTime;
            this.lastInboundPongTime = lastInboundPongTime;
            this.lastApplicationTrafficTime = lastApplicationTrafficTime;
        }

        public Peer() {
        }

        public InetSocketAddressWrapper getAddress() {
            return address;
        }

        public void setAddress(final InetSocketAddressWrapper address) {
            this.address = address;
        }

        /**
         * Returns the time when we last received a message from this peer. This includes all
         * message types ({@link Discovery}, {@link Acknowledgement}, {@link Application}, {@link
         * Unite}, etc.)
         */
        public long getLastInboundControlTrafficTime() {
            return lastInboundControlTrafficTime;
        }

        public void inboundControlTrafficOccurred() {
            lastInboundControlTrafficTime = System.currentTimeMillis();
        }

        public void inboundPongOccurred(final Ping ping) {
            lastInboundPongTime = System.currentTimeMillis();
            lastOutboundPingTime = Math.max(ping.getPingTime(), lastOutboundPingTime);
        }

        public void inboundPingOccurred() {
            lastInboundPongTime = System.currentTimeMillis();
        }

        /**
         * Returns the time when we last sent or received a application-level message to or from
         * this peer. This includes only message type {@link Application}
         */
        public long getLastApplicationTrafficTime() {
            return lastApplicationTrafficTime;
        }

        public void applicationTrafficOccurred() {
            lastApplicationTrafficTime = System.currentTimeMillis();
        }

        public boolean hasApplicationTraffic(final DrasylConfig config) {
            return lastApplicationTrafficTime >= System.currentTimeMillis() - config.getRemotePingCommunicationTimeout().toMillis();
        }

        public boolean hasControlTraffic(final DrasylConfig config) {
            return lastInboundControlTrafficTime >= System.currentTimeMillis() - config.getRemotePingTimeout().toMillis();
        }

        public boolean isReachable(final DrasylConfig config) {
            return lastInboundPongTime >= System.currentTimeMillis() - config.getRemotePingTimeout().toMillis();
        }

        public long getLatency() {
            return lastInboundPongTime - lastOutboundPingTime;
        }
    }

    @SuppressWarnings("java:S2972")
    public static class Ping {
        private final InetSocketAddressWrapper address;
        private final long pingTime;

        public Ping(final InetSocketAddressWrapper address) {
            this.address = requireNonNull(address);
            pingTime = System.currentTimeMillis();
        }

        public InetSocketAddressWrapper getAddress() {
            return address;
        }

        @Override
        public int hashCode() {
            return Objects.hash(address);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Ping ping = (Ping) o;
            return Objects.equals(address, ping.address);
        }

        @Override
        public String toString() {
            return "OpenPing{" +
                    "address=" + address +
                    '}';
        }

        public long getPingTime() {
            return pingTime;
        }
    }
}
