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
package org.drasyl.remote.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.PendingWriteQueue;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.event.Event;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.NettyUtil.getBestDatagramChannel;
import static org.drasyl.util.Preconditions.requireNonNegative;

/**
 * Binds to an udp port, sends outgoing messages via udp, and sends received udp packets to the
 * {@link Channel}.
 */
public class UdpServer extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(UdpServer.class);
    private final Bootstrap bootstrap;
    private final InetAddress bindHost;
    private final int bindPort;
    private PendingWriteQueue pendingWrites;
    private Channel channel;

    UdpServer(final Bootstrap bootstrap,
              final InetAddress bindHost,
              final int bindPort,
              final PendingWriteQueue pendingWrites,
              final Channel channel) {
        this.bootstrap = requireNonNull(bootstrap);
        this.bindHost = bindHost;
        this.bindPort = bindPort;
        this.pendingWrites = pendingWrites;
        this.channel = channel;
    }

    public UdpServer(final InetAddress bindHost,
                     final int bindPort) {
        this(
                new Bootstrap()
                        .group(EventLoopGroupUtil.getInstanceBest())
                        .channel(getBestDatagramChannel())
                        .option(ChannelOption.SO_BROADCAST, false),
                bindHost,
                bindPort,
                null,
                null
        );
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.pendingWrites = new PendingWriteQueue(ctx);
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
        pendingWrites.removeAndFailAll(new ClosedChannelException());
    }

    private void startServer(final ChannelHandlerContext ctx) throws BindFailedException {
        LOG.debug("Start Server...");
        final ChannelFuture channelFuture = bootstrap
                .handler(new SimpleChannelInboundHandler<DatagramPacket>(false) {
                    @Override
                    public void channelWritabilityChanged(final ChannelHandlerContext channelCtx) {
                        channelCtx.fireChannelWritabilityChanged();

                        if (channelCtx.channel().isWritable()) {
                            // UDP channel is writable again. Make sure (any existing) pending writes will be written
                            ctx.executor().submit(UdpServer.this::writePendingWrites);
                        }
                    }

                    @Override
                    protected void channelRead0(final ChannelHandlerContext channelCtx,
                                                final DatagramPacket packet) {
                        LOG.trace("Datagram received {}", packet);
                        ctx.fireChannelRead(new AddressedMessage<>(packet.content(), packet.sender()));
                    }
                })
                .bind(bindHost, this.bindPort);
        channelFuture.awaitUninterruptibly();

        if (channelFuture.isSuccess()) {
            // server successfully started
            this.channel = channelFuture.channel();
            final InetSocketAddress socketAddress = (InetSocketAddress) channel.localAddress();
            LOG.info("Server started and listening at udp:/{}", socketAddress);

            // consume NodeUpEvent and publish NodeUpEvent with port
            ctx.fireUserEventTriggered(new Port(socketAddress.getPort()));
        }
        else {
            // server start failed
            throw new BindFailedException("Unable to bind server to address udp://" + bindHost + ":" + this.bindPort, channelFuture.cause());
        }
    }

    private void stopServer() {
        if (channel != null) {
            final SocketAddress socketAddress = channel.localAddress();
            LOG.debug("Stop Server listening at udp:/{}...", socketAddress);
            // shutdown server
            channel.close().awaitUninterruptibly();
            channel = null;
            LOG.debug("Server stopped");
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof ByteBuf && ((AddressedMessage<?, ?>) msg).address() instanceof InetSocketAddress) {
            final ByteBuf byteBufMsg = (ByteBuf) ((AddressedMessage<?, ?>) msg).message();
            final InetSocketAddress recipient = (InetSocketAddress) ((AddressedMessage<?, ?>) msg).address();
            final DatagramPacket packet = new DatagramPacket(byteBufMsg, recipient);

            pendingWrites.add(packet, promise);
        }
        else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) throws Exception {
        writePendingWrites();
        ctx.flush();
    }

    private void writePendingWrites() {
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
            channel.writeAndFlush(currentWrite).addListener(future -> {
                if (future.isSuccess()) {
                    promise.setSuccess();
                }
                else {
                    promise.setFailure(future.cause());
                }
            });
        }
    }

    /**
     * Signals that the {@link UdpServer} is bind to {@link Port#getPort()}.
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
     * Signals that the {@link UdpServer} was unable to bind to port.
     */
    public static class BindFailedException extends Exception {
        public BindFailedException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
