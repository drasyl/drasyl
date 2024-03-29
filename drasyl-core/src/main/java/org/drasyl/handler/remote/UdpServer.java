/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.PendingWriteQueue;
import io.netty.channel.socket.DatagramChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.PromiseNotifier;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Binds to an udp port, sends outgoing messages via udp, and sends received udp packets to the
 * {@link Channel}.
 */
@UnstableApi
public class UdpServer extends ChannelDuplexHandler {
    /*
     * On MacOS -Djava.net.preferIPv4Stack=true must be set to work.
     */
    public static final int IP_TOS = Integer.decode(System.getProperty("ipTos", "0x0")); // real-time 0xB8
    private static final Logger LOG = LoggerFactory.getLogger(UdpServer.class);
    private static final boolean SO_REUSEADDR = Boolean.getBoolean(System.getProperty("reuseAddress", "false"));
    private final Bootstrap bootstrap;
    private final EventLoopGroup group;
    private final InetSocketAddress bindAddress;
    private final Function<ChannelHandlerContext, ChannelInitializer<DatagramChannel>> channelInitializerSupplier;
    private PendingWriteQueue pendingWrites;
    private Channel channel;

    UdpServer(final Bootstrap bootstrap,
              final EventLoopGroup group,
              final InetSocketAddress bindAddress,
              final Function<ChannelHandlerContext, ChannelInitializer<DatagramChannel>> channelInitializerSupplier,
              final PendingWriteQueue pendingWrites,
              final Channel channel) {
        this.bootstrap = requireNonNull(bootstrap);
        this.group = requireNonNull(group);
        this.bindAddress = requireNonNull(bindAddress);
        this.channelInitializerSupplier = requireNonNull(channelInitializerSupplier);
        this.pendingWrites = pendingWrites;
        this.channel = channel;
    }

    public UdpServer(final Bootstrap bootstrap,
                     final EventLoopGroup group,
                     final InetSocketAddress bindAddress,
                     final Function<ChannelHandlerContext, ChannelInitializer<DatagramChannel>> channelInitializerSupplier) {
        this(
                bootstrap,
                group,
                bindAddress,
                channelInitializerSupplier,
                null,
                null
        );
    }

    public UdpServer(final Bootstrap bootstrap,
                     final EventLoopGroup group,
                     final InetSocketAddress bindAddress) {
        this(bootstrap, group, bindAddress, UdpServerChannelInitializer::new);
    }

    /**
     * @param group       the {@link EventLoopGroup} the underlying udp server should run on
     * @param bindAddress the address the UDP server will bind to
     */
    public UdpServer(final EventLoopGroup group,
                     final InetSocketAddress bindAddress,
                     final Function<ChannelHandlerContext, ChannelInitializer<DatagramChannel>> channelInitializerSupplier) {
        this(new Bootstrap().option(ChannelOption.SO_BROADCAST, false).option(ChannelOption.SO_REUSEADDR, SO_REUSEADDR).option(ChannelOption.IP_TOS, 0xB8), group, bindAddress, channelInitializerSupplier);
    }

    /**
     * @param group       the {@link EventLoopGroup} the underlying udp server should run on
     * @param bindAddress the address the UDP server will bind to
     */
    public UdpServer(final EventLoopGroup group,
                     final InetSocketAddress bindAddress) {
        this(new Bootstrap().option(ChannelOption.SO_BROADCAST, false).option(ChannelOption.SO_REUSEADDR, SO_REUSEADDR).option(ChannelOption.IP_TOS, 0xB8), group, bindAddress);
    }

    /**
     * @param group    the {@link EventLoopGroup} the underlying udp server should run on
     * @param bindHost the host the UDP server will bind to
     * @param bindPort the port the UDP server will bind to
     */
    public UdpServer(final EventLoopGroup group,
                     final InetAddress bindHost,
                     final int bindPort,
                     final Function<ChannelHandlerContext, ChannelInitializer<DatagramChannel>> channelInitializerSupplier) {
        this(group, new InetSocketAddress(bindHost, bindPort), channelInitializerSupplier);
    }

    /**
     * @param group    the {@link EventLoopGroup} the underlying udp server should run on
     * @param bindHost the host the UDP server will bind to
     * @param bindPort the port the UDP server will bind to
     */
    public UdpServer(final EventLoopGroup group,
                     final InetAddress bindHost,
                     final int bindPort) {
        this(group, new InetSocketAddress(bindHost, bindPort));
    }

