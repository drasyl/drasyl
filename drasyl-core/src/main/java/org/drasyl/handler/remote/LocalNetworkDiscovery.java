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
import io.netty.util.concurrent.Future;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.HelloMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
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
    private final InetSocketAddress recipient;
    private Future<?> scheduledPingFuture;

    LocalNetworkDiscovery(final InetSocketAddress recipient,
                          final Future<?> scheduledPingFuture) {
        this.recipient = requireNonNull(recipient);
        this.scheduledPingFuture = scheduledPingFuture;
    }

    public LocalNetworkDiscovery(final InetSocketAddress recipient) {
        this(recipient, null);
    }

    void startHeartbeat(final ChannelHandlerContext ctx) {
        if (scheduledPingFuture == null) {
            LOG.debug("Start Network Network Discovery...");
            final long helloInterval = ((DrasylServerChannelConfig) ctx.channel().config()).getHelloInterval().toMillis();
            scheduledPingFuture = ctx.executor().scheduleWithFixedDelay(() -> doHeartbeat(ctx), randomLong(helloInterval), helloInterval, MILLISECONDS);
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
        config(ctx).getPeersManager().removeClientPaths(ctx, PATH_ID);
    }

    void doHeartbeat(final ChannelHandlerContext ctx) {
        removeStalePeers(ctx);
        pingLocalNetworkNodes(ctx);
    }

    private void removeStalePeers(final ChannelHandlerContext ctx) {
        for (final Iterator<DrasylAddress> it = config(ctx).getPeersManager().getPeers(PATH_ID).iterator();
             it.hasNext(); ) {
            final DrasylAddress publicKey = it.next();
            final boolean stale = config(ctx).getPeersManager().isStale(publicKey, PATH_ID);

            if (stale) {
                final long lastInboundHelloTime = config(ctx).getPeersManager().lastHelloMessageReceivedTime(publicKey, PATH_ID);
                LOG.debug("Last contact from {} is {}ms ago. Remove peer.", () -> publicKey, () -> System.currentTimeMillis() - lastInboundHelloTime);
                config(ctx).getPeersManager().removeClientPath(ctx, publicKey, PATH_ID);
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
            config(ctx).getPeersManager().addClientPath(ctx, msgSender, PATH_ID, sender, PATH_PRIORITY);
            config(ctx).getPeersManager().helloMessageReceived(msgSender, PATH_ID);
        }

        future.complete(null);
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
        final HelloMessage messageEnvelope = HelloMessage.of(config(ctx).getNetworkId(), ((DrasylChannel) ctx.channel()).identity().getIdentityPublicKey(), ((DrasylChannel) ctx.channel()).identity().getProofOfWork());
        LOG.debug("Send {} to {}", messageEnvelope, recipient);
        ctx.writeAndFlush(new InetAddressedMessage<>(messageEnvelope, recipient)).addListener(future -> {
            if (!future.isSuccess()) {
                LOG.warn("Unable to send local network discovery message to `{}`", () -> recipient, future::cause);
            }
        });
    }

    private static DrasylServerChannelConfig config(final ChannelHandlerContext ctx) {
        return (DrasylServerChannelConfig) ctx.channel().config();
    }
}
