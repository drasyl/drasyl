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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Future;
import org.drasyl.DrasylAddress;
import org.drasyl.channel.AddPathAndChildrenEvent;
import org.drasyl.channel.AddPathAndSuperPeerEvent;
import org.drasyl.channel.AddPathEvent;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.RemoveChildrenAndPathEvent;
import org.drasyl.channel.RemoveSuperPeerAndPathEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.remote.protocol.AcknowledgementMessage;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.remote.protocol.DiscoveryMessage;
import org.drasyl.remote.protocol.FullReadMessage;
import org.drasyl.remote.protocol.Nonce;
import org.drasyl.remote.protocol.RemoteMessage;
import org.drasyl.remote.protocol.UniteMessage;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
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
public class InternetDiscovery extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(InternetDiscovery.class);
    private static final Object path = InternetDiscovery.class;
    private final Map<Nonce, Ping> openPingsCache;
    private final DrasylAddress myAddress;
    private final ProofOfWork myProofOfWork;
    private final Map<Pair<IdentityPublicKey, IdentityPublicKey>, Boolean> uniteAttemptsCache;
    private final Map<IdentityPublicKey, Peer> peers;
    private final Set<IdentityPublicKey> directConnectionPeers;
    private final Set<IdentityPublicKey> superPeers;
    private final Duration pingInterval;
    private final Duration pingTimeout;
    private final Duration pingCommunicationTimeout;
    private final boolean superPeerEnabled;
    private final Set<Endpoint> superPeerEndpoints;
    private final int networkId;
    private Future<?> heartbeatDisposable;
    private IdentityPublicKey bestSuperPeer;

    @SuppressWarnings({ "java:S107", "java:S2384" })
    public InternetDiscovery(final int networkId,
                             final int pingMaxPeers,
                             final Duration pingInterval,
                             final Duration pingTimeout,
                             final Duration pingCommunicationTimeout,
                             final boolean superPeerEnabled,
                             final Set<Endpoint> superPeerEndpoints,
                             final Duration uniteMinInterval,
                             final DrasylAddress myAddress,
                             final ProofOfWork myProofOfWork) {
        this.pingInterval = requireNonNull(pingInterval);
        this.pingTimeout = requireNonNull(pingTimeout);
        this.pingCommunicationTimeout = requireNonNull(pingCommunicationTimeout);
        this.superPeerEnabled = superPeerEnabled;
        this.superPeerEndpoints = requireNonNull(superPeerEndpoints);
        this.networkId = networkId;
        openPingsCache = CacheBuilder.newBuilder()
                .maximumSize(pingMaxPeers)
                .expireAfterWrite(pingTimeout)
                .<Nonce, Ping>build()
                .asMap();
        this.myAddress = requireNonNull(myAddress);
        this.myProofOfWork = requireNonNull(myProofOfWork);
        directConnectionPeers = new HashSet<>();
        if (uniteMinInterval.toMillis() > 0) {
            uniteAttemptsCache = CacheBuilder.newBuilder()
                    .maximumSize(1_000)
                    .expireAfterWrite(uniteMinInterval)
                    .<Pair<IdentityPublicKey, IdentityPublicKey>, Boolean>build()
                    .asMap();
        }
        else {
            uniteAttemptsCache = null;
        }
        peers = new ConcurrentHashMap<>();
        superPeers = superPeerEndpoints.stream().map(Endpoint::getIdentityPublicKey).collect(Collectors.toSet());
    }

    @SuppressWarnings({ "java:S2384", "java:S107" })
    InternetDiscovery(final Map<Nonce, Ping> openPingsCache,
                      final DrasylAddress myAddress,
                      final ProofOfWork myProofOfWork,
                      final Map<Pair<IdentityPublicKey, IdentityPublicKey>, Boolean> uniteAttemptsCache,
                      final Map<IdentityPublicKey, Peer> peers,
                      final Set<IdentityPublicKey> directConnectionPeers,
                      final Set<IdentityPublicKey> superPeers,
                      final Duration pingInterval,
                      final Duration pingTimeout,
                      final Duration pingCommunicationTimeout,
                      final boolean superPeerEnabled,
                      final Set<Endpoint> superPeerEndpoints,
                      final int networkId,
                      final Future<?> heartbeatDisposable,
                      final IdentityPublicKey bestSuperPeer) {
        this.openPingsCache = openPingsCache;
        this.myAddress = requireNonNull(myAddress);
        this.myProofOfWork = requireNonNull(myProofOfWork);
        this.uniteAttemptsCache = requireNonNull(uniteAttemptsCache);
        this.directConnectionPeers = requireNonNull(directConnectionPeers);
        this.peers = requireNonNull(peers);
        this.superPeers = requireNonNull(superPeers);
        this.pingInterval = requireNonNull(pingInterval);
        this.pingTimeout = requireNonNull(pingTimeout);
        this.pingCommunicationTimeout = requireNonNull(pingCommunicationTimeout);
        this.superPeerEnabled = superPeerEnabled;
        this.superPeerEndpoints = requireNonNull(superPeerEndpoints);
        this.networkId = networkId;
        this.heartbeatDisposable = heartbeatDisposable;
        this.bestSuperPeer = bestSuperPeer;
    }

    void startHeartbeat(final ChannelHandlerContext ctx) {
        if (heartbeatDisposable == null) {
            LOG.debug("Start heartbeat scheduler");
            heartbeatDisposable = ctx.executor().scheduleAtFixedRate(() -> doHeartbeat(ctx), randomLong(pingInterval.toMillis()), pingInterval.toMillis(), MILLISECONDS);
        }
    }

    void stopHeartbeat() {
        if (heartbeatDisposable != null) {
            LOG.debug("Stop heartbeat scheduler");
            heartbeatDisposable.cancel(false);
            heartbeatDisposable = null;
        }
    }

    /**
     * This method sends ping messages to super peer and direct connection peers.
     *
     * @param ctx handler's context
     */
    void doHeartbeat(final ChannelHandlerContext ctx) {
        removeStalePeers(ctx);
        pingSuperPeers(ctx);
        pingDirectConnectionPeers(ctx);
        ctx.flush();
    }

    /**
     * This method removes stale peers from the peer list, that not respond to ping messages.
     *
     * @param ctx the handler context
     */
    private void removeStalePeers(final ChannelHandlerContext ctx) {
        // check lastContactTimes
        new HashMap<>(peers).forEach(((publicKey, peer) -> {
            if (!peer.hasControlTraffic(pingTimeout)) {
                LOG.debug("Last contact from {} is {}ms ago. Remove peer.", () -> publicKey, () -> System.currentTimeMillis() - peer.getLastInboundControlTrafficTime());
                if (superPeers.contains(publicKey)) {
                    ctx.fireUserEventTriggered(RemoveSuperPeerAndPathEvent.of(publicKey, path));
                }
                else {
                    ctx.fireUserEventTriggered(RemoveChildrenAndPathEvent.of(publicKey, path));
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
    private void pingSuperPeers(final ChannelHandlerContext ctx) {
        if (superPeerEnabled) {
            for (final Endpoint endpoint : superPeerEndpoints) {
                final SocketAddress address = new InetSocketAddress(endpoint.getHost(), endpoint.getPort());
                sendPing(ctx, endpoint.getIdentityPublicKey(), address).addListener(future -> {
                    if (!future.isSuccess()) {
                        //noinspection unchecked
                        LOG.warn("Unable to send ping for super peer `{}` to `{}`", endpoint::getIdentityPublicKey, () -> address, future::cause);
                    }
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
    private void pingDirectConnectionPeers(final ChannelHandlerContext ctx) {
        for (final IdentityPublicKey publicKey : new HashSet<>(directConnectionPeers)) {
            final Peer peer = peers.get(publicKey);
            final SocketAddress address = peer.getAddress();
            if (address != null && peer.hasApplicationTraffic(pingCommunicationTimeout)) {
                sendPing(ctx, publicKey, address).addListener(future -> {
                    if (!future.isSuccess()) {
                        //noinspection unchecked
                        LOG.warn("Unable to send ping for peer `{}` to `{}`", () -> publicKey, () -> address, future::cause);
                    }
                });
            }
            // remove trivial communications, that does not send any user generated messages
            else {
                LOG.debug("Last application communication to {} is {}ms ago. Remove peer.", () -> publicKey, () -> System.currentTimeMillis() - peer.getLastApplicationTrafficTime());
                ctx.fireUserEventTriggered(RemoveChildrenAndPathEvent.of(publicKey, path));
                directConnectionPeers.remove(publicKey);
            }
        }
    }

    private void removeAllPeers(final ChannelHandlerContext ctx) {
        new HashMap<>(peers).forEach(((publicKey, peer) -> {
            if (superPeers.contains(publicKey)) {
                ctx.fireUserEventTriggered(RemoveSuperPeerAndPathEvent.of(publicKey, path));
            }
            else {
                ctx.fireUserEventTriggered(RemoveChildrenAndPathEvent.of(publicKey, path));
            }
            peers.remove(publicKey);
            directConnectionPeers.remove(publicKey);
        }));
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof ApplicationMessage) {
            final ApplicationMessage applicationMsg = (ApplicationMessage) ((AddressedMessage<?, ?>) msg).message();
            final SocketAddress recipient = ((AddressedMessage<?, ?>) msg).address();

            if (recipient instanceof IdentityPublicKey) {
                // record communication to keep active connections alive
                if (directConnectionPeers.contains(recipient)) {
                    final Peer peer = peers.computeIfAbsent((IdentityPublicKey) recipient, key -> new Peer());
                    peer.applicationTrafficOccurred();
                }

                if (!processMessage(ctx, (IdentityPublicKey) recipient, applicationMsg, promise)) {
                    // pass through message
                    ctx.write(msg, promise);
                }
            }
            else {
                // pass through message
                ctx.write(msg, promise);
            }
        }
        else {
            ctx.write(msg, promise);
        }
    }

    /**
     * This method tries to find the cheapest path to the {@code recipient}. If we're a relay, we
     * additionally try to initiate a rendezvous between the two corresponding nodes.
     *
     * @param ctx       the handler context
     * @param recipient the recipient of the message
     * @param msg       the message
     * @param promise   the future
     * @return {@code true} if message could be processed. Otherwise {@code false}
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean processMessage(final ChannelHandlerContext ctx,
                                   final IdentityPublicKey recipient,
                                   final RemoteMessage msg,
                                   final ChannelPromise promise) {
        final Peer recipientPeer = peers.get(recipient);

        final Peer superPeerPeer;
        if (bestSuperPeer != null) {
            superPeerPeer = peers.get(bestSuperPeer);
        }
        else {
            superPeerPeer = null;
        }

        if (recipientPeer != null && recipientPeer.getAddress() != null && recipientPeer.isReachable(pingTimeout)) {
            final InetSocketAddress recipientSocketAddress = recipientPeer.getAddress();
            final Peer senderPeer = peers.get(msg.getSender());

            // rendezvous? I'm a super peer?
            if (senderPeer != null && superPeerPeer == null && senderPeer.getAddress() != null) {
                final InetSocketAddress senderSocketAddress = senderPeer.getAddress();
                final IdentityPublicKey msgSender = msg.getSender();
                final IdentityPublicKey msgRecipient = msg.getRecipient();
                LOG.trace("Relay message from {} to {}.", msgSender, recipient);

                if (shouldTryUnite(msgSender, msgRecipient)) {
                    ctx.executor().execute(() -> sendUnites(ctx, msgSender, msgRecipient, recipientSocketAddress, senderSocketAddress));
                }
            }

            LOG.trace("Send message to {} to {}.", recipient, recipientSocketAddress);
            ctx.writeAndFlush(new AddressedMessage<>(msg, recipientSocketAddress), promise);

            return true;
        }
        else if (superPeerPeer != null) {
            final SocketAddress superPeerSocketAddress = superPeerPeer.getAddress();
            LOG.trace("No connection to {}. Send message to super peer.", recipient);
            ctx.writeAndFlush(new AddressedMessage<>(msg, superPeerSocketAddress), promise);

            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        startHeartbeat(ctx);

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        stopHeartbeat();
        openPingsCache.clear();
        uniteAttemptsCache.clear();
        removeAllPeers(ctx);

        ctx.fireChannelInactive();
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
    @SuppressWarnings("DuplicatedCode")
    private void sendUnites(final ChannelHandlerContext ctx,
                            final IdentityPublicKey senderKey,
                            final IdentityPublicKey recipientKey,
                            final InetSocketAddress recipient,
                            final InetSocketAddress sender) {
        // send recipient's information to sender
        final UniteMessage senderRendezvousEnvelope = UniteMessage.of(networkId, (IdentityPublicKey) myAddress, myProofOfWork, senderKey, recipientKey, recipient);
        LOG.trace("Send {} to {}", senderRendezvousEnvelope, sender);
        ctx.write(new AddressedMessage<>(senderRendezvousEnvelope, sender)).addListener(future -> {
            if (!future.isSuccess()) {
                //noinspection unchecked
                LOG.warn("Unable to send unite message for peer `{}` to `{}`", () -> senderKey, () -> sender, future::cause);
            }
        });

        // send sender's information to recipient
        final UniteMessage recipientRendezvousEnvelope = UniteMessage.of(networkId, (IdentityPublicKey) myAddress, myProofOfWork, recipientKey, senderKey, sender);
        LOG.trace("Send {} to {}", recipientRendezvousEnvelope, recipient);
        ctx.write(new AddressedMessage<>(recipientRendezvousEnvelope, recipient)).addListener(future -> {
            if (!future.isSuccess()) {
                //noinspection unchecked
                LOG.warn("Unable to send unite message for peer `{}` to `{}`", () -> recipientKey, () -> recipient, future::cause);
            }
        });
        ctx.flush();
    }

    private boolean shouldTryUnite(final IdentityPublicKey sender,
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
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof RemoteMessage && ((AddressedMessage<?, ?>) msg).address() instanceof InetSocketAddress && ((RemoteMessage) ((AddressedMessage<?, ?>) msg).message()).getRecipient() != null) {
            final RemoteMessage remoteMsg = (RemoteMessage) ((AddressedMessage<?, ?>) msg).message();
            final SocketAddress sender = ((AddressedMessage<?, ?>) msg).address();

            // this message is for us and we will fully decode it
            if (myAddress.equals(remoteMsg.getRecipient()) && remoteMsg instanceof FullReadMessage) {
                handleMessage(ctx, (InetSocketAddress) sender, (FullReadMessage<?>) remoteMsg);
            }
            else if (!superPeerEnabled) {
                if (!processMessage(ctx, remoteMsg.getRecipient(), remoteMsg, ctx.newPromise())) {
                    // pass through message
                    ctx.fireChannelRead(remoteMsg);
                }
            }
            else if (LOG.isDebugEnabled()) {
                LOG.debug("We're not a super peer. Message `{}` from `{}` to `{}` for relaying was dropped.", remoteMsg, sender, remoteMsg.getRecipient());
            }
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    private void handleMessage(final ChannelHandlerContext ctx,
                               final InetSocketAddress sender,
                               final FullReadMessage<?> msg) {
        if (msg instanceof DiscoveryMessage) {
            handlePing(ctx, sender, (DiscoveryMessage) msg);
        }
        else if (msg instanceof AcknowledgementMessage) {
            handlePong(ctx, sender, (AcknowledgementMessage) msg);
        }
        else if (msg instanceof UniteMessage && superPeers.contains(msg.getSender())) {
            handleUnite(ctx, (UniteMessage) msg);
        }
        else if (msg instanceof ApplicationMessage) {
            handleApplication(ctx, (ApplicationMessage) msg);
        }
        else {
            // pass through message
            ctx.fireChannelRead(new AddressedMessage<>(msg, sender));
        }
    }

    private void handlePing(final ChannelHandlerContext ctx,
                            final InetSocketAddress sender,
                            final DiscoveryMessage msg) {
        final IdentityPublicKey envelopeSender = msg.getSender();
        final Nonce id = msg.getNonce();
        final boolean childrenJoin = msg.getChildrenTime() > 0;
        LOG.trace("Got {} from {}", msg, sender);
        final Peer peer = peers.computeIfAbsent(envelopeSender, key -> new Peer());
        peer.setAddress(sender);
        peer.inboundControlTrafficOccurred();

        if (childrenJoin) {
            peer.inboundPingOccurred();
            // store peer information
            ctx.fireUserEventTriggered(AddPathAndChildrenEvent.of(envelopeSender, path));
        }

        // reply with pong
        final AcknowledgementMessage responseEnvelope = AcknowledgementMessage.of(networkId, (IdentityPublicKey) myAddress, myProofOfWork, envelopeSender, id);
        LOG.trace("Send {} to {}", responseEnvelope, sender);
        ctx.writeAndFlush(new AddressedMessage<>(responseEnvelope, sender));
    }

    private void handlePong(final ChannelHandlerContext ctx,
                            final InetSocketAddress sender,
                            final AcknowledgementMessage msg) {
        final IdentityPublicKey envelopeSender = msg.getSender();
        LOG.trace("Got {} from {}", msg, sender);
        final Ping ping = openPingsCache.remove(msg.getCorrespondingId());
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
                ctx.fireUserEventTriggered(AddPathAndSuperPeerEvent.of(envelopeSender, path));
            }
            else {
                // store peer information
                ctx.fireUserEventTriggered(AddPathEvent.of(envelopeSender, peer));
            }
        }
    }

    private void determineBestSuperPeer() {
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

    private void handleUnite(final ChannelHandlerContext ctx,
                             final UniteMessage msg) {
        final InetAddress address = msg.getAddress();
        final InetSocketAddress socketAddress = new InetSocketAddress(address, msg.getPort());
        LOG.trace("Got {}", msg);

        final Peer peer = peers.computeIfAbsent(msg.getPublicKey(), key -> new Peer());
        peer.setAddress(socketAddress);
        peer.inboundControlTrafficOccurred();
        peer.applicationTrafficOccurred();
        directConnectionPeers.add(msg.getPublicKey());
        sendPing(ctx, msg.getPublicKey(), socketAddress);
        ctx.flush();
    }

    private void handleApplication(final ChannelHandlerContext ctx,
                                   final ApplicationMessage msg) {
        if (directConnectionPeers.contains(msg.getSender())) {
            final Peer peer = peers.computeIfAbsent(msg.getSender(), key -> new Peer());
            peer.applicationTrafficOccurred();
        }

        ctx.fireChannelRead(new AddressedMessage<>(msg, msg.getSender()));
    }

    private ChannelFuture sendPing(final ChannelHandlerContext ctx,
                                   final IdentityPublicKey recipient,
                                   final SocketAddress recipientAddress) {
        final boolean isChildrenJoin = superPeers.contains(recipient);
        final DiscoveryMessage messageEnvelope;
        messageEnvelope = DiscoveryMessage.of(networkId, (IdentityPublicKey) myAddress, myProofOfWork, recipient, isChildrenJoin ? System.currentTimeMillis() : 0);
        openPingsCache.put(messageEnvelope.getNonce(), new Ping(recipientAddress));
        LOG.trace("Send {} to {}", messageEnvelope, recipientAddress);
        return ctx.write(new AddressedMessage<>(messageEnvelope, recipientAddress));
    }

    @SuppressWarnings("java:S2972")
    static class Peer {
        private InetSocketAddress address;
        private long lastInboundControlTrafficTime;
        private long lastInboundPongTime;
        private long lastApplicationTrafficTime;
        private long lastOutboundPingTime;

        Peer(final InetSocketAddress address,
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

        public InetSocketAddress getAddress() {
            return address;
        }

        public void setAddress(final InetSocketAddress address) {
            this.address = address;
        }

        /**
         * Returns the time when we last received a message from this peer. This includes all
         * message types (discovery, acknowledge, application, unite, etc.)
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
         * this peer. This includes only message type {@link ApplicationMessage}
         */
        public long getLastApplicationTrafficTime() {
            return lastApplicationTrafficTime;
        }

        public void applicationTrafficOccurred() {
            lastApplicationTrafficTime = System.currentTimeMillis();
        }

        public boolean hasApplicationTraffic(final Duration pingCommunicationTimeout) {
            return lastApplicationTrafficTime >= System.currentTimeMillis() - pingCommunicationTimeout.toMillis();
        }

        public boolean hasControlTraffic(final Duration pingTimeout) {
            return lastInboundControlTrafficTime >= System.currentTimeMillis() - pingTimeout.toMillis();
        }

        public boolean isReachable(final Duration pingTimeout) {
            return lastInboundPongTime >= System.currentTimeMillis() - pingTimeout.toMillis();
        }

        public long getLatency() {
            return lastInboundPongTime - lastOutboundPingTime;
        }
    }

    @SuppressWarnings("java:S2972")
    public static class Ping {
        private final SocketAddress address;
        private final long pingTime;

        public Ping(final SocketAddress address) {
            this.address = requireNonNull(address);
            pingTime = System.currentTimeMillis();
        }

        public SocketAddress getAddress() {
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
