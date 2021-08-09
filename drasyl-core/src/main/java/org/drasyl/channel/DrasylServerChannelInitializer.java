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
package org.drasyl.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.intravm.IntraVmDiscovery;
import org.drasyl.localhost.LocalHostDiscovery;
import org.drasyl.loopback.handler.LoopbackMessageHandler;
import org.drasyl.monitoring.Monitoring;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.serialization.MessageSerializer;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.remote.handler.ChunkingHandler;
import org.drasyl.remote.handler.HopCountGuard;
import org.drasyl.remote.handler.InternetDiscovery;
import org.drasyl.remote.handler.InvalidProofOfWorkFilter;
import org.drasyl.remote.handler.LocalNetworkDiscovery;
import org.drasyl.remote.handler.OtherNetworkFilter;
import org.drasyl.remote.handler.RateLimiter;
import org.drasyl.remote.handler.RemoteMessageToByteBufCodec;
import org.drasyl.remote.handler.StaticRoutesHandler;
import org.drasyl.remote.handler.UdpMulticastServer;
import org.drasyl.remote.handler.UdpServer;
import org.drasyl.remote.handler.crypto.ArmHandler;
import org.drasyl.remote.handler.portmapper.PortMapper;
import org.drasyl.remote.handler.tcp.TcpClient;
import org.drasyl.remote.handler.tcp.TcpServer;
import org.drasyl.remote.protocol.UnarmedMessage;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.channel.DefaultDrasylServerChannel.CONFIG_ATTR_KEY;
import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;
import static org.drasyl.channel.Null.NULL;

/**
 * A special {@link ChannelInboundHandler} to initialize the default {@link DrasylServerChannel}
 * behavior.
 */
