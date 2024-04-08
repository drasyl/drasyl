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
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.remote.protocol.HelloMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requirePositive;
import static org.drasyl.util.RandomUtil.randomLong;

/**
 * This handler, along with the {@link UdpMulticastServer} or {@link UdpBroadcastServer}, is used to
 * discover other nodes on the local network via IP multicast or broadcast.
 * <p>
 * For this purpose, the above-mentioned server forwards received multicast/broadcast {@link
 * HelloMessage}s to this handler, which thus becomes aware of other nodes in the local network. In
 * case no {@link HelloMessage} has been received for a longer period of time, the other node is
 * considered stale.
 * <p>
 * In addition, this handler periodically sends a {@link HelloMessage} messages to a given
 * multicast/broadcast address so that other nodes become aware of this node.
 *
 * @see UdpMulticastServer
 * @see UdpBroadcastServer
 */
@UnstableApi
@SuppressWarnings("java:S110")
public class LocalNetworkDiscovery extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(LocalNetworkDiscovery.class);
    static final Class<?> PATH_ID = LocalNetworkDiscovery.class;
    static final short PATH_PRIORITY = 90;
    private static final Object path = LocalNetworkDiscovery.class;
    private final IdentityPublicKey myPublicKey;
    private final ProofOfWork myProofOfWork;
    private final long pingIntervalMillis;
    private final int networkId;
    private final InetSocketAddress recipient;
    private final PeersManager peersManager;
    private Future<?> scheduledPingFuture;

    LocalNetworkDiscovery(final IdentityPublicKey myPublicKey,
                          final ProofOfWork myProofOfWork,
                          final long pingIntervalMillis,
                          final int networkId,
                          final InetSocketAddress recipient,
                          final PeersManager peersManager,
                          final Future<?> scheduledPingFuture) {
        this.myPublicKey = requireNonNull(myPublicKey);
        this.myProofOfWork = requireNonNull(myProofOfWork);
        this.pingIntervalMillis = requirePositive(pingIntervalMillis);
        this.networkId = networkId;
        this.recipient = requireNonNull(recipient);
        this.peersManager = requireNonNull(peersManager);
        this.scheduledPingFuture = scheduledPingFuture;
    }

    public LocalNetworkDiscovery(final int networkId,
                                 final long pingIntervalMillis,
                                 final IdentityPublicKey myPublicKey,
                                 final ProofOfWork myProofOfWork,
                                 final InetSocketAddress recipient,
                                 final PeersManager peersManager) {
        this(myPublicKey, myProofOfWork, pingIntervalMillis, networkId, recipient, peersManager, null);
    }

    void startHeartbeat(final ChannelHandlerContext ctx) {
        if (scheduledPingFuture == null) {
            LOG.debug("Start Network Network Discovery...");
            scheduledPingFuture = ctx.executor().scheduleWithFixedDelay(() -> doHeartbeat(ctx), randomLong(pingIntervalMillis), pingIntervalMillis, MILLISECONDS);
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
        peersManager.getPeers(PATH_ID).forEach(peer -> ctx.fireUserEventTriggered(RemovePathEvent.of(peer, path)));
        peersManager.removePaths(PATH_ID);
    }

    void doHeartbeat(final ChannelHandlerContext ctx) {
        removeStalePeers(ctx);
        pingLocalNetworkNodes(ctx);
    }

    private void removeStalePeers(final ChannelHandlerContext ctx) {
        for (final Iterator<DrasylAddress> it = peersManager.getPeers(PATH_ID).iterator();
             it.hasNext(); ) {
            final DrasylAddress publicKey = it.next();
            final boolean stale = peersManager.isStale(publicKey, PATH_ID);

            if (stale) {
                final long lastInboundHelloTime = peersManager.lastInboundHelloTime(publicKey, PATH_ID);
                LOG.debug("Last contact from {} is {}ms ago. Remove peer.", () -> publicKey, () -> System.currentTimeMillis() - lastInboundHelloTime);
                ctx.fireUserEventTriggered(RemovePathEvent.of(publicKey, path));
                peersManager.removePath(publicKey, PATH_ID);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof HelloMessage && ((HelloMessage) ((InetAddressedMessage<?>) msg).content()).getRecipient() == null && scheduledPingFuture != null) {
            final HelloMessage helloMsg = ((InetAddressedMessage<HelloMessage>) msg).content();
            final InetSocketAddress sender = ((InetAddressedMessage<HelloMessage>) msg).sender();
            handlePing(ctx, sender, helloMsg, new CompletableFuture<>());
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    private void handlePing(final ChannelHandlerContext ctx,
                            final InetSocketAddress sender,
                            final RemoteMessage msg,
                            final CompletableFuture<Void> future) {
        final DrasylAddress msgSender = msg.getSender();
        if (!ctx.channel().localAddress().equals(msgSender)) {
            LOG.debug("Got local network discovery message for `{}` from address `{}`", msgSender, sender);
            if (peersManager.addPath(msgSender, PATH_ID, sender, PATH_PRIORITY)) {
                ctx.fireUserEventTriggered(AddPathEvent.of(msgSender, sender, path));
            }
            peersManager.inboundHelloOccurred(msgSender, PATH_ID);
        }

        future.complete(null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) msg).content() instanceof RemoteMessage) {
            final DrasylAddress recipient = ((OverlayAddressedMessage<RemoteMessage>) msg).recipient();

            final InetSocketAddress endpoint = peersManager.getEndpoint(recipient, PATH_ID);
            if (endpoint != null) {
                LOG.trace("Resolve message `{}` for peer `{}` to inet address `{}`.", () -> ((OverlayAddressedMessage<RemoteMessage>) msg).content().getNonce(), () -> recipient, () -> endpoint);
                ctx.write(((OverlayAddressedMessage<RemoteMessage>) msg).resolve(endpoint), promise);
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
        final HelloMessage messageEnvelope = HelloMessage.of(networkId, myPublicKey, myProofOfWork);
        LOG.debug("Send {} to {}", messageEnvelope, recipient);
        ctx.writeAndFlush(new InetAddressedMessage<>(messageEnvelope, recipient)).addListener(future -> {
            if (!future.isSuccess()) {
                LOG.warn("Unable to send local network discovery message to `{}`", () -> recipient, future::cause);
            }
        });
    }
}
