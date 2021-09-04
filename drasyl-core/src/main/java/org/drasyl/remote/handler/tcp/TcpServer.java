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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.event.Event;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.remote.protocol.RemoteMessage.MAGIC_NUMBER;
import static org.drasyl.util.NettyUtil.getBestServerSocketChannel;
import static org.drasyl.util.Preconditions.requireNonNegative;

/**
 * Starts a TCP-based server, allowing clients in very restrictive networks that do not allow
 * UDP-based traffic to connect to this super peer via TCP.
 * <p>
 * This server is only used if the node act as a super peer.
 */
public class TcpServer extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TcpServer.class);
    private final ServerBootstrap bootstrap;
    private final Map<SocketAddress, Channel> clientChannels;
    private final InetAddress bindHost;
    private final int bindPort;
    private final Duration pingTimeout;
    private Channel serverChannel;

    public TcpServer(final InetAddress bindHost, final int bindPort, final Duration pingTimeout) {
        this(
                new ServerBootstrap().group(EventLoopGroupUtil.getInstanceBest(), EventLoopGroupUtil.getInstanceBest()).channel(getBestServerSocketChannel()),
                new ConcurrentHashMap<>(),
                bindHost,
                bindPort,
                pingTimeout,
                null
        );
    }

    TcpServer(final ServerBootstrap bootstrap,
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
        this.serverChannel = serverChannel;
    }

    private void startServer(final ChannelHandlerContext ctx) throws BindFailedException {
        LOG.debug("Start Server...");
        final ChannelFuture channelFuture = bootstrap
                .childHandler(new TcpServerChannelInitializer(clientChannels, ctx, pingTimeout))
                .bind(bindHost, bindPort);
        channelFuture.awaitUninterruptibly();

        if (channelFuture.isSuccess()) {
            // server successfully started
            this.serverChannel = channelFuture.channel();
            final InetSocketAddress socketAddress = (InetSocketAddress) serverChannel.localAddress();
            LOG.debug("Server started and listening at tcp:/{}", socketAddress);

            // consume NodeUpEvent and publish NodeUpEvent with port
            ctx.fireUserEventTriggered(new Port(socketAddress.getPort()));
        }
        else {
            // server start failed
            throw new BindFailedException("Unable to bind server to address tcp://" + bindHost + ":" + bindPort, channelFuture.cause());
        }
    }

    private void stopServer() {
        final SocketAddress socketAddress = serverChannel.localAddress();
        LOG.debug("Stop Server listening at tcp:/{}...", socketAddress);
        // shutdown server
        serverChannel.close().awaitUninterruptibly();
        serverChannel = null;
        LOG.debug("Server stopped");
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof ByteBuf && ((AddressedMessage<?, ?>) msg).address() instanceof InetSocketAddress) {
            final SocketAddress recipient = ((AddressedMessage<?, ?>) msg).address();
            final ByteBuf byteBufMsg = (ByteBuf) ((AddressedMessage<?, ?>) msg).message();

            // check if we can route the message via a tcp connection
            final Channel client = clientChannels.get(recipient);
            if (client != null) {
                LOG.trace("Send message `{}` via TCP to client `{}`", byteBufMsg, recipient);
                client.writeAndFlush(byteBufMsg).addListener(future -> {
                    if (future.isSuccess()) {
                        promise.setSuccess();
                    }
                    else {
                        promise.setFailure(future.cause());
                    }
                });
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

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws BindFailedException {
        startServer(ctx);

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        ctx.fireChannelInactive();

        stopServer();
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

                if (MAGIC_NUMBER == magicNumber) {
                    msg.resetReaderIndex();
                    final SocketAddress sender = nettyCtx.channel().remoteAddress();
                    ctx.fireChannelRead(new AddressedMessage<>(msg, sender));
                }
                else {
                    LOG.debug("Close TCP connection to `{}` because peer send non-drasyl message (wrong magic number).", nettyCtx.channel()::remoteAddress);
                    msg.release();
                    nettyCtx.close();
                }
            }
            else {
                LOG.debug("Close TCP connection to `{}` because peer send non-drasyl message (too short).", nettyCtx.channel()::remoteAddress);
                msg.release();
                nettyCtx.close();
            }
        }
    }

    /**
     * Signals that the {@link TcpServer} is bind to {@link TcpServer.Port#getPort()}.
     */
    public static class Port implements Event {
        private final int value;

        public Port(final int value) {
            this.value = requireNonNegative(value, "port must be non-negative");
        }

        public int getPort() {
            return value;
        }
    }

    /**
     * Signals that the {@link TcpServer} was unable to bind to port.
     */
    public static class BindFailedException extends Exception {
        public BindFailedException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