    /**
     * @param group    the {@link EventLoopGroup} the underlying udp server should run on
     * @param bindHost the host the UDP server will bind to
     * @param bindPort the port the UDP server will bind to
     */
    public UdpServer(final EventLoopGroup group,
                     final String bindHost,
                     final int bindPort) {
        this(group, new InetSocketAddress(bindHost, bindPort));
    }

    /**
     * Create UDP server that will bind to host {@code 0.0.0.0} and port {@code bindPort}.
     *
     * @param group    the {@link EventLoopGroup} the underlying udp server should run on
     * @param bindPort the port the UDP server will bind to
     */
    public UdpServer(final EventLoopGroup group,
                     final int bindPort) {
        this(group, new InetSocketAddress(bindPort));
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.pendingWrites = new PendingWriteQueue(ctx);
    }

    @SuppressWarnings("java:S1905")
    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws UdpServerBindFailedException {
        LOG.debug("Start Server...");
        bootstrap
                .group(group)
                .channel(EventLoopGroupUtil.getBestDatagramChannel())
                .handler(channelInitializerSupplier.apply(ctx))
                .bind(bindAddress)
                .addListener(new UdpServerBindListener(ctx));
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        ctx.fireChannelInactive();

        if (channel != null) {
            final SocketAddress socketAddress = channel.localAddress();
            LOG.debug("Stop Server listening at udp:/{}...", socketAddress);
            // shutdown server
            channel.close();
            channel = null;
        }
        pendingWrites.removeAndFailAll(new ClosedChannelException());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof RemoteMessage) {
            if (channel.isWritable()) {
                LOG.trace("Write Datagram {}", msg);
                channel.write(msg).addListener(new PromiseNotifier<>(promise));
            }
            else {
                pendingWrites.add(msg, promise);
            }
        }
        else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) throws Exception {
        channel.flush();
        ctx.flush();
    }

    /**
     * ensure this method is called by same ctx (thread) from which PendingWrites was created.
     */
    void writePendingWrites(final ChannelHandlerContext ctx) {
        if (!ctx.executor().inEventLoop()) {
            // make sure this method is always called from the channel's thread.
            ctx.executor().execute(() -> writePendingWrites(ctx));
            return;
        }

        // pass all pending writes to the UDP channel while it writable
        while (channel != null && channel.isWritable()) {
            final Object currentWrite = pendingWrites.current();

            if (currentWrite == null) {
                break;
            }

            // make sure PendingWriteQueue#remove() will not release currentWrite
            ReferenceCountUtil.retain(currentWrite);
            final ChannelPromise promise = pendingWrites.remove();

            LOG.trace("Write Datagram {}", currentWrite);
            channel.writeAndFlush(currentWrite).addListener(new PromiseNotifier<>(promise));
        }
    }

    /**
     * Listener that gets called once the channel is closed.
     */
    private static class UdpServerCloseListener implements ChannelFutureListener {
        @Override
        public void operationComplete(final ChannelFuture future) {
            final InetSocketAddress socketAddress = (InetSocketAddress) future.channel().localAddress();
            LOG.debug("Server listening at udp:/{} stopped.", socketAddress);
        }
    }

    /**
     * Signals that the {@link UdpServer} is bound to {@link UdpServerBound#getBindAddress()}.
     */
    public static class UdpServerBound {
        private final InetSocketAddress bindAddress;

        public UdpServerBound(final InetSocketAddress bindAddress) {
            this.bindAddress = requireNonNull(bindAddress);
        }

        public InetSocketAddress getBindAddress() {
            return bindAddress;
        }
    }

    /**
     * Signals that the {@link UdpServer} was unable to bind to given address.
     */
    public static class UdpServerBindFailedException extends Exception {
        public UdpServerBindFailedException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Listener that gets called once the channel is bound.
     */
    private class UdpServerBindListener implements ChannelFutureListener {
        private final ChannelHandlerContext ctx;

        UdpServerBindListener(final ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void operationComplete(final ChannelFuture future) {
            if (future.isSuccess()) {
                // server successfully started
                final Channel myChannel = future.channel();
                myChannel.closeFuture().addListener(new UdpServerCloseListener());
                final InetSocketAddress socketAddress = (InetSocketAddress) myChannel.localAddress();
                LOG.info("Server started and listening at udp:/{}.", socketAddress);

                UdpServer.this.channel = myChannel;
                ctx.fireUserEventTriggered(new UdpServerBound(socketAddress));
                ctx.fireChannelActive();
            }
            else {
                // server start failed
                ctx.fireExceptionCaught(new UdpServerBindFailedException("Unable to bind server to address udp:/" + bindAddress, future.cause()));
            }
        }
    }
}
