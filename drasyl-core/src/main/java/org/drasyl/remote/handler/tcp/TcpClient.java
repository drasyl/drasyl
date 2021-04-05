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
package org.drasyl.remote.handler.tcp;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.peer.Endpoint;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.NettyUtil.getBestSocketChannel;

/**
 * This handler monitors how long the node has not received a response from any super peer. If the
 * super peers have not responded for {@code drasyl.remote.tcp-fallback.client.timeout}, an attempt
 * is made to connect to {@code drasyl.remote.tcp-fallback.client.address} via TCP. If a TCP-based
 * connection can be established, all messages are also sent via TCP. As soon as a super peer
 * responds (again) via UDP, the TCP connection is closed.
 * <p>
 * This client is used as a last resort when otherwise no connection to a super peer can be
 * established (e.g. because the node operates in a very restrictive network that does not allow
 * UDP-based traffic). In this case, no direct connections to other peers are available and all
 * messages must be relayed through the fallback connection.
 * <p>
 * This client is only used if the node does not act as a super peer itself.
 */
@SuppressWarnings({ "java:S110" })
public class TcpClient extends SimpleDuplexHandler<ByteBuf, ByteBuf, InetSocketAddressWrapper> {
    private static final Logger LOG = LoggerFactory.getLogger(TcpClient.class);
    private final Set<InetSocketAddressWrapper> superPeerAddresses;
    private final Bootstrap bootstrap;
    private final AtomicLong noResponseFromSuperPeerSince;
    private ChannelFuture superPeerChannel;

    TcpClient(final Set<InetSocketAddressWrapper> superPeerAddresses,
              final Bootstrap bootstrap,
              final AtomicLong noResponseFromSuperPeerSince,
              final ChannelFuture superPeerChannel) {
        this.superPeerAddresses = requireNonNull(superPeerAddresses);
        this.bootstrap = requireNonNull(bootstrap);
        this.noResponseFromSuperPeerSince = requireNonNull(noResponseFromSuperPeerSince);
        this.superPeerChannel = superPeerChannel;
    }

    public TcpClient(final DrasylConfig config) {
        this(
                config.getRemoteSuperPeerEndpoints().stream().map(Endpoint::toInetSocketAddress).collect(Collectors.toSet()),
                new Bootstrap().group(EventLoopGroupUtil.getInstanceBest()).channel(getBestSocketChannel()),
                new AtomicLong(),
                null
        );
    }

    @Override
    public void onEvent(final HandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            // stop all clients
            stopClient();
        }

        ctx.passEvent(event, future);
    }

    private synchronized void stopClient() {
        if (superPeerChannel != null) {
            superPeerChannel.cancel(true);

            if (superPeerChannel.isSuccess()) {
                superPeerChannel.channel().close();
            }
            superPeerChannel = null;
        }
    }

    @Override
    protected void matchedInbound(final HandlerContext ctx,
                                  final InetSocketAddressWrapper sender,
                                  final ByteBuf msg,
                                  final CompletableFuture<Void> future) throws Exception {
        ctx.passInbound(sender, msg, future);

        checkForReachableSuperPeer(sender);
    }

    /**
     * This method is called whenever a message is received. It checks whether a message has been
     * received from a super peer and closes the fallback TCP connection if necessary.
     */
    private void checkForReachableSuperPeer(final InetSocketAddressWrapper sender) {
        // message from super peer?
        if (superPeerAddresses.contains(sender)) {
            // super peer(s) reachable via udp -> close fallback connection!
            noResponseFromSuperPeerSince.set(0);
            stopClient();
        }
    }

    @Override
    protected void matchedOutbound(final HandlerContext ctx,
                                   final InetSocketAddressWrapper recipient,
                                   final ByteBuf msg,
                                   final CompletableFuture<Void> future) throws Exception {
        // check if we can route the message via a tcp connection
        final ChannelFuture mySuperPeerChannel = this.superPeerChannel;
        if (mySuperPeerChannel != null && mySuperPeerChannel.isSuccess()) {
            LOG.trace("Send message `{}` via TCP connection to `{}`.", () -> msg, () -> recipient);
            FutureCombiner.getInstance()
                    .add(FutureUtil.toFuture(mySuperPeerChannel.channel().writeAndFlush(msg)))
                    .combine(future);
        }
        else {
            // passthrough message
            ctx.passOutbound(recipient, msg, future);

            checkForUnreachableSuperPeers(ctx, recipient);
        }
    }

    /**
     * This method is called every time a message is sent. It checks how long no response was
     * received from a super peer and then tries to establish a fallback TCP connection if
     * necessary.
     */
    private void checkForUnreachableSuperPeers(final HandlerContext ctx,
                                               final InetSocketAddressWrapper recipient) {
        // message to super peer?
        if (superPeerAddresses.contains(recipient)) {
            final long currentTimeMillis = System.currentTimeMillis();
            noResponseFromSuperPeerSince.compareAndSet(0, currentTimeMillis);
            if (noResponseFromSuperPeerSince.get() < currentTimeMillis - ctx.config().getRemoteTcpFallbackClientTimeout().toMillis()) {
                // no response from super peer(s) for a too long duration -> establish fallback connection!
                startClient(ctx);
            }
        }
    }

    @SuppressWarnings({ "java:S1905", "java:S3824" })
    private synchronized void startClient(final HandlerContext ctx) {
        if (superPeerChannel == null) {
            final long currentTime = System.currentTimeMillis();
            LOG.debug("No response from any super peer since {}ms. UDP traffic" +
                    " blocked!? Try to reach a super peer via TCP.", () -> currentTime - noResponseFromSuperPeerSince.get());

            // reset counter so that no connection is attempted more often then defined in `drasyl.remote.tcp-fallback.client.timeout`
            noResponseFromSuperPeerSince.set(currentTime);

            superPeerChannel = bootstrap
                    .handler(new TcpClientHandler(ctx))
                    .connect(ctx.config().getRemoteTcpFallbackClientAddress());
            superPeerChannel.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    final Channel channel = future.channel();
                    LOG.debug("TCP connection to `{}` established.", ctx.config().getRemoteTcpFallbackClientAddress());
                    channel.closeFuture().addListener(future1 -> {
                        LOG.debug("TCP connection to `{}` closed.", ctx.config().getRemoteTcpFallbackClientAddress());
                        superPeerChannel = null;
                    });
                }
                else {
                    LOG.debug("Unable to establish TCP connection to `{}`:", () -> ctx.config().getRemoteTcpFallbackClientAddress(), future::cause);
                    superPeerChannel = null;
                }
            });
        }
    }

    /**
     * This handler passes all receiving messages to the pipeline.
     */
    static class TcpClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final HandlerContext ctx;

        public TcpClientHandler(
                final HandlerContext ctx) {
            this.ctx = requireNonNull(ctx);
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext nettyCtx,
                                    final ByteBuf msg) {
            LOG.trace("Packet `{}` received via TCP from `{}`", () -> msg, nettyCtx.channel()::remoteAddress);
            final InetSocketAddress sender = (InetSocketAddress) nettyCtx.channel().remoteAddress();
            ctx.passInbound(new InetSocketAddressWrapper(sender), msg.retain(), new CompletableFuture<>());
        }
    }
}
