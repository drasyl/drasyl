/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.JavaDrasylServerChannel;
import org.drasyl.channel.JavaDrasylServerChannelConfig;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.Objects;
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
    private final Function<JavaDrasylServerChannel, ChannelInitializer<SocketChannel>> channelInitializerSupplier;
    final Map<IdentityPublicKey, SocketChannel> tcpClientChannels;
    private ServerSocketChannel tcpServerChannel;

    /**
     */
    @SuppressWarnings("java:S107")
    TcpServer(final Function<JavaDrasylServerChannel, ChannelInitializer<SocketChannel>> channelInitializerSupplier,
              final ServerSocketChannel tcpServerChannel,
              final Map<IdentityPublicKey, SocketChannel> tcpClientChannels) {
        this.tcpClientChannels = requireNonNull(tcpClientChannels);
        this.channelInitializerSupplier = requireNonNull(channelInitializerSupplier);
        this.tcpServerChannel = tcpServerChannel;
    }

    /**
     */
    public TcpServer(final Function<JavaDrasylServerChannel, ChannelInitializer<SocketChannel>> channelInitializerSupplier) {
        this(
                channelInitializerSupplier, null, new ConcurrentHashMap<>()
        );
    }

    public TcpServer() {
        this(TcpServerChannelInitializer::new);
    }

    @SuppressWarnings("java:S1905")
    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws TcpServerBindFailedException {
        LOG.debug("Start Server...");

        config(ctx).getTcpServerBootstrap().get()
                .group(config(ctx).getTcpServerEventLoopGroup().get())
                .channel(config(ctx).getTcpServerChannelClass())
                .childHandler(channelInitializerSupplier.apply((JavaDrasylServerChannel) ctx.channel()))
                .bind(config(ctx).getTcpServerBind())
                .addListener(new TcpServerBindListener((JavaDrasylServerChannel) ctx.channel()));

        ctx.fireChannelActive();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof RemoteMessage) {
            final SocketAddress recipient = ((InetAddressedMessage<RemoteMessage>) msg).recipient();

            // check if we can route the message via a tcp connection
            final SocketChannel clientChannel = tcpClientChannels.get(recipient);
            if (clientChannel != null) {
                final TcpServerToDrasylHandler tcpDrasylHandler = clientChannel.pipeline().get(TcpServerToDrasylHandler.class);
                tcpDrasylHandler.enqueueWrite(msg);
                return;
            }
        }

        ctx.write(msg, promise);
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) throws Exception {
        for (final SocketChannel clientChannel : tcpClientChannels.values()) {
            if (clientChannel.isOpen()) {
                final TcpServerToDrasylHandler tcpDrasylHandler = clientChannel.pipeline().get(TcpServerToDrasylHandler.class);
                if (tcpDrasylHandler != null) {
                    tcpDrasylHandler.finishWrite();
                }
            }
        }

        ctx.flush();
    }

    protected static JavaDrasylServerChannelConfig config(final ChannelHandlerContext ctx) {
        return (JavaDrasylServerChannelConfig) ctx.channel().config();
    }

    /**
     * Listener that gets called once the channel is bound.
     */
    private class TcpServerBindListener implements ChannelFutureListener {
        private final JavaDrasylServerChannel parent;

        TcpServerBindListener(final JavaDrasylServerChannel parent) {
            this.parent = requireNonNull(parent);
        }

        @Override
        public void operationComplete(final ChannelFuture future) {
            if (future.isSuccess()) {
                // server successfully started
                final ServerSocketChannel channel = (ServerSocketChannel) future.channel();
                final InetSocketAddress socketAddress = channel.localAddress();
                LOG.debug("Server started and bound to tcp:/{}.", socketAddress);

                TcpServer.this.tcpServerChannel = channel;
                parent.pipeline().fireUserEventTriggered(new TcpServerBound(socketAddress));

                channel.closeFuture().addListener(new TcpServerCloseListener());
                parent.closeFuture().addListener(new DrasylServerChannelCloseListener(channel));
            }
            else {
                // server start failed
                parent.pipeline().fireExceptionCaught(new TcpServerBindFailedException("Unable to bind server to address tcp://" + future.channel().localAddress(), future.cause()));
            }
        }
    }

    /**
     * Signals that the {@link TcpServer} was unable to bind to given address.
     */
    public static class TcpServerBindFailedException extends Exception {
        public TcpServerBindFailedException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Signals that the {@link TcpServer} is bound to {@link TcpServerBound#getBindAddress()}.
     */
    public static class TcpServerBound {
        private final InetSocketAddress bindAddress;

        public TcpServerBound(final InetSocketAddress bindAddress) {
            this.bindAddress = requireNonNull(bindAddress);
        }

        public InetSocketAddress getBindAddress() {
            return bindAddress;
        }
    }

    /**
     * Listener that gets called once the channel is closed.
     */
    private static class TcpServerCloseListener implements ChannelFutureListener {
        @Override
        public void operationComplete(final ChannelFuture future) {
            LOG.debug("Server bound to tcp:/{} stopped.", future.channel().localAddress());
        }
    }

    /**
     * Listener that gets called once DrasylServerChannel is closed.
     */
    private static class DrasylServerChannelCloseListener implements ChannelFutureListener {
        private final ServerSocketChannel tcpChannel;

        public DrasylServerChannelCloseListener(final ServerSocketChannel tcpChannel) {
            this.tcpChannel = Objects.requireNonNull(tcpChannel);
        }

        @Override
        public void operationComplete(final ChannelFuture future) {
            if (tcpChannel.isOpen()) {
                LOG.debug("Stop server bound to udp:/{}...", tcpChannel.localAddress());
                tcpChannel.close();
            }
        }
    }
}
