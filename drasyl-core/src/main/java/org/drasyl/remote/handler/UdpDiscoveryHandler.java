/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.remote.handler;

import com.google.common.cache.CacheBuilder;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeerInformation;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.remote.message.AcknowledgementMessage;
import org.drasyl.remote.message.DiscoverMessage;
import org.drasyl.remote.message.MessageId;
import org.drasyl.remote.message.RemoteApplicationMessage;
import org.drasyl.remote.message.RemoteMessage;
import org.drasyl.remote.message.UniteMessage;
import org.drasyl.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@SuppressWarnings({ "java:S110" })
public class UdpDiscoveryHandler extends SimpleDuplexHandler<RemoteMessage, RemoteMessage, Address> {
    private static final Logger LOG = LoggerFactory.getLogger(UdpDiscoveryHandler.class);
    public static final String UDP_DISCOVERY_HANDLER = "UDP_DISCOVERY_HANDLER";
    private static final Object path = UdpDiscoveryHandler.class;
    private final Map<MessageId, Pair<DiscoverMessage, InetSocketAddressWrapper>> openPingsCache;
    private final Map<Pair<CompressedPublicKey, CompressedPublicKey>, Boolean> uniteAttemptsCache;
    private final Map<CompressedPublicKey, Peer> peers;
    private final Set<CompressedPublicKey> directConnectionPeers;
    private Disposable heartbeatDisposable;

    public UdpDiscoveryHandler(final DrasylConfig config) {
        openPingsCache = CacheBuilder.newBuilder()
                .maximumSize(config.getRemotePingMaxPeers())
                .expireAfterWrite(config.getRemotePingTimeout())
                .<MessageId, Pair<DiscoverMessage, InetSocketAddressWrapper>>build()
                .asMap();
        directConnectionPeers = new HashSet<>();
        if (config.getRemoteUniteMinInterval().toMillis() > 0) {
            uniteAttemptsCache = CacheBuilder.newBuilder()
                    .expireAfterWrite(config.getRemoteUniteMinInterval())
                    .<Pair<CompressedPublicKey, CompressedPublicKey>, Boolean>build()
                    .asMap();
        }
        else {
            uniteAttemptsCache = null;
        }
        peers = new HashMap<>();
    }

    UdpDiscoveryHandler(final Map<MessageId, Pair<DiscoverMessage, InetSocketAddressWrapper>> openPingsCache,
                        final Map<Pair<CompressedPublicKey, CompressedPublicKey>, Boolean> uniteAttemptsCache,
                        final Map<CompressedPublicKey, Peer> peers,
                        final Set<CompressedPublicKey> directConnectionPeers) {
        this.openPingsCache = openPingsCache;
        this.uniteAttemptsCache = uniteAttemptsCache;
        this.directConnectionPeers = directConnectionPeers;
        this.peers = peers;
    }

    @Override
    public void eventTriggered(final HandlerContext ctx,
                               final Event event,
                               final CompletableFuture<Void> future) {
        if (event instanceof NodeUpEvent) {
            startHeartbeat(ctx);
        }
        else if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            stopHeartbeat();
        }

