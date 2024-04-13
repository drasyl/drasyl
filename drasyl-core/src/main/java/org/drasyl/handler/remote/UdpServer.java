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
import io.netty.channel.socket.DatagramChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
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
    private EventLoopGroup group;
    private InetSocketAddress bindAddress;
    private final Function<ChannelHandlerContext, ChannelInitializer<DatagramChannel>> channelInitializerSupplier;
    private Channel channel;

    UdpServer(final Bootstrap bootstrap,
              final EventLoopGroup group,
              final InetSocketAddress bindAddress,
              final Function<ChannelHandlerContext, ChannelInitializer<DatagramChannel>> channelInitializerSupplier,
              final Channel channel) {
        this.bootstrap = requireNonNull(bootstrap);
        this.group = group;
        this.bindAddress = bindAddress;
        this.channelInitializerSupplier = requireNonNull(channelInitializerSupplier);
        this.channel = channel;
    }

    public UdpServer(final Bootstrap bootstrap,
                     final InetSocketAddress bindAddress,
                     final Function<ChannelHandlerContext, ChannelInitializer<DatagramChannel>> channelInitializerSupplier) {
        this(
                bootstrap,
                null,
                bindAddress,
                channelInitializerSupplier,
                null
        );
    }

    public UdpServer(final Bootstrap bootstrap) {
        this(bootstrap, null, ctx -> new UdpServerChannelInitializer((DrasylServerChannel) ctx.channel().parent()));
    }

    /**
     */
    public UdpServer(final Function<ChannelHandlerContext, ChannelInitializer<DatagramChannel>> channelInitializerSupplier) {
        this(new Bootstrap().option(ChannelOption.SO_BROADCAST, false).option(ChannelOption.SO_REUSEADDR, SO_REUSEADDR).option(ChannelOption.IP_TOS, 0xB8), null, channelInitializerSupplier);
    }

    /**
     */
    public UdpServer() {
        this(new Bootstrap().option(ChannelOption.SO_BROADCAST, false).option(ChannelOption.SO_REUSEADDR, SO_REUSEADDR).option(ChannelOption.IP_TOS, 0xB8));
    }

    @SuppressWarnings("java:S1905")
    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws UdpServerBindFailedException {
        LOG.debug("Start Server...");
        if (bindAddress == null) {
            DrasylServerChannelConfig drasylServerChannelConfig = ((DrasylServerChannelConfig) ctx.channel().config());
            bindAddress = drasylServerChannelConfig.getUdpBind();
        }
        if (group == null) {
            group = ((DrasylServerChannelConfig) ctx.channel().config()).getUdpEventLoopSupplier().get();
        }

        bootstrap
                .group(group)
                .channel(EventLoopGroupUtil.getBestDatagramChannel()) // TODO: check if compatible with group
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
    }

    @SuppressWarnings("unchecked")
    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof RemoteMessage) {
            final UdpServerToDrasylHandler handler = channel.pipeline().get(UdpServerToDrasylHandler.class);
            handler.outboundBuffer().add(msg);
        }
        else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) throws Exception {
        final UdpServerToDrasylHandler handler = channel.pipeline().get(UdpServerToDrasylHandler.class);
        handler.finishWrite(channel.pipeline().context(UdpServerToDrasylHandler.class));

        ctx.flush();
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

                final DrasylServerChannel drasylServerChannel = (DrasylServerChannel) ctx.channel();
                drasylServerChannel.udpChannel = (DatagramChannel) future.channel();

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
