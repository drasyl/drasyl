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

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
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
    private static final Logger LOG = LoggerFactory.getLogger(UdpServer.class);
    private final Function<DrasylServerChannel, ChannelInitializer<DatagramChannel>> channelInitializerSupplier;
    private DatagramChannel udpChannel;
    private UdpServerToDrasylHandler udpDrasylHandler;

    UdpServer(final Function<DrasylServerChannel, ChannelInitializer<DatagramChannel>> channelInitializerSupplier,
              final DatagramChannel udpChannel) {
        this.channelInitializerSupplier = requireNonNull(channelInitializerSupplier);
        this.udpChannel = udpChannel;
    }

    public UdpServer(final Function<DrasylServerChannel, ChannelInitializer<DatagramChannel>> channelInitializerSupplier) {
        this(channelInitializerSupplier, null);
    }

    public UdpServer() {
        this(UdpServerChannelInitializer::new);
    }

    @SuppressWarnings("java:S1905")
    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws UdpServerBindFailedException {
        LOG.debug("Start Server...");

        config(ctx).getUdpBootstrap()
                .group(config(ctx).getUdpEventLoopSupplier().get())
                .channel(config(ctx).getUdpChannelClass())
                .handler(channelInitializerSupplier.apply((DrasylServerChannel) ctx.channel()))
                .bind(config(ctx).getUdpBind())
                .addListener(new UdpServerBindListener((DrasylServerChannel) ctx.channel()));
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        ctx.fireChannelInactive();

        if (udpChannel != null) {
            final SocketAddress socketAddress = udpChannel.localAddress();
            LOG.debug("Stop Server listening at udp:/{}...", socketAddress);
            // shutdown server
            udpChannel.close();
            udpChannel = null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof RemoteMessage) {
            outboundUdpBufferHolder().enqueueWrite(msg);
        }
        else {
            ctx.write(msg, promise);
        }
    }

    private UdpServerToDrasylHandler outboundUdpBufferHolder() {
        if (udpDrasylHandler == null) {
            udpDrasylHandler = udpChannel.pipeline().get(UdpServerToDrasylHandler.class);
        }
        return udpDrasylHandler;
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) throws Exception {
        final UdpServerToDrasylHandler outboundBufferHolder = outboundUdpBufferHolder();
        outboundBufferHolder.finishWrite();

        ctx.flush();
    }

    private static DrasylServerChannelConfig config(final ChannelHandlerContext ctx) {
        return (DrasylServerChannelConfig) ctx.channel().config();
    }

    public DatagramChannel udpChannel() {
        return udpChannel;
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
        private final DrasylServerChannel parent;

        UdpServerBindListener(final DrasylServerChannel parent) {
            this.parent = requireNonNull(parent);
        }

        @Override
        public void operationComplete(final ChannelFuture future) {
            if (future.isSuccess()) {
                // server successfully started
                final Channel myChannel = future.channel();
                myChannel.closeFuture().addListener(new UdpServerCloseListener());
                final InetSocketAddress socketAddress = (InetSocketAddress) myChannel.localAddress();
                LOG.info("Server started and listening at udp:/{}.", socketAddress);

                UdpServer.this.udpChannel = (DatagramChannel) myChannel;
                parent.pipeline().fireUserEventTriggered(new UdpServerBound(socketAddress));
                parent.pipeline().context(UdpServer.class).fireChannelActive();
            }
            else {
                // server start failed
                parent.pipeline().fireExceptionCaught(new UdpServerBindFailedException("Unable to bind server to address udp:/" + future.channel().localAddress(), future.cause()));
            }
        }
    }
}
