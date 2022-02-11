/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.NetUtil;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Starts an UDP broadcast server and together with the {@link LocalNetworkDiscovery} is responsible
 * for discovering other nodes in the local network.
 *
 * @see LocalNetworkDiscovery
 */
@Sharable
public class UdpBroadcastServer extends ChannelInboundHandlerAdapter {
    private static final String BROADCAST_ADDRESS_PROPERTY = "org.drasyl.remote.broadcast.address";
    private static final String BROADCAST_BIND_HOST_PROPERTY = "org.drasyl.remote.broadcast.bind-host";
    private static final Logger LOG = LoggerFactory.getLogger(UdpBroadcastServer.class);
    public static final InetSocketAddress BROADCAST_ADDRESS;
    private static final String BROADCAST_BIND_HOST;
    private final Set<ChannelHandlerContext> nodes;
    private final Supplier<Bootstrap> bootstrapSupplier;
    private DatagramChannel channel;

    static {
        try {
            final String stringValue = SystemPropertyUtil.get(BROADCAST_ADDRESS_PROPERTY, "255.255.255.255:22527");
            final URI uriValue = new URI("my://" + stringValue);
            BROADCAST_ADDRESS = new InetSocketAddress(uriValue.getHost(), uriValue.getPort());
        }
        catch (final URISyntaxException | IllegalArgumentException e) {
            throw new RuntimeException("Invalid broadcast address given:", e);
        }

        BROADCAST_BIND_HOST = SystemPropertyUtil.get(BROADCAST_BIND_HOST_PROPERTY, "0.0.0.0");
    }

    @SuppressWarnings("java:S2384")
    UdpBroadcastServer(final Set<ChannelHandlerContext> nodes,
                       final Supplier<Bootstrap> bootstrapSupplier,
                       final DatagramChannel channel) {
        this.nodes = requireNonNull(nodes);
        this.bootstrapSupplier = requireNonNull(bootstrapSupplier);
        this.channel = channel;
    }

    public UdpBroadcastServer() {
        this(
                new HashSet<>(),
                Bootstrap::new,
                null
        );
    }

    @SuppressWarnings("java:S1905")
    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        if (NetUtil.isIpV6AddressesPreferred()) {
            LOG.debug("Do not start Broadcast Server as we're in an IPv6 preferred environment.");
            ctx.fireChannelActive();
            return;
        }

        nodes.add(ctx);

        if (channel == null) {
            LOG.debug("Start Broadcast Server...");
            bootstrapSupplier.get()
                    .group((EventLoopGroup) ctx.executor().parent())
                    .channel(NioDatagramChannel.class)
                    .handler(new UdpBroadcastServerHandler())
                    .bind(BROADCAST_BIND_HOST, BROADCAST_ADDRESS.getPort())
                    .addListener(new UdpBroadcastServerFutureListener(ctx));
        }
        else {
            ctx.fireChannelActive();
        }
    }

    @SuppressWarnings("java:S1602")
    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        ctx.fireChannelInactive();

        nodes.remove(ctx);

        if (channel != null && nodes.isEmpty()) {
            final InetSocketAddress socketAddress = channel.localAddress();
            LOG.debug("Stop Server listening at udp:/{}...", socketAddress);
            // shutdown server
            channel.close().addListener(future1 -> {
                channel = null;
                LOG.debug("Server stopped.");
            });
        }
    }

    private class UdpBroadcastServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        public UdpBroadcastServerHandler() {
            super(false);
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext channelCtx,
                                    final DatagramPacket packet) {
            final InetSocketAddress sender = packet.sender();
            final ByteBuf content = packet.content().asReadOnly();
            nodes.forEach(nodeCtx -> {
                LOG.trace("Datagram received {} and passed to {}", () -> packet, nodeCtx.channel()::localAddress);
                final ByteBuf byteBuf = content.retainedDuplicate();
                nodeCtx.executor().execute(() -> {
                    nodeCtx.fireChannelRead(new InetAddressedMessage<>(byteBuf, null, sender));
                    nodeCtx.fireChannelReadComplete();
                });
            });
            content.release();
        }
    }

    private class UdpBroadcastServerFutureListener implements ChannelFutureListener {
        private final ChannelHandlerContext ctx;

        public UdpBroadcastServerFutureListener(final ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void operationComplete(final ChannelFuture future) {
            if (future.isSuccess()) {
                // server successfully started
                final DatagramChannel myChannel = (DatagramChannel) future.channel();
                LOG.info("Server started and listening at udp:/{}", myChannel.localAddress());
                UdpBroadcastServer.this.channel = myChannel;
                ctx.fireChannelActive();
            }
            else {
                // server start failed
                //noinspection unchecked
                LOG.info("Unable to bind server to address udp://{}:{}. This can be caused by another drasyl node running in a different JVM or another application is bind to that port.", () -> BROADCAST_BIND_HOST, BROADCAST_ADDRESS::getPort, future.cause()::getMessage);
                ctx.fireChannelActive();
            }
        }
    }
}
