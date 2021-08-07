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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.channel.MigrationEvent;
import org.drasyl.channel.MigrationInboundMessage;
import org.drasyl.channel.MigrationOutboundMessage;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.pipeline.Skip;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.skeleton.HandlerAdapter;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.network.NetworkUtil;

import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;
import static org.drasyl.channel.Null.NULL;

/**
 * Starts an UDP server which joins a multicast group and together with the {@link
 * LocalNetworkDiscovery} is responsible for discovering other nodes in the local network.
 *
 * @see LocalNetworkDiscovery
 */
@SuppressWarnings({ "java:S112", "java:S2974" })
public class UdpMulticastServer extends HandlerAdapter {
    private static final String MULTICAST_INTERFACE_PROPERTY = "org.drasyl.remote.multicast.interface";
    private static final Logger LOG = LoggerFactory.getLogger(UdpMulticastServer.class);
    public static final InetSocketAddressWrapper MULTICAST_ADDRESS;
    public static final NetworkInterface MULTICAST_INTERFACE;
    private static final String MULTICAST_BIND_HOST;
    private static UdpMulticastServer instance;
    private final Map<IdentityPublicKey, ChannelHandlerContext> nodes;
    private final Bootstrap bootstrap;
    private DatagramChannel channel;

    static {
        try {
            final String stringValue = SystemPropertyUtil.get("org.drasyl.remote.multicast.address", "239.22.5.27:22527");
            final URI uriValue = new URI("my://" + stringValue);
            MULTICAST_ADDRESS = new InetSocketAddressWrapper(uriValue.getHost(), uriValue.getPort());
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

    UdpMulticastServer(final Map<IdentityPublicKey, ChannelHandlerContext> nodes,
                       final Bootstrap bootstrap, final DatagramChannel channel) {
        this.nodes = nodes;
        this.bootstrap = bootstrap;
        this.channel = channel;
    }

    private UdpMulticastServer() {
        this(
                new ConcurrentHashMap<>(),
                new Bootstrap().group(EventLoopGroupUtil.getInstanceNio()).channel(NioDatagramChannel.class),
                null
        );
    }

    @Skip
    public void onEvent(final ChannelHandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        if (event instanceof NodeUpEvent) {
            startServer(ctx);
        }
        else if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            stopServer(ctx);
        }

        // passthrough event
        ctx.fireUserEventTriggered(new MigrationEvent(event, future));
    }

    private synchronized void startServer(final ChannelHandlerContext ctx) {
        if (MULTICAST_INTERFACE == null) {
            LOG.warn("No default network interface could be identified. Therefore the server cannot be started. You can manually specify an interface by using the Java System Property `{}`.", () -> MULTICAST_INTERFACE_PROPERTY);
            return;
        }

        nodes.put(ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey(), ctx);

        if (channel == null) {
            LOG.debug("Start Server...");
            final ChannelFuture channelFuture = bootstrap
                    .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                        @Override
                        protected void channelRead0(final ChannelHandlerContext channelCtx,
                                                    final DatagramPacket packet) {
                            final InetSocketAddressWrapper sender = new InetSocketAddressWrapper(packet.sender());
                            nodes.values().forEach(nodeCtx -> {
                                LOG.trace("Datagram received {} and passed to {}", () -> packet, nodeCtx.attr(IDENTITY_ATTR_KEY).get()::getIdentityPublicKey);
                                nodeCtx.fireChannelRead(new MigrationInboundMessage<>((Object) packet.content().retain(), (Address) sender, new CompletableFuture<Void>()));
                            });
                        }
                    })
                    .bind(MULTICAST_BIND_HOST, MULTICAST_ADDRESS.getPort());
            channelFuture.awaitUninterruptibly();

            if (channelFuture.isSuccess()) {
                // server successfully started
                final DatagramChannel myChannel = (DatagramChannel) channelFuture.channel();
                LOG.info("Server started and listening at {}", myChannel.localAddress());

                // join multicast group
                LOG.debug("Join multicast group `{}` at network interface `{}`...", () -> MULTICAST_ADDRESS, MULTICAST_INTERFACE::getName);
                final ChannelFuture multicastFuture = myChannel.joinGroup(MULTICAST_ADDRESS, MULTICAST_INTERFACE).awaitUninterruptibly();

                if (multicastFuture.isSuccess()) {
                    // join succeeded
                    LOG.info("Successfully joined multicast group `{}` at network interface `{}`", () -> MULTICAST_ADDRESS, MULTICAST_INTERFACE::getName);
                    this.channel = myChannel;
                }
                else {
                    // join failed
                    //noinspection unchecked
                    LOG.warn("Unable to join multicast group `{}` at network interface `{}`:", () -> MULTICAST_ADDRESS, MULTICAST_INTERFACE::getName, multicastFuture.cause()::getMessage);
                }
            }
            else {
                // server start failed
                //noinspection unchecked
                LOG.warn("Unable to bind server to address {}:{}: {}", () -> MULTICAST_BIND_HOST, MULTICAST_ADDRESS::getPort, channelFuture.cause()::getMessage);
            }
        }
    }

    private synchronized void stopServer(final ChannelHandlerContext ctx) {
        nodes.remove(ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey());

        if (channel != null) {
            final InetSocketAddress socketAddress = channel.localAddress();
            LOG.debug("Stop Server listening at {}...", socketAddress);
            // leave multicast group
            channel.leaveGroup(MULTICAST_ADDRESS, MULTICAST_INTERFACE).awaitUninterruptibly();
            // shutdown server
            channel.close().awaitUninterruptibly();
            channel = null;
            LOG.debug("Server stopped");
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof MigrationInboundMessage) {
            final MigrationInboundMessage<?, ?> migrationMsg = (MigrationInboundMessage<?, ?>) msg;
            final Object payload = migrationMsg.message() == NULL ? null : migrationMsg.message();
            try {
                onInbound(ctx, migrationMsg.address(), payload, migrationMsg.future());
            }
            catch (final Exception e) {
                migrationMsg.future().completeExceptionally(e);
                ctx.fireExceptionCaught(e);
                ReferenceCountUtil.safeRelease(migrationMsg.message());
            }
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        onAdded(ctx);
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        onRemoved(ctx);
    }

    @Skip
    public void onException(final ChannelHandlerContext ctx, final Exception cause) {
        ctx.fireExceptionCaught(cause);
    }

    @SuppressWarnings("java:S112")
    @Skip
    public void onInbound(final ChannelHandlerContext ctx,
                          final Address sender,
                          final Object msg,
                          final CompletableFuture<Void> future) throws Exception {
        ctx.fireChannelRead(new MigrationInboundMessage<>(msg, sender, future));
    }

    @SuppressWarnings("java:S112")
    @Skip
    public void onOutbound(final ChannelHandlerContext ctx,
                           final Address recipient,
                           final Object msg,
                           final CompletableFuture<Void> future) throws Exception {
        FutureUtil.combine(ctx.writeAndFlush(new MigrationOutboundMessage<>(msg, recipient)), future);
    }

    public static synchronized UdpMulticastServer getInstance() {
        if (instance == null) {
            instance = new UdpMulticastServer();
        }
        return instance;
    }
}
