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

import com.google.common.hash.Hashing;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.peer.Endpoint;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.UnsignedInteger;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.DefaultDrasylServerChannel.CONFIG_ATTR_KEY;
import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;
import static org.drasyl.util.NettyUtil.getBestDatagramChannel;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.network.NetworkUtil.MAX_PORT_NUMBER;
import static org.drasyl.util.network.NetworkUtil.getAddresses;

/**
 * Binds to a udp port, sends outgoing messages via udp, and sends received udp packets to the
 * {@link org.drasyl.pipeline.Pipeline}.
 */
public class UdpServer extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(UdpServer.class);
    private static final short MIN_DERIVED_PORT = 22528;
    private final Bootstrap bootstrap;
    private Channel channel;

    UdpServer(final Bootstrap bootstrap,
              final Channel channel) {
        this.bootstrap = requireNonNull(bootstrap);
        this.channel = channel;
    }

    public UdpServer() {
        this(
                new Bootstrap()
                        .group(EventLoopGroupUtil.getInstanceBest())
                        .channel(getBestDatagramChannel())
                        .option(ChannelOption.SO_BROADCAST, false),
                null
        );
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        startServer(ctx);

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);

        stopServer();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        super.channelRead(ctx, msg);
    }

    static Set<Endpoint> determineActualEndpoints(final Identity identity,
                                                  final DrasylConfig config,
                                                  final InetSocketAddress listenAddress) {
        final Set<Endpoint> configEndpoints = config.getRemoteEndpoints();
        if (!configEndpoints.isEmpty()) {
            // read endpoints from config
            return configEndpoints.stream().map(endpoint -> {
                if (endpoint.getPort() == 0) {
                    return Endpoint.of(endpoint.getHost(), listenAddress.getPort(), identity.getIdentityPublicKey());
                }
                return endpoint;
            }).collect(Collectors.toSet());
        }

        final Set<InetAddress> addresses;
        if (listenAddress.getAddress().isAnyLocalAddress()) {
            // use all available addresses
            addresses = getAddresses();
        }
        else {
            // use given host
            addresses = Set.of(listenAddress.getAddress());
        }
        return addresses.stream()
                .map(address -> Endpoint.of(address.getHostAddress(), listenAddress.getPort(), identity.getIdentityPublicKey()))
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("UnstableApiUsage")
    private void startServer(final ChannelHandlerContext ctx) throws Exception {
        LOG.debug("Start Server...");
        final int bindPort;
        if (ctx.attr(CONFIG_ATTR_KEY).get().getRemoteBindPort() == -1) {
                /*
                 derive a port in the range between MIN_DERIVED_PORT and {MAX_PORT_NUMBER from its
                 own identity. this is done because we also expose this port via
                 UPnP-IGD/NAT-PMP/PCP and some NAT devices behave unexpectedly when multiple nodes
                 in the local network try to expose the same local port.
                 a completely random port would have the disadvantage that every time the node is
                 started it would use a new port and this would make discovery more difficult
                */
            final long identityHash = UnsignedInteger.of(Hashing.murmur3_32().hashBytes(ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().toByteArray()).asBytes()).getValue();
            bindPort = (int) (MIN_DERIVED_PORT + identityHash % (MAX_PORT_NUMBER - MIN_DERIVED_PORT));
        }
        else {
            bindPort = ctx.attr(CONFIG_ATTR_KEY).get().getRemoteBindPort();
        }
        final ChannelFuture channelFuture = bootstrap
                .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    protected void channelRead0(final ChannelHandlerContext channelCtx,
                                                final DatagramPacket packet) {
                        LOG.trace("Datagram received {}", packet);
                        ctx.fireChannelRead(new AddressedMessage<>(packet.content().retain(), packet.sender()));
                    }
                })
                .bind(ctx.attr(CONFIG_ATTR_KEY).get().getRemoteBindHost(), bindPort);
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
            throw new Exception("Unable to bind server to address udp://" + ctx.attr(CONFIG_ATTR_KEY).get().getRemoteBindHost() + ":" + bindPort, channelFuture.cause());
        }
    }

    private void stopServer() {
        final InetSocketAddress socketAddress = (InetSocketAddress) channel.localAddress();
        LOG.debug("Stop Server listening at udp:/{}...", socketAddress);
        // shutdown server
        channel.close().awaitUninterruptibly();
        channel = null;
        LOG.debug("Server stopped");
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) throws Exception {
        if (msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof ByteBuf && ((AddressedMessage<?, ?>) msg).address() instanceof InetSocketAddress) {
            final ByteBuf byteBufMsg = (ByteBuf) ((AddressedMessage<?, ?>) msg).message();
            final InetSocketAddress recipient = (InetSocketAddress) ((AddressedMessage<?, ?>) msg).address();

            if (channel != null && channel.isWritable()) {
                final DatagramPacket packet = new DatagramPacket(byteBufMsg, recipient);
                LOG.trace("Send Datagram {}", packet);
                channel.writeAndFlush(packet).addListener(future -> {
                    if (future.isSuccess()) {
                        promise.setSuccess();
                    }
                    else {
                        promise.setFailure(future.cause());
                    }
                });
            }
            else {
                ReferenceCountUtil.safeRelease(msg);
                promise.setFailure(new Exception("UDP channel is not present or is not writable."));
            }
        }
        else {
            super.write(ctx, msg, promise);
        }
    }

    public class Port implements Event {
        private final int port;

        public Port(final int port) {
            this.port = requireNonNegative(port, "port must be non-negative");
        }

        public int getPort() {
            return port;
        }
    }
}
