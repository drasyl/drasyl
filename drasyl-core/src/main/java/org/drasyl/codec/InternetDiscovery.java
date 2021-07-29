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
package org.drasyl.codec;

import com.google.common.cache.CacheBuilder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.protocol.AcknowledgementMessage;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.remote.protocol.DiscoveryMessage;
import org.drasyl.remote.protocol.FullReadMessage;
import org.drasyl.remote.protocol.Nonce;
import org.drasyl.remote.protocol.Protocol;
import org.drasyl.remote.protocol.RemoteMessage;
import org.drasyl.remote.protocol.UniteMessage;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
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

public class InternetDiscovery extends SimpleChannelDuplexHandler<AddressedRemoteMessage, AddressedApplicationMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(InternetDiscovery.class);
    private static final Object path = org.drasyl.remote.handler.InternetDiscovery.class;
    private final Map<Nonce, InternetDiscovery.Ping> openPingsCache;
    private final Map<Pair<IdentityPublicKey, IdentityPublicKey>, Boolean> uniteAttemptsCache;
    private final Map<IdentityPublicKey, InternetDiscovery.Peer> peers;
    private final Set<IdentityPublicKey> directConnectionPeers;
    private final Set<IdentityPublicKey> superPeers;
    private ScheduledFuture<?> heartbeatDisposable;
    private IdentityPublicKey bestSuperPeer;

    public InternetDiscovery(final DrasylConfig config) {
        super(false, false, true);
        openPingsCache = CacheBuilder.newBuilder()
                .maximumSize(config.getRemotePingMaxPeers())
                .expireAfterWrite(config.getRemotePingTimeout())
                .<Nonce, InternetDiscovery.Ping>build()
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
    InternetDiscovery(final Map<Nonce, InternetDiscovery.Ping> openPingsCache,
                      final Map<Pair<IdentityPublicKey, IdentityPublicKey>, Boolean> uniteAttemptsCache,
                      final Map<IdentityPublicKey, InternetDiscovery.Peer> peers,
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
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        startHeartbeat(ctx);

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        stopHeartbeat();
        openPingsCache.clear();
        uniteAttemptsCache.clear();
        removeAllPeers(ctx);

        super.channelInactive(ctx);
    }

    void startHeartbeat(final ChannelHandlerContext ctx) {
        if (heartbeatDisposable == null) {
            LOG.debug("Start heartbeat scheduler");
            final long pingInterval = ((DrasylServerChannel) ctx.channel()).drasylConfig().getRemotePingInterval().toMillis();
            heartbeatDisposable = ctx.executor().scheduleAtFixedRate(() -> doHeartbeat(ctx), randomLong(pingInterval), pingInterval, MILLISECONDS);
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
    }

    /**
     * This method removes stale peers from the peer list, that not respond to ping messages.
     *
     * @param ctx the handler context
     */
    private void removeStalePeers(final ChannelHandlerContext ctx) {
        // check lastContactTimes
        new HashMap<>(peers).forEach(((publicKey, peer) -> {
            if (!peer.hasControlTraffic(((DrasylServerChannel) ctx.channel()).drasylConfig())) {
                LOG.debug("Last contact from {} is {}ms ago. Remove peer.", () -> publicKey, () -> System.currentTimeMillis() - peer.getLastInboundControlTrafficTime());
                if (superPeers.contains(publicKey)) {
                    ((DrasylServerChannel) ctx.channel()).peersManager().removeSuperPeerAndPath(publicKey, path);
                }
                else {
                    ((DrasylServerChannel) ctx.channel()).peersManager().removeChildrenAndPath(publicKey, path);
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
        if (((DrasylServerChannel) ctx.channel()).drasylConfig().isRemoteSuperPeerEnabled()) {
            for (final Endpoint endpoint : ((DrasylServerChannel) ctx.channel()).drasylConfig().getRemoteSuperPeerEndpoints()) {
                final InetSocketAddressWrapper address = new InetSocketAddressWrapper(endpoint.getHost(), endpoint.getPort());
                sendPing(ctx, endpoint.getIdentityPublicKey(), address);
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
            final InetSocketAddressWrapper address = peer.getAddress();
            if (address != null && peer.hasApplicationTraffic(((DrasylServerChannel) ctx.channel()).drasylConfig())) {
                sendPing(ctx, publicKey, address);
            }
            // remove trivial communications, that does not send any user generated messages
            else {
                LOG.debug("Last application communication to {} is {}ms ago. Remove peer.", () -> publicKey, () -> System.currentTimeMillis() - peer.getLastApplicationTrafficTime());
                ((DrasylServerChannel) ctx.channel()).peersManager().removeChildrenAndPath(publicKey, path);
                directConnectionPeers.remove(publicKey);
            }
        }
    }

    private void removeAllPeers(final ChannelHandlerContext ctx) {
        new HashMap<>(peers).forEach(((publicKey, peer) -> {
            if (superPeers.contains(publicKey)) {
                ((DrasylServerChannel) ctx.channel()).peersManager().removeSuperPeerAndPath(publicKey, path);
            }
            else {
                ((DrasylServerChannel) ctx.channel()).peersManager().removeChildrenAndPath(publicKey, path);
            }
            peers.remove(publicKey);
            directConnectionPeers.remove(publicKey);
        }));
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final AddressedRemoteMessage addressedMsg) throws Exception {
        final RemoteMessage msg = addressedMsg.content();
        final Object sender = addressedMsg.sender();

        if (sender instanceof InetSocketAddress && msg.getRecipient() != null) {
            // This message is for us and we will fully decode it
            if (((DrasylServerChannel) ctx.channel()).localAddress0().getIdentityPublicKey().equals(msg.getRecipient()) && msg instanceof FullReadMessage) {
                handleMessage(ctx, (InetSocketAddressWrapper) sender, (FullReadMessage<?>) msg);
            }
            else if (!((DrasylServerChannel) ctx.channel()).drasylConfig().isRemoteSuperPeerEnabled()) {
                if (!processMessage(ctx, msg.getRecipient(), msg)) {
                    // passthrough message
                    ctx.writeAndFlush(addressedMsg);
                }
            }
            else if (LOG.isDebugEnabled()) {
                LOG.debug("We're not a super peer. Message `{}` from `{}` to `{}` for relaying was dropped.", msg, sender, msg.getRecipient());
            }
        }
        else {
            // passthrough message
            ctx.writeAndFlush(addressedMsg);
        }
    }

    @Override
    protected void channelWrite0(final ChannelHandlerContext ctx,
                                 final AddressedApplicationMessage addressedMsg,
                                 final ChannelPromise promise) throws Exception {
        final ApplicationMessage msg = addressedMsg.content();
        final Object recipient = addressedMsg.recipient();

        if (recipient instanceof IdentityPublicKey) {
            // record communication to keep active connections alive
            if (directConnectionPeers.contains(recipient)) {
                final Peer peer = peers.computeIfAbsent((IdentityPublicKey) recipient, key -> new Peer());
                peer.applicationTrafficOccurred();
            }

            if (!processMessage(ctx, (IdentityPublicKey) recipient, msg)) {
                // passthrough message
                ctx.writeAndFlush(addressedMsg, promise);
            }
        }
        else {
            // passthrough message
            ctx.writeAndFlush(addressedMsg, promise);
        }
    }

    /**
     * This method tries to find the cheapest path to the {@code recipient}. If we're a relay, we
     * additionally try to initiate a rendezvous between the two corresponding nodes.
     *
     * @param ctx       the handler context
     * @param recipient the recipient of the message
     * @param msg       the message
     * @return {@code true} if message could be processed. Otherwise {@code false}
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean processMessage(final ChannelHandlerContext ctx,
                                   final IdentityPublicKey recipient,
                                   final RemoteMessage msg) {
        final Peer recipientPeer = peers.get(recipient);

        final Peer superPeerPeer;
        if (bestSuperPeer != null) {
            superPeerPeer = peers.get(bestSuperPeer);
        }
        else {
            superPeerPeer = null;
        }

        if (recipientPeer != null && recipientPeer.getAddress() != null && recipientPeer.isReachable(((DrasylServerChannel) ctx.channel()).drasylConfig())) {
            final InetSocketAddressWrapper recipientSocketAddress = recipientPeer.getAddress();
            final Peer senderPeer = peers.get(msg.getSender());

            // rendezvous? I'm a super peer?
            if (senderPeer != null && superPeerPeer == null && senderPeer.getAddress() != null) {
                final InetSocketAddressWrapper senderSocketAddress = senderPeer.getAddress();
                final IdentityPublicKey msgSender = msg.getSender();
                final IdentityPublicKey msgRecipient = msg.getRecipient();
                LOG.trace("Relay message from {} to {}.", msgSender, recipient);

                if (shouldTryUnite(msgSender, msgRecipient)) {
                    ctx.executor().execute(() -> sendUnites(ctx, msgSender, msgRecipient, recipientSocketAddress, senderSocketAddress));
                }
            }

            LOG.trace("Send message to {} to {}.", recipient, recipientSocketAddress);
            if (msg instanceof ApplicationMessage) {
                ctx.writeAndFlush(new AddressedApplicationMessage((ApplicationMessage) msg, recipientSocketAddress, null));
            }
            else {
                // FIXME
                throw new RuntimeException("not implemented yet");
//            ctx.passOutbound(superPeerSocketAddress, msg, future);
            }

            return true;
        }
        else if (superPeerPeer != null) {
            final InetSocketAddressWrapper superPeerSocketAddress = superPeerPeer.getAddress();
            LOG.trace("No connection to {}. Send message to super peer.", recipient);
            if (msg instanceof ApplicationMessage) {
                ctx.writeAndFlush(new AddressedApplicationMessage((ApplicationMessage) msg, superPeerSocketAddress, null));
            }
            else {
                // FIXME
                throw new RuntimeException("not implemented yet");
//            ctx.passOutbound(superPeerSocketAddress, msg, future);
            }

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
    private static void sendUnites(final ChannelHandlerContext ctx,
                                   final IdentityPublicKey senderKey,
                                   final IdentityPublicKey recipientKey,
                                   final InetSocketAddressWrapper recipient,
                                   final InetSocketAddressWrapper sender) {
        // send recipient's information to sender
        final UniteMessage senderRendezvousEnvelope = UniteMessage.of(((DrasylServerChannel) ctx.channel()).drasylConfig().getNetworkId(), ((DrasylServerChannel) ctx.channel()).localAddress0().getIdentityPublicKey(), ((DrasylServerChannel) ctx.channel()).localAddress0().getProofOfWork(), senderKey, recipientKey, recipient);
        LOG.trace("Send {} to {}", senderRendezvousEnvelope, sender);
        ctx.writeAndFlush(new AddressedFullReadMessage(senderRendezvousEnvelope, null, sender)).addListener(future -> {
            if (!future.isSuccess()) {
                //noinspection unchecked
                LOG.warn("Unable to send unite message for peer `{}` to `{}`", () -> senderKey, () -> sender, future::cause);
            }
        });

        // send sender's information to recipient
        final UniteMessage recipientRendezvousEnvelope = UniteMessage.of(((DrasylServerChannel) ctx.channel()).drasylConfig().getNetworkId(), ((DrasylServerChannel) ctx.channel()).localAddress0().getIdentityPublicKey(), ((DrasylServerChannel) ctx.channel()).localAddress0().getProofOfWork(), recipientKey, senderKey, sender);
        LOG.trace("Send {} to {}", recipientRendezvousEnvelope, recipient);
        ctx.writeAndFlush(new AddressedFullReadMessage(senderRendezvousEnvelope, null, sender)).addListener(future -> {
            //noinspection unchecked
            LOG.warn("Unable to send unite message for peer `{}` to `{}`", () -> recipientKey, () -> recipient, future::cause);
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

    private void handleMessage(final ChannelHandlerContext ctx,
                               final InetSocketAddressWrapper sender,
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
            // passthrough message
            ctx.fireChannelRead(new AddressedFullReadMessage(msg, null, sender));
        }
    }

    private void handlePing(final ChannelHandlerContext ctx,
                            final InetSocketAddressWrapper sender,
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
            if (LOG.isDebugEnabled() && !((DrasylServerChannel) ctx.channel()).peersManager().getChildren().contains(envelopeSender) && !((DrasylServerChannel) ctx.channel()).peersManager().getPaths(envelopeSender).contains(path)) {
                LOG.debug("PING! Add {} as children", envelopeSender);
            }
            ((DrasylServerChannel) ctx.channel()).peersManager().addPathAndChildren(envelopeSender, path);
        }

        // reply with pong
        final int networkId = ((DrasylServerChannel) ctx.channel()).drasylConfig().getNetworkId();
        final IdentityPublicKey myPublicKey = ((DrasylServerChannel) ctx.channel()).localAddress0().getIdentityPublicKey();
        final ProofOfWork myProofOfWork = ((DrasylServerChannel) ctx.channel()).localAddress0().getProofOfWork();
        final AcknowledgementMessage responseEnvelope = AcknowledgementMessage.of(networkId, myPublicKey, myProofOfWork, envelopeSender, id);
        LOG.trace("Send {} to {}", responseEnvelope, sender);
        ctx.writeAndFlush(new AddressedFullReadMessage(responseEnvelope, null, sender));
    }

    private void handlePong(final ChannelHandlerContext ctx,
                            final InetSocketAddressWrapper sender,
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
                if (LOG.isDebugEnabled() && !((DrasylServerChannel) ctx.channel()).peersManager().getChildren().contains(envelopeSender) && !((DrasylServerChannel) ctx.channel()).peersManager().getPaths(envelopeSender).contains(path)) {
                    LOG.debug("PONG! Add {} as super peer", envelopeSender);
                }
                ((DrasylServerChannel) ctx.channel()).peersManager().addPathAndSuperPeer(envelopeSender, path);
            }
            else {
                // store peer information
                if (LOG.isDebugEnabled() && !((DrasylServerChannel) ctx.channel()).peersManager().getPaths(envelopeSender).contains(path)) {
                    LOG.debug("PONG! Add {} as peer", envelopeSender);
                }
                ((DrasylServerChannel) ctx.channel()).peersManager().addPath(envelopeSender, path);
            }
        }
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

    private void handleUnite(final ChannelHandlerContext ctx,
                             final UniteMessage msg) {
        final InetAddress address = msg.getAddress();
        final InetSocketAddressWrapper socketAddress = new InetSocketAddressWrapper(address, msg.getPort());
        LOG.trace("Got {}", msg);

        final Peer peer = peers.computeIfAbsent(msg.getPublicKey(), key -> new Peer());
        peer.setAddress(socketAddress);
        peer.inboundControlTrafficOccurred();
        peer.applicationTrafficOccurred();
        directConnectionPeers.add(msg.getPublicKey());
        sendPing(ctx, msg.getPublicKey(), socketAddress);
    }

    private void handleApplication(final ChannelHandlerContext ctx,
                                   final ApplicationMessage msg) {
        if (directConnectionPeers.contains(msg.getSender())) {
            final Peer peer = peers.computeIfAbsent(msg.getSender(), key -> new Peer());
            peer.applicationTrafficOccurred();
        }

        ctx.fireChannelRead(new AddressedApplicationMessage(msg, null, msg.getSender()));
    }

    private void sendPing(final ChannelHandlerContext ctx,
                          final IdentityPublicKey recipient,
                          final InetSocketAddressWrapper recipientAddress) {
        final int networkId = ((DrasylServerChannel) ctx.channel()).drasylConfig().getNetworkId();
        final IdentityPublicKey sender = ((DrasylServerChannel) ctx.channel()).localAddress0().getIdentityPublicKey();
        final ProofOfWork proofOfWork = ((DrasylServerChannel) ctx.channel()).localAddress0().getProofOfWork();

        final boolean isChildrenJoin = superPeers.contains(recipient);
        final DiscoveryMessage messageEnvelope;
        messageEnvelope = DiscoveryMessage.of(networkId, sender, proofOfWork, recipient, isChildrenJoin ? System.currentTimeMillis() : 0);
        openPingsCache.put(messageEnvelope.getNonce(), new InternetDiscovery.Ping(recipientAddress));
        LOG.trace("Send {} to {}", messageEnvelope, recipientAddress);
        ctx.writeAndFlush(new AddressedFullReadMessage(messageEnvelope, recipientAddress, null));
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
         * message types ({@link DiscoveryMessage}, {@link AcknowledgementMessage}, {@link
         * Protocol.Application}, {@link UniteMessage}, etc.)
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
            final InternetDiscovery.Ping ping = (InternetDiscovery.Ping) o;
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