public class DrasylServerChannelInitializer extends ChannelInitializer<Channel> {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylServerChannelInitializer.class);
    public static final String LOOPBACK_MESSAGE_HANDLER = "LOOPBACK_OUTBOUND_MESSAGE_SINK_HANDLER";
    public static final String INTRA_VM_DISCOVERY = "INTRA_VM_DISCOVERY";
    public static final String CHILD_CHANNEL_ROUTER = "CHILD_CHANNEL_ROUTER";
    public static final String MESSAGE_SERIALIZER = "MESSAGE_SERIALIZER";
    public static final String STATIC_ROUTES_HANDLER = "STATIC_ROUTES_HANDLER";
    public static final String LOCAL_HOST_DISCOVERY = "LOCAL_HOST_DISCOVERY";
    public static final String INTERNET_DISCOVERY = "INTERNET_DISCOVERY";
    public static final String LOCAL_NETWORK_DISCOVER = "LOCAL_NETWORK_DISCOVER";
    public static final String HOP_COUNT_GUARD = "HOP_COUNT_GUARD";
    public static final String MONITORING_HANDLER = "MONITORING_HANDLER";
    public static final String RATE_LIMITER = "RATE_LIMITER";
    public static final String UNARMED_MESSAGE_READER = "UNARMED_MESSAGE_READER";
    public static final String ARM_HANDLER = "ARM_HANDLER";
    public static final String INVALID_PROOF_OF_WORK_FILTER = "INVALID_PROOF_OF_WORK_FILTER";
    public static final String OTHER_NETWORK_FILTER = "OTHER_NETWORK_FILTER";
    public static final String CHUNKING_HANDLER = "CHUNKING_HANDLER";
    public static final String REMOTE_MESSAGE_TO_BYTE_BUF_CODEC = "REMOTE_ENVELOPE_TO_BYTE_BUF_CODEC";
    public static final String UDP_MULTICAST_SERVER = "UDP_MULTICAST_SERVER";
    public static final String TCP_SERVER = "TCP_SERVER";
    public static final String TCP_CLIENT = "TCP_CLIENT";
    public static final String PORT_MAPPER = "PORT_MAPPER";
    public static final String UDP_SERVER = "UDP_SERVER";
    public static final String NODE_EVENTS = "NODE_EVENTS";

    @SuppressWarnings({ "java:S138", "java:S1541", "java:S3776" })
    @Override
    protected void initChannel(final Channel ch) {
        final DrasylConfig config = ch.attr(CONFIG_ATTR_KEY).get();

        ch.pipeline().addFirst(new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(final ChannelHandlerContext ctx,
                                        final Throwable e) {
                LOG.warn("drasyl node faced error and will shut down:", e);
                ctx.pipeline().fireUserEventTriggered(NodeUnrecoverableErrorEvent.of(Node.of(ctx.channel().attr(IDENTITY_ATTR_KEY).get()), e));
                ch.close();
            }
        });

        ch.pipeline().addFirst(CHILD_CHANNEL_ROUTER, new ChildChannelRouter());

        // convert outbound messages addresses to us to inbound messages
        ch.pipeline().addFirst(LOOPBACK_MESSAGE_HANDLER, new LoopbackMessageHandler());

        // discover nodes running within the same jvm.
        if (config.isIntraVmDiscoveryEnabled()) {
            ch.pipeline().addFirst(INTRA_VM_DISCOVERY, IntraVmDiscovery.INSTANCE);
        }

        if (config.isRemoteEnabled()) {
            // convert Object <-> ApplicationMessage
            ch.pipeline().addFirst(MESSAGE_SERIALIZER, MessageSerializer.INSTANCE);

            // route outbound messages to pre-configured ip addresses
            if (!config.getRemoteStaticRoutes().isEmpty()) {
                ch.pipeline().addFirst(STATIC_ROUTES_HANDLER, StaticRoutesHandler.INSTANCE);
            }

            if (config.isRemoteLocalHostDiscoveryEnabled()) {
                // discover nodes running on the same local computer
                ch.pipeline().addFirst(LOCAL_HOST_DISCOVERY, new LocalHostDiscovery());
            }

            // discovery nodes on the local network
            if (config.isRemoteLocalNetworkDiscoveryEnabled()) {
                ch.pipeline().addFirst(LOCAL_NETWORK_DISCOVER, new LocalNetworkDiscovery());
            }

            // discover nodes on the internet
            ch.pipeline().addFirst(INTERNET_DISCOVERY, new InternetDiscovery(config));

            // outbound message guards
            ch.pipeline().addFirst(HOP_COUNT_GUARD, HopCountGuard.INSTANCE);

            if (config.isMonitoringEnabled()) {
                ch.pipeline().addFirst(MONITORING_HANDLER, new Monitoring());
            }

            ch.pipeline().addFirst(RATE_LIMITER, new RateLimiter());

            ch.pipeline().addFirst(UNARMED_MESSAGE_READER, new SimpleInboundHandler<UnarmedMessage, Address>() {
                @Override
                protected void matchedInbound(final ChannelHandlerContext ctx,
                                              final Address sender,
                                              final UnarmedMessage msg) throws Exception {
                    ctx.fireChannelRead(new AddressedMessage<>(msg.readAndRelease(), sender));
                }
            });

            // arm outbound and disarm inbound messages
            if (config.isRemoteMessageArmEnabled()) {
                ch.pipeline().addFirst(ARM_HANDLER, new ArmHandler(
                        config.getRemoteMessageArmSessionMaxCount(),
                        config.getRemoteMessageArmSessionMaxAgreements(),
                        config.getRemoteMessageArmSessionExpireAfter(),
                        config.getRemoteMessageArmSessionRetryInterval()));
            }

            // filter out inbound messages with invalid proof of work or other network id
            ch.pipeline().addFirst(INVALID_PROOF_OF_WORK_FILTER, InvalidProofOfWorkFilter.INSTANCE);
            ch.pipeline().addFirst(OTHER_NETWORK_FILTER, OtherNetworkFilter.INSTANCE);

            // split messages too big for udp
            ch.pipeline().addFirst(CHUNKING_HANDLER, new ChunkingHandler());
            // convert RemoteMessage <-> ByteBuf
            ch.pipeline().addFirst(REMOTE_MESSAGE_TO_BYTE_BUF_CODEC, RemoteMessageToByteBufCodec.INSTANCE);

            if (config.isRemoteLocalNetworkDiscoveryEnabled()) {
                ch.pipeline().addFirst(UDP_MULTICAST_SERVER, UdpMulticastServer.getInstance());
            }

            // tcp fallback
            if (config.isRemoteTcpFallbackEnabled()) {
                if (!config.isRemoteSuperPeerEnabled()) {
                    ch.pipeline().addFirst(TCP_SERVER, new TcpServer());
                }
                else {
                    ch.pipeline().addFirst(TCP_CLIENT, new TcpClient(config));
                }
            }

            // udp server
            if (config.isRemoteExposeEnabled()) {
                ch.pipeline().addFirst(PORT_MAPPER, new PortMapper());
            }
            ch.pipeline().addFirst(UDP_SERVER, new UdpServer());
        }

        ch.pipeline().addFirst(NODE_EVENTS, new NodeLifecycleEvents());
    }

    /**
     * Routes inbound messages to the correct child channel and broadcast events to all child
     * channels.
     */
    private static class ChildChannelRouter extends SimpleChannelInboundHandler<AddressedMessage<?, ? extends Address>> {
        @Override
        protected void channelRead0(final ChannelHandlerContext ctx,
                                    final AddressedMessage<?, ? extends Address> migrationMsg) {
            Object msg = migrationMsg.message();
            final IdentityPublicKey sender = (IdentityPublicKey) migrationMsg.address();

            // create/get channel
            final Channel channel = ((DefaultDrasylServerChannel) ctx.channel()).getOrCreateChildChannel(ctx, sender);

            if (msg == null) {
                msg = NULL;
            }

            // pass message to channel
            channel.pipeline().fireChannelRead(msg);
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx,
                                       final Object evt) throws Exception {
            super.userEventTriggered(ctx, evt);

            if (evt instanceof Event) {
                ((DefaultDrasylServerChannel) ctx.channel()).channels().forEach((address, channel) -> channel.pipeline().fireUserEventTriggered(evt));
            }
        }
    }

    /**
     * Emits node lifecycle events ({@link NodeUpEvent}, {@link NodeDownEvent}, {@link
     * NodeNormalTerminationEvent}, {@link NodeUnrecoverableErrorEvent}).
     */
    private static class NodeLifecycleEvents extends ChannelInboundHandlerAdapter {
        private boolean errorOccurred;

        @Override
        public void channelActive(final ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);

            LOG.info("Start drasyl node with identity `{}`...", ctx.channel().localAddress());
            ctx.fireUserEventTriggered(NodeUpEvent.of(Node.of(ctx.channel().attr(IDENTITY_ATTR_KEY).get())));
            LOG.info("drasyl node with identity `{}` has started", ctx.channel().localAddress());
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);

            LOG.info("Shutdown drasyl node with identity `{}`...", ctx.channel().localAddress());
            if (!errorOccurred) {
                ctx.fireUserEventTriggered(NodeDownEvent.of(Node.of(ctx.channel().attr(IDENTITY_ATTR_KEY).get())));
                ctx.fireUserEventTriggered(NodeNormalTerminationEvent.of(Node.of(ctx.channel().attr(IDENTITY_ATTR_KEY).get())));
                LOG.info("drasyl node with identity `{}` has shut down", ctx.channel().localAddress());
            }
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx,
                                       final Object evt) throws Exception {
            if (evt instanceof NodeUnrecoverableErrorEvent) {
                errorOccurred = true;
            }

            super.userEventTriggered(ctx, evt);
        }
    }
}