        // passthrough event
        ctx.fireEventTriggered(event, future);
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final Address recipient,
                                final RemoteMessage msg,
                                final CompletableFuture<Void> future) {
        if (msg instanceof RemoteApplicationMessage && directConnectionPeers.contains(msg.getRecipient())) {
            final Peer peer = peers.computeIfAbsent(msg.getRecipient(), key -> new Peer());
            peer.applicationTrafficOccurred();
        }

        if (recipient instanceof CompressedPublicKey) {
            processMessage(ctx, (CompressedPublicKey) recipient, msg, future);
        }
        else {
            // passthrough message
            ctx.write(recipient, msg, future);
        }
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final RemoteMessage msg,
                               final CompletableFuture<Void> future) {
        requireNonNull(msg);
        requireNonNull(sender);

        if (sender instanceof InetSocketAddressWrapper && msg instanceof DiscoverMessage) {
            handlePing(ctx, (InetSocketAddressWrapper) sender, (DiscoverMessage) msg, future);
        }
        else if (sender instanceof InetSocketAddressWrapper && msg instanceof AcknowledgementMessage) {
            handlePong(ctx, (InetSocketAddressWrapper) sender, (AcknowledgementMessage) msg, future);
        }
        else if (msg instanceof UniteMessage && ctx.config().getRemoteSuperPeerEndpoint().getPublicKey().equals(msg.getSender())) {
            handleUnite(ctx, (UniteMessage) msg, future);
        }
        else if (!msg.getRecipient().equals(ctx.identity().getPublicKey())) {
            if (!ctx.config().isRemoteSuperPeerEnabled()) {
                processMessage(ctx, msg.getRecipient(), msg, future);
            }
            else if (LOG.isDebugEnabled()) {
                LOG.debug("We're not a super peer. Message {} from {} for relaying was dropped.", msg, sender);
            }
        }
        else {
            if (msg instanceof RemoteApplicationMessage && directConnectionPeers.contains(msg.getSender())) {
                final Peer peer = peers.computeIfAbsent(msg.getSender(), key -> new Peer());
                peer.applicationTrafficOccurred();
            }

            // passthrough message
            ctx.fireRead(sender, msg, future);
        }
    }

    synchronized void startHeartbeat(final HandlerContext ctx) {
        if (heartbeatDisposable == null) {
            LOG.debug("Start heartbeat scheduler");
            heartbeatDisposable = ctx.scheduler()
                    .schedulePeriodicallyDirect(() -> doHeartbeat(ctx), 0, ctx.config().getRemotePingInterval().toMillis(), MILLISECONDS);
        }
    }

    /**
     * This method sends ping messages to super peer and direct connection peers.
     *
     * @param ctx handler's context
     */
    void doHeartbeat(final HandlerContext ctx) {
        removeStalePeers(ctx);
        pingSuperPeer(ctx);
        pingDirectConnectionPeers(ctx);
    }

    synchronized void stopHeartbeat() {
        if (heartbeatDisposable != null) {
            LOG.debug("Stop heartbeat scheduler");
            heartbeatDisposable.dispose();
            heartbeatDisposable = null;
        }
    }

    /**
     * This method removes stale peers from the peer list, that not respond to ping messages.
     *
     * @param ctx the handler context
     */
    private void removeStalePeers(final HandlerContext ctx) {
        // check lastContactTimes
        final CompressedPublicKey superPeerKey = ctx.config().getRemoteSuperPeerEndpoint().getPublicKey();
        new HashMap<>(peers).forEach(((publicKey, peer) -> {
            if (!peer.hasControlTraffic(ctx.config())) {
                if (LOG.isDebugEnabled()) {
                    final long lastInboundControlTrafficTime = peer.getLastInboundControlTrafficTime();
                    LOG.debug("Last contact from {} is {}ms ago. Remove peer.", publicKey, System.currentTimeMillis() - lastInboundControlTrafficTime);
                }
                if (publicKey.equals(superPeerKey)) {
                    ctx.peersManager().unsetSuperPeerAndRemovePath(path);
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
     * If the node has configured a Super Peer, a ping message is sent to it.
     *
     * @param ctx handler's context
     */
    private void pingSuperPeer(final HandlerContext ctx) {
        if (ctx.config().isRemoteSuperPeerEnabled()) {
            sendPing(ctx, ctx.config().getRemoteSuperPeerEndpoint().getPublicKey(),
                    InetSocketAddressWrapper.of(
                            new InetSocketAddress(ctx.config().getRemoteSuperPeerEndpoint().getHost(),
                                    ctx.config().getRemoteSuperPeerEndpoint().getPort())),
                    true, new CompletableFuture<>());
        }
    }

    /**
     * Sends ping messages to all peers with whom a direct connection should be kept open. Removes
     * peers that have not had application-level communication with you for a while.
     *
     * @param ctx handler's context
     */
    private void pingDirectConnectionPeers(final HandlerContext ctx) {
        new HashSet<>(directConnectionPeers).forEach(publicKey -> {
            final Peer peer = peers.get(publicKey);
            final InetSocketAddressWrapper address = peer.getAddress();
            if (address != null && peer.hasApplicationTraffic(ctx.config())) {
                sendPing(ctx, publicKey, address, false, new CompletableFuture<>());
            }
            // remove trivial communications, that does not send any user generated messages
            else {
                final long lastCommunicationTime = peer.getLastApplicationTrafficTime();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Last application communication to {} is {}ms ago. Remove peer.", publicKey, System.currentTimeMillis() - lastCommunicationTime);
                }
                ctx.peersManager().removeChildrenAndPath(publicKey, path);
                directConnectionPeers.remove(publicKey);
            }
        });
    }

    private void sendPing(final HandlerContext ctx,
                          final CompressedPublicKey publicKey,
                          final InetSocketAddressWrapper recipientAddress,
                          final boolean isChildrenJoin,
                          final CompletableFuture<Void> future) {
        final int networkId = ctx.config().getNetworkId();
        final CompressedPublicKey sender = ctx.identity().getPublicKey();
        final ProofOfWork proofOfWork = ctx.identity().getProofOfWork();

        final DiscoverMessage message = new DiscoverMessage(networkId, sender, proofOfWork, publicKey, isChildrenJoin ? System.currentTimeMillis() : 0);
        LOG.trace("Send {} to {}", message, recipientAddress);
        openPingsCache.put(message.getId(), Pair.of(message, recipientAddress));
        ctx.write(recipientAddress, message, future);
    }

    private void handlePing(final HandlerContext ctx,
                            final InetSocketAddressWrapper senderSocketAddress,
                            final DiscoverMessage message,
                            final CompletableFuture<Void> future) {
        LOG.trace("Got {} from {}", message, senderSocketAddress);
        final Peer peer = peers.computeIfAbsent(message.getSender(), key -> new Peer());
        peer.setAddress(senderSocketAddress);
        peer.inboundControlTrafficOccurred();

        if (message.isChildrenJoin()) {
            peer.inboundPongOccurred();
            // store peer information
            if (LOG.isDebugEnabled() && !ctx.peersManager().getChildrenKeys().contains(message.getSender()) && !ctx.peersManager().getPeer(message.getSender()).second().contains(path)) {
                LOG.debug("PING! Add {} as children", message.getSender());
            }
            ctx.peersManager().setPeerInformationAndAddPathAndChildren(message.getSender(), PeerInformation.of(), path);
        }

        // reply with pong
        final int networkId = ctx.config().getNetworkId();
        final CompressedPublicKey sender = ctx.identity().getPublicKey();
        final ProofOfWork proofOfWork = ctx.identity().getProofOfWork();
        final AcknowledgementMessage response = new AcknowledgementMessage(networkId, sender, proofOfWork, message.getSender(), message.getId());

        LOG.trace("Send {} to {}", response, senderSocketAddress);
        ctx.write(senderSocketAddress, response, future);
    }

    private void handlePong(final HandlerContext ctx,
                            final InetSocketAddressWrapper senderSocketAddress,
                            final AcknowledgementMessage message,
                            final CompletableFuture<Void> future) {
        LOG.trace("Got {} from {}", message, senderSocketAddress);
        final Pair<DiscoverMessage, InetSocketAddressWrapper> openRequest = openPingsCache.remove(message.getCorrespondingId());
        if (openRequest != null) {
            final Peer peer = peers.computeIfAbsent(message.getSender(), key -> new Peer());
            peer.setAddress(senderSocketAddress);
            peer.inboundControlTrafficOccurred();
            peer.inboundPongOccurred();
            if (openRequest.first().isChildrenJoin()) {
                // store peer information
                if (LOG.isDebugEnabled() && !ctx.peersManager().getChildrenKeys().contains(message.getSender()) && !ctx.peersManager().getPeer(message.getSender()).second().contains(path)) {
                    LOG.debug("PONG! Add {} as super peer", message.getSender());
                }
                ctx.peersManager().setPeerInformationAndAddPathAndSetSuperPeer(message.getSender(), PeerInformation.of(), path);
            }
            else {
                // store peer information
                if (LOG.isDebugEnabled() && !ctx.peersManager().getPeer(message.getSender()).second().contains(path)) {
                    LOG.debug("PONG! Add {} as peer", message.getSender());
                }
                ctx.peersManager().setPeerInformationAndAddPath(message.getSender(), PeerInformation.of(), path);
            }
        }
        future.complete(null);
    }

    private synchronized boolean shouldTryUnite(final CompressedPublicKey sender,
                                                final CompressedPublicKey recipient) {
        final Pair<CompressedPublicKey, CompressedPublicKey> key;
        if (sender.hashCode() > recipient.hashCode()) {
            key = Pair.of(sender, recipient);
        }
        else {
            key = Pair.of(recipient, sender);
        }
        return uniteAttemptsCache != null && uniteAttemptsCache.putIfAbsent(key, TRUE) == null;
    }

    /**
     * This method initiates a unite between the {@code sender} and {@code recipient}, by sending
     * both the required information to establish a direct (P2P) connection.
     *
     * @param ctx       the handler context
     * @param msg       the message
     * @param recipient the recipient socket address
     * @param sender    the sender socket address
     */
    private void sendUnites(final HandlerContext ctx,
                            final RemoteMessage msg,
                            final InetSocketAddressWrapper recipient,
                            final InetSocketAddressWrapper sender) {
        // send recipient's information to sender
        final UniteMessage senderRendezvous = new UniteMessage(ctx.config().getNetworkId(), ctx.identity().getPublicKey(), ctx.identity().getProofOfWork(), msg.getSender(), msg.getRecipient(), recipient.getAddress());
        LOG.trace("Send {} to {}", senderRendezvous, sender);
        ctx.write(sender, senderRendezvous, new CompletableFuture<>());

        // send sender's information to recipient
        final UniteMessage recipientRendezvous = new UniteMessage(ctx.config().getNetworkId(), ctx.identity().getPublicKey(), ctx.identity().getProofOfWork(), msg.getRecipient(), msg.getSender(), sender.getAddress());
        LOG.trace("Send {} to {}", recipientRendezvous, recipient);
        ctx.write(recipient, recipientRendezvous, new CompletableFuture<>());
    }

    private void handleUnite(final HandlerContext ctx,
                             final UniteMessage message,
                             final CompletableFuture<Void> future) {
        LOG.trace("Got {}", message);
        final InetSocketAddressWrapper socketAddress = new InetSocketAddressWrapper(message.getAddress());
        final Peer peer = peers.computeIfAbsent(message.getPublicKey(), key -> new Peer());
        peer.setAddress(socketAddress);
        peer.inboundControlTrafficOccurred();
        peer.applicationTrafficOccurred();
        directConnectionPeers.add(message.getPublicKey());
        sendPing(ctx, message.getPublicKey(), socketAddress, false, future);
    }

    /**
     * This method tries to find the cheapest path to the {@code recipient}. If we're a relay, we
     * additionally try to initiate a rendezvous between the two corresponding nodes.
     *
     * @param ctx       the handler context
     * @param recipient the recipient of the message
     * @param msg       the message
     * @param future    the future
     */
    private void processMessage(final HandlerContext ctx,
                                final CompressedPublicKey recipient,
                                final RemoteMessage msg,
                                final CompletableFuture<Void> future) {
        final Peer recipientPeer = peers.get(recipient);
        final CompressedPublicKey superPeerKey = ctx.peersManager().getSuperPeerKey();
        final Peer superPeerPeer;
        if (superPeerKey != null) {
            superPeerPeer = peers.get(superPeerKey);
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
                LOG.trace("Relay message from {} to {}.", msg.getSender(), recipient);

                if (shouldTryUnite(msg.getSender(), msg.getRecipient())) {
                    ctx.scheduler().scheduleDirect(() -> sendUnites(ctx, msg, recipientSocketAddress, senderSocketAddress));
                }
            }

            LOG.trace("Send message to {} to {}.", recipient, recipientSocketAddress);
            ctx.write(recipientSocketAddress, msg, future);
        }
        else if (superPeerPeer != null) {
            final InetSocketAddressWrapper superPeerSocketAddress = superPeerPeer.getAddress();
            LOG.trace("No connection to {}. Send message to super peer.", recipient);

            ctx.write(superPeerSocketAddress, msg, future);
        }
        else {
            // passthrough message
            ctx.write(recipient, msg, future);
        }
    }

    static class Peer {
        private InetSocketAddressWrapper address;
        private long lastInboundControlTrafficTime;
        private long lastInboundPongTime;
        private long lastApplicationTrafficTime;

        public InetSocketAddressWrapper getAddress() {
            return address;
        }

        public void setAddress(final InetSocketAddressWrapper address) {
            this.address = address;
        }

        /**
         * Returns the time when we last received a message from this peer. This includes all
         * message types ({@link DiscoverMessage}, {@link AcknowledgementMessage}, {@link
         * org.drasyl.pipeline.message.ApplicationMessage}, etc.)
         */
        public long getLastInboundControlTrafficTime() {
            return lastInboundControlTrafficTime;
        }

        public void inboundControlTrafficOccurred() {
            lastInboundControlTrafficTime = System.currentTimeMillis();
        }

        public void inboundPongOccurred() {
            lastInboundPongTime = System.currentTimeMillis();
        }

        /**
         * Returns the time when we last sent or received a application-level message to or from
         * this peer. This includes only message type {@link org.drasyl.pipeline.message.ApplicationMessage}
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
    }
}