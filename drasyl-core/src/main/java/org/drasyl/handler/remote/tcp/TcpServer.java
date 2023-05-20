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
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Starts a TCP-based server, allowing clients in very restrictive networks that do not allow
 * UDP-based traffic to connect to this super peer via TCP.
 * <p>
 * This server is only used if the node act as a super peer.
 */
@UnstableApi
public class TcpServer extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TcpServer.class);
    static final boolean STATUS_ENABLED = SystemPropertyUtil.getBoolean("org.drasyl.status.enabled", true);
    static final byte[] HTTP_OK = "HTTP/1.1 200 OK\nContent-Length:0".getBytes(UTF_8);
    private final ServerBootstrap bootstrap;
    private final Map<SocketAddress, Channel> clientChannels;
    private final InetAddress bindHost;
    private final int bindPort;
    private final Duration pingTimeout;
    private final EventLoopGroup group;
    private final Function<ChannelHandlerContext, ChannelInitializer<SocketChannel>> channelInitializerSupplier;
    private Channel serverChannel;

    /**
     * @param group                      the {@link NioEventLoopGroup} the underlying tcp server
     *                                   should run on
     * @param channelInitializerSupplier
     */
    @SuppressWarnings("java:S107")
    TcpServer(final ServerBootstrap bootstrap,
              final NioEventLoopGroup group,
              final Map<SocketAddress, Channel> clientChannels,
              final InetAddress bindHost,
              final int bindPort,
              final Duration pingTimeout,
              final Function<ChannelHandlerContext, ChannelInitializer<SocketChannel>> channelInitializerSupplier,
              final Channel serverChannel) {
        this.bootstrap = requireNonNull(bootstrap);
        this.clientChannels = requireNonNull(clientChannels);
        this.bindHost = bindHost;
        this.bindPort = bindPort;
        this.pingTimeout = pingTimeout;
        this.group = requireNonNull(group);
        this.channelInitializerSupplier = requireNonNull(channelInitializerSupplier);
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
                TcpServerChannelInitializer::new,
                null
        );
    }

    @SuppressWarnings("unchecked")
    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof RemoteMessage) {
            final SocketAddress recipient = ((InetAddressedMessage<RemoteMessage>) msg).recipient();

            // check if we can route the message via a tcp connection
            final Channel client = clientChannels.get(recipient);
            if (client != null) {
                LOG.trace("Send message `{}` via TCP to client `{}`", msg, recipient);
                PromiseNotifier.cascade(client.writeAndFlush(msg), promise);
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
                .option(ChannelOption.IP_TOS, UdpServer.IP_TOS)
                .group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(channelInitializerSupplier.apply(ctx))
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

    Map<SocketAddress, Channel> clientChannels() {
        return clientChannels;
    }

    Duration pingTimeout() {
        return pingTimeout;
    }

    private class TcpServerFutureListener implements ChannelFutureListener {
        private final ChannelHandlerContext ctx;

        TcpServerFutureListener(final ChannelHandlerContext ctx) {
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
