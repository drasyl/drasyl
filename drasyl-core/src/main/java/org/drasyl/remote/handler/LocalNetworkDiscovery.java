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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Future;
import org.drasyl.DrasylAddress;
import org.drasyl.channel.AddPathEvent;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.RemovePathEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.DiscoveryMessage;
import org.drasyl.remote.protocol.Protocol.Discovery;
import org.drasyl.remote.protocol.RemoteMessage;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;
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
import static org.drasyl.remote.handler.UdpMulticastServer.MULTICAST_ADDRESS;
import static org.drasyl.util.RandomUtil.randomLong;

/**
 * This handler, along with the {@link UdpMulticastServer}, is used to discover other nodes on the
 * local network.
 * <p>
 * For this purpose, the {@link UdpMulticastServer} joins a multicast group and forwards received
 * {@link Discovery} messages to this handler, which thus becomes aware of other nodes in the local
 * network. In case no {@link Discovery} message has been received for a longer period of time, the
 * other node is considered stale.
 * <p>
 * In addition, this handler periodically sends a {@link Discovery} messages to a multicast group so
 * that other nodes become aware of this node.
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
    private Future<?> pingDisposable;

    public LocalNetworkDiscovery(final Map<IdentityPublicKey, Peer> peers,
                                 final DrasylAddress myAddress,
                                 final ProofOfWork myProofOfWork,
                                 final Duration pingInterval,
                                 final Duration pingTimeout,
                                 final int networkId,
                                 final Future<?> pingDisposable) {
        this.peers = requireNonNull(peers);
        this.myAddress = requireNonNull(myAddress);
        this.myProofOfWork = requireNonNull(myProofOfWork);
        this.pingInterval = pingInterval;
        this.pingTimeout = pingTimeout;
        this.networkId = networkId;
        this.pingDisposable = pingDisposable;
    }

    public LocalNetworkDiscovery(final int networkId,
                                 final Duration pingInterval,
                                 final Duration pingTimeout, final DrasylAddress myAddress,
                                 final ProofOfWork myProofOfWork) {
        this(new ConcurrentHashMap<>(), myAddress, myProofOfWork, pingInterval, pingTimeout, networkId, null);
    }

    synchronized void startHeartbeat(final ChannelHandlerContext ctx) {
        if (pingDisposable == null) {
            LOG.debug("Start Network Network Discovery...");
            pingDisposable = ctx.executor().scheduleAtFixedRate(() -> doHeartbeat(ctx), randomLong(pingInterval.toMillis()), pingInterval.toMillis(), MILLISECONDS);
            LOG.debug("Network Discovery started.");
        }
    }

    synchronized void stopHeartbeat() {
        if (pingDisposable != null) {
            LOG.debug("Stop Network Host Discovery...");
            pingDisposable.cancel(false);
            pingDisposable = null;
            LOG.debug("Network Discovery stopped.");
        }
    }

    synchronized void clearRoutes(final ChannelHandlerContext ctx) {
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
            final SocketAddress sender = ((AddressedMessage<?, ?>) msg).address();
            final DiscoveryMessage discoveryMsg = (DiscoveryMessage) ((AddressedMessage<?, ?>) msg).message();

            if (pingDisposable != null && sender instanceof InetSocketAddress && discoveryMsg.getRecipient() == null) {
                handlePing(ctx, sender, discoveryMsg, new CompletableFuture<>());
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
                            final SocketAddress sender,
                            final RemoteMessage msg,
                            final CompletableFuture<Void> future) {
        final IdentityPublicKey msgSender = msg.getSender();
        if (!myAddress.equals(msgSender)) {
            LOG.debug("Got multicast discovery message for `{}` from address `{}`", msgSender, sender);
            final Peer peer = peers.computeIfAbsent(msgSender, key -> new Peer(sender, pingTimeout));
            peer.inboundPingOccurred();
            ctx.fireUserEventTriggered(AddPathEvent.of(msgSender, path));
        }

        future.complete(null);
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof RemoteMessage) {
            final RemoteMessage remoteMsg = (RemoteMessage) ((AddressedMessage<?, ?>) msg).message();
            final SocketAddress recipient = ((AddressedMessage<?, ?>) msg).address();

            final Peer peer = peers.get(recipient);
            if (peer != null) {
                LOG.trace("Send message `{}` via local network route `{}`.", () -> remoteMsg, peer::getAddress);
                ctx.write(new AddressedMessage<>(remoteMsg, peer.getAddress()), promise);
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
        final CompletableFuture<Void> future = new CompletableFuture<>();
        FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new AddressedMessage<>(messageEnvelope, MULTICAST_ADDRESS)))).combine(future);
        future.exceptionally(e -> {
            LOG.warn("Unable to send discovery message to multicast group `{}`", () -> MULTICAST_ADDRESS, () -> e);
            return null;
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
