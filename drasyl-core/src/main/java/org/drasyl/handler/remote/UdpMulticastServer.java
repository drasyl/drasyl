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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.util.NetUtil;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.UnarmedProtocolMessage;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.network.NetworkUtil;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.netty.channel.socket.InternetProtocolFamily.IPv4;
import static io.netty.channel.socket.InternetProtocolFamily.IPv6;
import static java.util.Objects.requireNonNull;

/**
 * Starts an UDP server which joins a IP multicast group and together with the
 * {@link LocalNetworkDiscovery} is responsible for discovering other nodes in the local network.
 *
 * @see LocalNetworkDiscovery
 */
@Sharable
@SuppressWarnings({ "java:S112", "java:S2974" })
@UnstableApi
public class UdpMulticastServer extends ChannelInboundHandlerAdapter {
    private static final String MULTICAST_ADDRESS_PROPERTY = "org.drasyl.remote.multicast.address";
    private static final String MULTICAST_BIND_HOST_PROPERTY = "org.drasyl.remote.multicast.bind-host";
    private static final String MULTICAST_INTERFACE_PROPERTY = "org.drasyl.remote.multicast.interface";
    private static final Logger LOG = LoggerFactory.getLogger(UdpMulticastServer.class);
    public static final InetSocketAddress MULTICAST_ADDRESS;
    private static final NetworkInterface MULTICAST_INTERFACE;
    private static final String MULTICAST_BIND_HOST;
    private final Set<ChannelHandlerContext> nodes;
    private final Supplier<Bootstrap> bootstrapSupplier;
    private final EventLoopGroup group;
    private final Function<ChannelHandlerContext, ChannelInitializer<DatagramChannel>> channelInitializerSupplier;
    private DatagramChannel channel;

    static {
        try {
            final String stringValue = SystemPropertyUtil.get(MULTICAST_ADDRESS_PROPERTY, NetUtil.isIpV6AddressesPreferred() ? "[ff00::22:5:27]:22527" : "239.22.5.27:22527");
            final URI uriValue = new URI("my://" + stringValue);
            MULTICAST_ADDRESS = new InetSocketAddress(uriValue.getHost(), uriValue.getPort());
        }
        catch (final URISyntaxException | IllegalArgumentException e) {
            throw new RuntimeException("Invalid multicast address given:", e);
        }

        MULTICAST_BIND_HOST = SystemPropertyUtil.get(MULTICAST_BIND_HOST_PROPERTY, "0.0.0.0");

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

    /**
     * @param group                      the {@link EventLoopGroup} the underlying udp server should
     *                                   run on
     * @param channelInitializerSupplier
     */
    @SuppressWarnings("java:S2384")
    UdpMulticastServer(final Set<ChannelHandlerContext> nodes,
                       final Supplier<Bootstrap> bootstrapSupplier,
                       final EventLoopGroup group,
                       final Function<ChannelHandlerContext, ChannelInitializer<DatagramChannel>> channelInitializerSupplier,
                       final DatagramChannel channel) {
        this.nodes = requireNonNull(nodes);
        this.bootstrapSupplier = requireNonNull(bootstrapSupplier);
        this.group = requireNonNull(group);
        this.channelInitializerSupplier = requireNonNull(channelInitializerSupplier);
        this.channel = channel;
    }

    /**
     * @param group                      the {@link EventLoopGroup} the underlying udp server should
     *                                   run on
     * @param channelInitializerSupplier
     */
    UdpMulticastServer(final Set<ChannelHandlerContext> nodes,
                       final Supplier<Bootstrap> bootstrapSupplier,
                       final EventLoopGroup group,
                       final Function<ChannelHandlerContext, ChannelInitializer<DatagramChannel>> channelInitializerSupplier) {
        this(nodes, bootstrapSupplier, group, channelInitializerSupplier, null);
    }

    /**
     * @param group                      the {@link EventLoopGroup} the underlying udp server should
     *                                   run on
     * @param channelInitializerSupplier
     */
    public UdpMulticastServer(final EventLoopGroup group,
                              final Function<ChannelHandlerContext, ChannelInitializer<DatagramChannel>> channelInitializerSupplier) {
        this(
                new HashSet<>(),
                Bootstrap::new,
                group,
                channelInitializerSupplier);
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
                LOG.debug("Start Multicast Server to bind to udp://{}:{}...", () -> MULTICAST_BIND_HOST, MULTICAST_ADDRESS::getPort);
                bootstrapSupplier.get()
                        .group(group)
                        .channelFactory(() -> EventLoopGroupUtil.getBestDatagramChannel(MULTICAST_ADDRESS.getAddress() instanceof Inet4Address ? IPv4 : IPv6))
                        .handler(channelInitializerSupplier.apply(ctx))
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

    void multicastRead(final InetAddressedMessage<UnarmedProtocolMessage> msg) {
        nodes.forEach(nodeCtx -> {
            LOG.trace("Datagram received {} and passed to {}", () -> msg, nodeCtx.channel()::localAddress);
            nodeCtx.fireChannelRead(new InetAddressedMessage<>(msg.content().asReadOnly().retain(), msg.recipient(), msg.sender()));
        });
        msg.release();
    }

    void multicastReadComplete() {
        nodes.forEach(ChannelHandlerContext::fireChannelReadComplete);
    }

    private class UdpMulticastServerFutureListener implements ChannelFutureListener {
        private final ChannelHandlerContext ctx;

        UdpMulticastServerFutureListener(final ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void operationComplete(final ChannelFuture future) {
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
                        LOG.warn("Unable to join multicast group `{}` at network interface `{}`. Therefore, local eager detection of other drasyl nodes running in the same network is not possible. However, these nodes can still be detected using super nodes:", () -> MULTICAST_ADDRESS, MULTICAST_INTERFACE::getName, multicastFuture::cause);
                    }
                    ctx.fireChannelActive();
                });
            }
            else {
                // server start failed
                //noinspection unchecked
                LOG.warn("Unable to bind server to address udp://{}:{}. This can be caused by another drasyl node running in a different JVM or another application is bind to that port. Therefore, local eager detection of other drasyl nodes running in the same network is not possible. However, these nodes can still be detected using super nodes.", () -> MULTICAST_BIND_HOST, MULTICAST_ADDRESS::getPort, future.cause()::getMessage);
                ctx.fireChannelActive();
            }
        }
    }
}
