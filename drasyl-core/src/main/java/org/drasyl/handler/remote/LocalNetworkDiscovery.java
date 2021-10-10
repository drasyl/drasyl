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
package org.drasyl.handler.remote;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.remote.protocol.DiscoveryMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.remote.UdpMulticastServer.MULTICAST_ADDRESS;
import static org.drasyl.util.RandomUtil.randomLong;

/**
 * This handler, along with the {@link UdpMulticastServer}, is used to discover other nodes on the
 * local network.
 * <p>
 * For this purpose, the {@link UdpMulticastServer} joins a multicast group and forwards received
 * {@link DiscoveryMessage}s to this handler, which thus becomes aware of other nodes in the local
 * network. In case no {@link DiscoveryMessage} has been received for a longer period of time, the
 * other node is considered stale.
 * <p>
 * In addition, this handler periodically sends a {@link DiscoveryMessage} messages to a multicast
 * group so that other nodes become aware of this node.
 *
 * @see UdpMulticastServer
 */
@SuppressWarnings("java:S110")
public class LocalNetworkDiscovery extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(LocalNetworkDiscovery.class);
    private static final Object path = LocalNetworkDiscovery.class;
    private final Map<IdentityPublicKey, Peer> peers;
    private final DrasylAddress myAddress;
    private final ProofOfWork myProofOfWork;
    private final Duration pingInterval;
    private final Duration pingTimeout;
    private final int networkId;
    private Future<?> scheduledPingFuture;

    public LocalNetworkDiscovery(final Map<IdentityPublicKey, Peer> peers,
                                 final DrasylAddress myAddress,
                                 final ProofOfWork myProofOfWork,
                                 final Duration pingInterval,
                                 final Duration pingTimeout,
                                 final int networkId,
                                 final Future<?> scheduledPingFuture) {
        this.peers = requireNonNull(peers);
        this.myAddress = requireNonNull(myAddress);
        this.myProofOfWork = requireNonNull(myProofOfWork);
        this.pingInterval = requireNonNull(pingInterval);
        this.pingTimeout = requireNonNull(pingTimeout);
        this.networkId = networkId;
        this.scheduledPingFuture = scheduledPingFuture;
    }

    public LocalNetworkDiscovery(final int networkId,
                                 final Duration pingInterval,
                                 final Duration pingTimeout,
                                 final DrasylAddress myAddress,
                                 final ProofOfWork myProofOfWork) {
        this(new ConcurrentHashMap<>(), myAddress, myProofOfWork, pingInterval, pingTimeout, networkId, null);
    }

    void startHeartbeat(final ChannelHandlerContext ctx) {
        if (scheduledPingFuture == null) {
            LOG.debug("Start Network Network Discovery...");
            scheduledPingFuture = ctx.executor().scheduleWithFixedDelay(() -> doHeartbeat(ctx), randomLong(pingInterval.toMillis()), pingInterval.toMillis(), MILLISECONDS);
            LOG.debug("Network Discovery started.");
        }
    }

    void stopHeartbeat() {
        if (scheduledPingFuture != null) {
            LOG.debug("Stop Network Host Discovery...");
            scheduledPingFuture.cancel(false);
            scheduledPingFuture = null;
            LOG.debug("Network Discovery stopped.");
        }
    }

    void clearRoutes(final ChannelHandlerContext ctx) {
        new HashMap<>(peers).forEach(((publicKey, peer) -> {
            ctx.fireUserEventTriggered(RemovePathEvent.of(publicKey, path));
            peers.remove(publicKey);
        }));
        peers.clear();
    }

    void doHeartbeat(final ChannelHandlerContext ctx) {
        removeStalePeers(ctx);
        pingLocalNetworkNodes(ctx);
    }

    private void removeStalePeers(final ChannelHandlerContext ctx) {
        new HashMap<>(peers).forEach(((publicKey, peer) -> {
            if (peer.isStale()) {
                LOG.debug("Last contact from {} is {}ms ago. Remove peer.", () -> publicKey, () -> System.currentTimeMillis() - peer.getLastInboundPingTime());
                ctx.fireUserEventTriggered(RemovePathEvent.of(publicKey, path));
                peers.remove(publicKey);
            }
        }));
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof DiscoveryMessage) {
            final DiscoveryMessage discoveryMsg = ((AddressedMessage<DiscoveryMessage, ?>) msg).message();
            final SocketAddress sender = ((AddressedMessage<DiscoveryMessage, ?>) msg).address();

            if (scheduledPingFuture != null && sender instanceof InetSocketAddress && discoveryMsg.getRecipient() == null) {
                handlePing(ctx, (InetSocketAddress) sender, discoveryMsg, new CompletableFuture<>());
            }
            else {
                ctx.fireChannelRead(msg);
            }
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    private void handlePing(final ChannelHandlerContext ctx,
                            final InetSocketAddress sender,
                            final RemoteMessage msg,
                            final CompletableFuture<Void> future) {
        final IdentityPublicKey msgSender = msg.getSender();
        if (!myAddress.equals(msgSender)) {
            LOG.debug("Got multicast discovery message for `{}` from address `{}`", msgSender, sender);
            final Peer peer = peers.computeIfAbsent(msgSender, key -> new Peer(sender, pingTimeout));
            peer.inboundPingOccurred();
            ctx.fireUserEventTriggered(AddPathEvent.of(msgSender, sender, path));
        }

        future.complete(null);
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof RemoteMessage) {
            final RemoteMessage remoteMsg = ((AddressedMessage<RemoteMessage, ?>) msg).message();
            final SocketAddress recipient = ((AddressedMessage<RemoteMessage, ?>) msg).address();

            final Peer peer = peers.get(recipient);
            if (peer != null) {
                LOG.trace("[{}] Send message `{}` via local network route `{}`.", ctx.channel()::id, () -> remoteMsg, peer::getAddress);
                ctx.write(((AddressedMessage<?, ?>) msg).route(peer.getAddress()), promise);
            }
            else {
                ctx.write(msg, promise);
            }
        }
        else {
            ctx.write(msg, promise);
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
        clearRoutes(ctx);

        ctx.fireChannelInactive();
    }

    private void pingLocalNetworkNodes(final ChannelHandlerContext ctx) {
        final DiscoveryMessage messageEnvelope = DiscoveryMessage.of(networkId, (IdentityPublicKey) myAddress, myProofOfWork);
        LOG.debug("Send {} to {}", messageEnvelope, MULTICAST_ADDRESS);
        ctx.write(new AddressedMessage<>(messageEnvelope, MULTICAST_ADDRESS)).addListener(future -> {
            if (!future.isSuccess()) {
                LOG.warn("Unable to send discovery message to multicast group `{}`", () -> MULTICAST_ADDRESS, future::cause);
            }
        });
    }

    static class Peer {
        private final Duration pingTimeout;
        private final SocketAddress address;
        private long lastInboundPingTime;

        Peer(final Duration pingTimeout, final SocketAddress address,
             final long lastInboundPingTime) {
            this.pingTimeout = pingTimeout;
            this.address = requireNonNull(address);
            this.lastInboundPingTime = lastInboundPingTime;
        }

        public Peer(final SocketAddress address, final Duration pingTimeout) {
            this(pingTimeout, address, 0L);
        }

        public SocketAddress getAddress() {
            return address;
        }

        public void inboundPingOccurred() {
            lastInboundPingTime = System.currentTimeMillis();
        }

        public boolean isStale() {
            return lastInboundPingTime < System.currentTimeMillis() - pingTimeout.toMillis();
        }

        public long getLastInboundPingTime() {
            return lastInboundPingTime;
        }
    }
}
