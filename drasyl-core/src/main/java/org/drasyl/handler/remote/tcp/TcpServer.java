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
package org.drasyl.handler.remote.tcp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Starts a TCP-based server, allowing clients in very restrictive networks that do not allow
 * UDP-based traffic to connect to this super peer via TCP.
 * <p>
 * This server is only used if the node act as a super peer.
 */
public class TcpServer extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TcpServer.class);
    private static final boolean STATUS_ENABLED = SystemPropertyUtil.getBoolean("org.drasyl.status.enabled", true);
    static final byte[] HTTP_OK = "HTTP/1.1 200 OK\nContent-Length:0".getBytes(UTF_8);
    private final ServerBootstrap bootstrap;
    private final Map<SocketAddress, Channel> clientChannels;
    private final InetAddress bindHost;
    private final int bindPort;
    private final Duration pingTimeout;
    private final EventLoopGroup group;
    private Channel serverChannel;

    /**
     * @param group the {@link NioEventLoopGroup} the underlying tcp server should run on
     */
    @SuppressWarnings("java:S107")
    TcpServer(final ServerBootstrap bootstrap,
              final NioEventLoopGroup group,
              final Map<SocketAddress, Channel> clientChannels,
              final InetAddress bindHost,
              final int bindPort,
              final Duration pingTimeout,
              final Channel serverChannel) {
        this.bootstrap = requireNonNull(bootstrap);
        this.clientChannels = requireNonNull(clientChannels);
        this.bindHost = bindHost;
        this.bindPort = bindPort;
        this.pingTimeout = pingTimeout;
        this.group = requireNonNull(group);
        this.serverChannel = serverChannel;
    }

    /**
     * @param group the {@link NioEventLoopGroup} the underlying tcp server should run on
     */
    public TcpServer(final NioEventLoopGroup group,
                     final InetAddress bindHost,
                     final int bindPort,
                     final Duration pingTimeout) {
        this(
                new ServerBootstrap(),
                group,
                new ConcurrentHashMap<>(),
                bindHost,
                bindPort,
                pingTimeout,
                null
        );
    }

    @SuppressWarnings("unchecked")
    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof ByteBuf) {
            final ByteBuf byteBufMsg = ((InetAddressedMessage<ByteBuf>) msg).content();
            final SocketAddress recipient = ((InetAddressedMessage<ByteBuf>) msg).recipient();

            // check if we can route the message via a tcp connection
            final Channel client = clientChannels.get(recipient);
            if (client != null) {
                LOG.trace("Send message `{}` via TCP to client `{}`", byteBufMsg, recipient);
                PromiseNotifier.cascade(client.writeAndFlush(byteBufMsg), promise);
            }
            else {
                // message is not addressed to any of our clients. pass through message
                ctx.write(msg, promise);
            }
        }
        else {
            ctx.write(msg, promise);
        }
    }

    @SuppressWarnings("java:S1905")
    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws TcpServerBindFailedException {
        LOG.debug("Start Server...");
        bootstrap
                .group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(new TcpServerChannelInitializer(clientChannels, ctx, pingTimeout))
                .bind(bindHost, bindPort)
                .addListener(new TcpServerFutureListener(ctx));
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        ctx.fireChannelInactive();

        if (serverChannel != null) {
            LOG.debug("Stop Server listening at tcp:/{}...", serverChannel.localAddress());
            // shutdown server
            serverChannel.close().addListener(future -> {
                serverChannel = null;
                LOG.debug("Server stopped.");
            });
        }
    }

    private class TcpServerFutureListener implements ChannelFutureListener {
        private final ChannelHandlerContext ctx;

        public TcpServerFutureListener(final ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void operationComplete(final ChannelFuture future) {
            if (future.isSuccess()) {
                // server successfully started
                TcpServer.this.serverChannel = future.channel();
                final InetSocketAddress socketAddress = (InetSocketAddress) serverChannel.localAddress();
                LOG.info("Server started and listening at tcp:/{}", socketAddress);

                ctx.fireUserEventTriggered(new TcpServerBound(socketAddress));
                ctx.fireChannelActive();
            }
            else {
                // server start failed
                ctx.fireExceptionCaught(new TcpServerBindFailedException("Unable to bind server to address tcp://" + bindHost + ":" + bindPort, future.cause()));
            }
        }
    }

    static class TcpServerChannelInitializer extends ChannelInitializer<Channel> {
        private final Map<SocketAddress, Channel> clients;
        private final ChannelHandlerContext ctx;
        private final Duration pingTimeout;

        public TcpServerChannelInitializer(final Map<SocketAddress, Channel> clients,
                                           final ChannelHandlerContext ctx,
                                           final Duration pingTimeout) {
            this.clients = requireNonNull(clients);
            this.ctx = requireNonNull(ctx);
            this.pingTimeout = pingTimeout;
        }

        @Override
        protected void initChannel(final Channel ch) {
            ch.pipeline().addLast(new IdleStateHandler(pingTimeout.toMillis(), 0, 0, MILLISECONDS));
            ch.pipeline().addLast(new TcpServerHandler(clients, ctx));
        }
    }

    /**
     * This handler passes all receiving messages to the pipeline and updates {@link #clients} on
     * new/closed connections.
     */
    static class TcpServerHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final Map<SocketAddress, Channel> clients;
        private final ChannelHandlerContext ctx;

        public TcpServerHandler(final Map<SocketAddress, Channel> clients,
                                final ChannelHandlerContext ctx) {
            super(false);
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

            // drasyl message?
            if (msg.readableBytes() >= Integer.BYTES) {
                msg.markReaderIndex();
                final int magicNumber = msg.readInt();

                if (RemoteMessage.MAGIC_NUMBER == magicNumber) {
                    msg.resetReaderIndex();
                    final InetSocketAddress sender = (InetSocketAddress) nettyCtx.channel().remoteAddress();
                    ctx.executor().execute(() -> {
                        ctx.fireChannelRead(new InetAddressedMessage<>(msg, null, sender));
                        ctx.fireChannelReadComplete();
                    });
                }
                else {
                    LOG.debug("Close TCP connection to `{}` because peer send non-drasyl message (wrong magic number).", nettyCtx.channel()::remoteAddress);
                    msg.release();
                    if (STATUS_ENABLED) {
                        nettyCtx.writeAndFlush(ctx.alloc().buffer(HTTP_OK.length).writeBytes(HTTP_OK)).addListener(l -> nettyCtx.close());
                    }
                    else {
                        nettyCtx.close();
                    }
                }
            }
            else {
                LOG.debug("Close TCP connection to `{}` because peer send non-drasyl message (too short).", nettyCtx.channel()::remoteAddress);
                msg.release();
                if (STATUS_ENABLED) {
                    nettyCtx.writeAndFlush(ctx.alloc().buffer(HTTP_OK.length).writeBytes(HTTP_OK)).addListener(l -> nettyCtx.close());
                }
                else {
                    nettyCtx.close();
                }
            }
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
            LOG.debug("Close TCP connection to `{}` due to an exception: ", ctx.channel()::remoteAddress, () -> cause);
            ctx.close();
        }
    }

    /**
     * Signals that the {@link TcpServer} is bind to {@link TcpServerBound#getPort()}.
     */
    public static class TcpServerBound {
        private final InetSocketAddress bindAddress;

        public TcpServerBound(final InetSocketAddress bindAddress) {
            this.bindAddress = requireNonNull(bindAddress);
        }

        public InetSocketAddress getBindAddress() {
            return bindAddress;
        }

        public int getPort() {
            return getBindAddress().getPort();
        }
    }

    /**
     * Signals that the {@link TcpServer} was unable to bind to port.
     */
    public static class TcpServerBindFailedException extends Exception {
        public TcpServerBindFailedException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
