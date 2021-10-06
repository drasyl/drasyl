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
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.network.NetworkUtil;

import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Starts an UDP server which joins a multicast group and together with the {@link
 * LocalNetworkDiscovery} is responsible for discovering other nodes in the local network.
 *
 * @see LocalNetworkDiscovery
 */
@Sharable
@SuppressWarnings({ "java:S112", "java:S2974" })
public class UdpMulticastServer extends ChannelInboundHandlerAdapter {
    public static final UdpMulticastServer INSTANCE = new UdpMulticastServer();
    private static final String MULTICAST_INTERFACE_PROPERTY = "org.drasyl.remote.multicast.interface";
    private static final Logger LOG = LoggerFactory.getLogger(UdpMulticastServer.class);
    public static final InetSocketAddress MULTICAST_ADDRESS;
    public static final NetworkInterface MULTICAST_INTERFACE;
    private static final String MULTICAST_BIND_HOST;
    private final Set<ChannelHandlerContext> nodes;
    private final Supplier<Bootstrap> bootstrapSupplier;
    private DatagramChannel channel;

    static {
        try {
            final String stringValue = SystemPropertyUtil.get("org.drasyl.remote.multicast.address", "239.22.5.27:22527");
            final URI uriValue = new URI("my://" + stringValue);
            MULTICAST_ADDRESS = new InetSocketAddress(uriValue.getHost(), uriValue.getPort());
        }
        catch (final URISyntaxException | IllegalArgumentException e) {
            throw new RuntimeException("Invalid multicast address given:", e);
        }

        MULTICAST_BIND_HOST = SystemPropertyUtil.get("org.drasyl.remote.multicast.bind-host", "0.0.0.0");

        final NetworkInterface multicastInterface;
        try {
            final String stringValue = SystemPropertyUtil.get(MULTICAST_INTERFACE_PROPERTY);
            if (stringValue != null) {
                multicastInterface = NetworkInterface.getByName(stringValue);
            }
            else {
                multicastInterface = NetworkUtil.getDefaultInterface();
            }
        }
        catch (final SocketException e) {
            throw new RuntimeException("I/O error occurred:", e);
        }
        MULTICAST_INTERFACE = multicastInterface;
    }

    @SuppressWarnings("java:S2384")
    UdpMulticastServer(final Set<ChannelHandlerContext> nodes,
                       final Supplier<Bootstrap> bootstrapSupplier,
                       final DatagramChannel channel) {
        this.nodes = requireNonNull(nodes);
        this.bootstrapSupplier = requireNonNull(bootstrapSupplier);
        this.channel = channel;
    }

    private UdpMulticastServer() {
        this(
                new HashSet<>(),
                Bootstrap::new,
                null
        );
    }

    @SuppressWarnings("java:S1905")
    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        if (MULTICAST_INTERFACE == null) {
            LOG.warn("No default network interface could be identified. Therefore the server cannot be started. You can manually specify an interface by using the Java System Property `{}`.", () -> MULTICAST_INTERFACE_PROPERTY);
            ctx.fireChannelActive();
        }
        else {
            nodes.add(ctx);

            if (channel == null) {
                LOG.debug("Start Multicast Server...");
                bootstrapSupplier.get()
                        .group((EventLoopGroup) ctx.executor().parent())
                        .channel(NioDatagramChannel.class)
                        .handler(new UdpMulticastServerHandler())
                        .bind(MULTICAST_BIND_HOST, MULTICAST_ADDRESS.getPort())
                        .addListener(new UdpMulticastServerFutureListener(ctx));
            }
            else {
                ctx.fireChannelActive();
            }
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
            // leave multicast group
            channel.leaveGroup(MULTICAST_ADDRESS, MULTICAST_INTERFACE).addListener(future -> {
                // shutdown server
                channel.close().addListener(future1 -> {
                    channel = null;
                    LOG.debug("Server stopped.");
                });
            });
        }
    }

    private class UdpMulticastServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        public UdpMulticastServerHandler() {
            super(false);
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext channelCtx,
                                    final DatagramPacket packet) {
            final SocketAddress sender = packet.sender();
            final ByteBuf content = packet.content();
            nodes.forEach(nodeCtx -> {
                LOG.trace("Datagram received {} and passed to {}", () -> packet, nodeCtx.channel()::localAddress);
                final ByteBuf byteBuf = content.retainedDuplicate();
                nodeCtx.executor().execute(() -> {
                    nodeCtx.fireChannelRead(new AddressedMessage<>(byteBuf, sender));
                    nodeCtx.fireChannelReadComplete();
                });
            });
            content.release();
        }
    }

    private class UdpMulticastServerFutureListener implements ChannelFutureListener {
        private final ChannelHandlerContext ctx;

        public UdpMulticastServerFutureListener(final ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void operationComplete(final ChannelFuture future) throws Exception {
            if (future.isSuccess()) {
                // server successfully started
                final DatagramChannel myChannel = (DatagramChannel) future.channel();
                LOG.info("Server started and listening at udp:/{}", myChannel.localAddress());

                // join multicast group
                LOG.debug("Join multicast group `{}` at network interface `{}`...", () -> MULTICAST_ADDRESS, MULTICAST_INTERFACE::getName);
                myChannel.joinGroup(MULTICAST_ADDRESS, MULTICAST_INTERFACE).addListener(multicastFuture -> {
                    if (multicastFuture.isSuccess()) {
                        // join succeeded
                        LOG.info("Successfully joined multicast group `{}` at network interface `{}`", () -> MULTICAST_ADDRESS, MULTICAST_INTERFACE::getName);
                        UdpMulticastServer.this.channel = myChannel;
                    }
                    else {
                        // join failed
                        //noinspection unchecked
                        LOG.warn("Unable to join multicast group `{}` at network interface `{}`:", () -> MULTICAST_ADDRESS, MULTICAST_INTERFACE::getName, multicastFuture.cause()::getMessage);
                    }
                    ctx.fireChannelActive();
                });
            }
            else {
                // server start failed
                //noinspection unchecked
                LOG.info("Unable to bind server to address udp://{}:{}. This can be caused by another drasyl node running in a different JVM or another application is bind to that port.", () -> MULTICAST_BIND_HOST, MULTICAST_ADDRESS::getPort, future.cause()::getMessage);
                ctx.fireChannelActive();
            }
        }
    }
}
