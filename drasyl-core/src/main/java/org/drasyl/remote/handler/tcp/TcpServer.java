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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.remote.protocol.InvalidMessageFormatException;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.NettyUtil.getBestServerSocketChannel;

/**
 * Starts a TCP-based server, allowing clients in very restrictive networks that do not allow
 * UDP-based traffic to connect to this super peer via TCP.
 * <p>
 * This server is only used if the node act as a super peer.
 */
public class TcpServer extends SimpleOutboundHandler<ByteBuf, InetSocketAddressWrapper> {
    private static final Logger LOG = LoggerFactory.getLogger(TcpServer.class);
    private final ServerBootstrap bootstrap;
    private final Map<SocketAddress, Channel> clientChannels;
    private Channel serverChannel;

    public TcpServer() {
        this(
                new ServerBootstrap().group(EventLoopGroupUtil.getInstanceBest(), EventLoopGroupUtil.getInstanceBest()).channel(getBestServerSocketChannel()),
                new ConcurrentHashMap<>(),
                null
        );
    }

    TcpServer(final ServerBootstrap bootstrap,
              final Map<SocketAddress, Channel> clientChannels,
              final Channel serverChannel) {
        this.bootstrap = requireNonNull(bootstrap);
        this.clientChannels = requireNonNull(clientChannels);
        this.serverChannel = serverChannel;
    }

    @Override
    public void onEvent(final HandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        if (event instanceof NodeUpEvent) {
            startServer(ctx, (NodeUpEvent) event, future);
        }
        else if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            stopServer(ctx, event, future);
        }
        else {
            // passthrough event
            ctx.passEvent(event, future);
        }
    }

    private synchronized void startServer(final HandlerContext ctx,
                                          final NodeUpEvent event,
                                          final CompletableFuture<Void> future) {
        if (serverChannel == null) {
            LOG.debug("Start Server...");
            final ChannelFuture channelFuture = bootstrap
                    .childHandler(new TcpServerChannelInitializer(clientChannels, ctx))
                    .bind(ctx.config().getRemoteTcpFallbackServerBindHost(), ctx.config().getRemoteTcpFallbackServerBindPort());
            channelFuture.awaitUninterruptibly();

            if (channelFuture.isSuccess()) {
                // server successfully started
                this.serverChannel = channelFuture.channel();
                final InetSocketAddress socketAddress = (InetSocketAddress) serverChannel.localAddress();
                LOG.debug("Server started and listening at tcp:/{}", socketAddress);

                // consume NodeUpEvent and publish NodeUpEvent with port
                ctx.passEvent(NodeUpEvent.of(Node.of(ctx.identity(), event.getNode().getPort(), socketAddress.getPort())), future);
            }
            else {
                // server start failed
                //noinspection unchecked
                LOG.warn("Unable to bind server to address tcp://{}:{}: {}", ctx.config()::getRemoteBindHost, ctx.config()::getRemoteTcpFallbackServerBindPort, channelFuture.cause()::getMessage);
            }
        }

        // passthrough event
        ctx.passEvent(event, future);
    }

    private synchronized void stopServer(final HandlerContext ctx,
                                         final Event event,
                                         final CompletableFuture<Void> future) {
        if (serverChannel != null) {
            final InetSocketAddress socketAddress = (InetSocketAddress) serverChannel.localAddress();
            LOG.debug("Stop Server listening at tcp:/{}...", socketAddress);
            // shutdown server
            serverChannel.close().awaitUninterruptibly();
            serverChannel = null;
            LOG.debug("Server stopped");
        }

        // passthrough event
        ctx.passEvent(event, future);
    }

    @Override
    protected void matchedOutbound(final HandlerContext ctx,
                                   final InetSocketAddressWrapper recipient,
                                   final ByteBuf msg,
                                   final CompletableFuture<Void> future) throws Exception {
        // check if we can route the message via a tcp connection
        final Channel client = clientChannels.get(recipient);
        if (client != null) {
            if (client.isWritable()) {
                LOG.trace("Send message `{}` via TCP to client `{}`", msg, recipient);
                FutureCombiner.getInstance()
                        .add(FutureUtil.toFuture(client.writeAndFlush(msg)))
                        .combine(future);
            }
            else {
                ReferenceCountUtil.safeRelease(msg);
                future.completeExceptionally(new Exception("TCP channel is not writable."));
            }
        }
        else {
            // message is not addressed to any of our clients. passthrough message
            ctx.passOutbound(recipient, msg, future);
        }
    }

    static class TcpServerChannelInitializer extends ChannelInitializer<Channel> {
        private final Map<SocketAddress, Channel> clients;
        private final HandlerContext ctx;

        public TcpServerChannelInitializer(final Map<SocketAddress, Channel> clients,
                                           final HandlerContext ctx) {
            this.clients = requireNonNull(clients);
            this.ctx = requireNonNull(ctx);
        }

        @Override
        protected void initChannel(final Channel ch) {
            ch.pipeline().addLast(new IdleStateHandler(ctx.config().getRemotePingTimeout().toMillis(), 0, 0, MILLISECONDS));
            ch.pipeline().addLast(new TcpServerHandler(clients, ctx));
        }
    }

    /**
     * This handler passes all receiving messages to the pipeline and updates {@link #clients} on
     * new/closed connections.
     */
    static class TcpServerHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final Map<SocketAddress, Channel> clients;
        private final HandlerContext ctx;

        public TcpServerHandler(final Map<SocketAddress, Channel> clients,
                                final HandlerContext ctx) {
            this.clients = requireNonNull(clients);
            this.ctx = requireNonNull(ctx);
        }

        @Override
        public void channelActive(final ChannelHandlerContext nettyCtx) {
            LOG.debug("New TCP connection from client `{}`.", nettyCtx.channel()::remoteAddress);
            clients.put(nettyCtx.channel().remoteAddress(), nettyCtx.channel());

            nettyCtx.fireChannelActive();
        }

        @Override
        public void channelInactive(final ChannelHandlerContext nettyCtx) {
            LOG.debug("TCP connection to client `{}` closed.", nettyCtx.channel()::remoteAddress);
            clients.remove(nettyCtx.channel().remoteAddress());

            nettyCtx.fireChannelInactive();
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext nettyCtx, final Object evt) {
            nettyCtx.fireUserEventTriggered(evt);

            if (evt instanceof IdleStateEvent) {
                LOG.debug("Close TCP connection to `{}` due to inactivity.", nettyCtx.channel()::remoteAddress);
                nettyCtx.close();
            }
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext nettyCtx,
                                    final ByteBuf msg) {
            LOG.trace("Packet `{}` received via TCP from `{}`", () -> msg, nettyCtx.channel()::remoteAddress);
            final InetSocketAddress sender = (InetSocketAddress) nettyCtx.channel().remoteAddress();
            ctx.passInbound(new InetSocketAddressWrapper(sender), msg.retain(), new CompletableFuture<>()).exceptionally(e -> {
                if (e.getCause() instanceof InvalidMessageFormatException) {
                    LOG.debug("Close TCP connection to `{}` because a message with an invalid format has been received. Possibly not a drasyl client talks to us!?", nettyCtx.channel()::remoteAddress, () -> e);
                    nettyCtx.close();
                }
                return null;
            });
        }
    }
}
