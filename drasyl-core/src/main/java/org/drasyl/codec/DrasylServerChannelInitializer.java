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
package org.drasyl.codec;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.DrasylAddress;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.intravm.IntraVmDiscovery;
import org.drasyl.localhost.LocalHostDiscovery;
import org.drasyl.loopback.handler.LoopbackMessageHandler;
import org.drasyl.monitoring.Monitoring;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.pipeline.HandlerContext;
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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.drasyl.codec.Null.NULL;

class DrasylServerChannelInitializer extends ChannelInitializer<Channel> {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylPipeline.class);
    public static final String LOOPBACK_MESSAGE_HANDLER = "LOOPBACK_OUTBOUND_MESSAGE_SINK_HANDLER";
    public static final String INTRA_VM_DISCOVERY = "INTRA_VM_DISCOVERY";
    public static final String CHANNEL_SPAWNER = "CHANNEL_SPAWNER";
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
    private final Consumer<Event> eventConsumer;

    public DrasylServerChannelInitializer(final Consumer<Event> eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    protected void initChannel(final Channel ch) {
        final DrasylConfig config = ((DrasylServerChannel) ch).drasylConfig();

        ch.pipeline().addFirst(CHANNEL_SPAWNER, new ChannelSpawner());

        // convert outbound messages addresses to us to inbound messages
        ch.pipeline().addFirst(LOOPBACK_MESSAGE_HANDLER, new MigrationChannelHandler(new LoopbackMessageHandler()));

        // discover nodes running within the same jvm.
        if (config.isIntraVmDiscoveryEnabled()) {
            ch.pipeline().addFirst(INTRA_VM_DISCOVERY, new MigrationChannelHandler(IntraVmDiscovery.INSTANCE));
        }

        if (config.isRemoteEnabled()) {
            // convert Object <-> ApplicationMessage
            ch.pipeline().addFirst(MESSAGE_SERIALIZER, new MigrationChannelHandler(MessageSerializer.INSTANCE));

            // route outbound messages to pre-configured ip addresses
            if (!config.getRemoteStaticRoutes().isEmpty()) {
                ch.pipeline().addFirst(STATIC_ROUTES_HANDLER, new MigrationChannelHandler(StaticRoutesHandler.INSTANCE));
            }

            if (config.isRemoteLocalHostDiscoveryEnabled()) {
                // discover nodes running on the same local computer
                ch.pipeline().addFirst(LOCAL_HOST_DISCOVERY, new MigrationChannelHandler(new LocalHostDiscovery()));
            }

            // discovery nodes on the local network
            if (config.isRemoteLocalNetworkDiscoveryEnabled()) {
                ch.pipeline().addFirst(LOCAL_NETWORK_DISCOVER, new MigrationChannelHandler(new LocalNetworkDiscovery()));
            }

            // discover nodes on the internet
            ch.pipeline().addFirst(INTERNET_DISCOVERY, new MigrationChannelHandler(new InternetDiscovery(config)));

            // outbound message guards
            ch.pipeline().addFirst(HOP_COUNT_GUARD, new MigrationChannelHandler(HopCountGuard.INSTANCE));

            if (config.isMonitoringEnabled()) {
                ch.pipeline().addFirst(MONITORING_HANDLER, new MigrationChannelHandler(new Monitoring()));
            }

            ch.pipeline().addFirst(RATE_LIMITER, new MigrationChannelHandler(new RateLimiter()));

            ch.pipeline().addFirst(UNARMED_MESSAGE_READER, new MigrationChannelHandler(new SimpleInboundHandler<UnarmedMessage, Address>() {
                @Override
                protected void matchedInbound(final HandlerContext ctx,
                                              final Address sender,
                                              final UnarmedMessage msg,
                                              final CompletableFuture<Void> future) throws Exception {
                    ctx.passInbound(sender, msg.readAndRelease(), future);
                }
            }));

            // arm outbound and disarm inbound messages
            if (config.isRemoteMessageArmEnabled()) {
                ch.pipeline().addFirst(ARM_HANDLER, new MigrationChannelHandler(new ArmHandler(
                        config.getRemoteMessageArmSessionMaxCount(),
                        config.getRemoteMessageArmSessionMaxAgreements(),
                        config.getRemoteMessageArmSessionExpireAfter(),
                        config.getRemoteMessageArmSessionRetryInterval())));
            }

            // filter out inbound messages with invalid proof of work or other network id
            ch.pipeline().addFirst(INVALID_PROOF_OF_WORK_FILTER, new MigrationChannelHandler(InvalidProofOfWorkFilter.INSTANCE));
            ch.pipeline().addFirst(OTHER_NETWORK_FILTER, new MigrationChannelHandler(OtherNetworkFilter.INSTANCE));

            // split messages too big for udp
            ch.pipeline().addFirst(CHUNKING_HANDLER, new MigrationChannelHandler(new ChunkingHandler()));
            // convert RemoteMessage <-> ByteBuf
            ch.pipeline().addFirst(REMOTE_MESSAGE_TO_BYTE_BUF_CODEC, new MigrationChannelHandler(RemoteMessageToByteBufCodec.INSTANCE));

            if (config.isRemoteLocalNetworkDiscoveryEnabled()) {
                ch.pipeline().addFirst(UDP_MULTICAST_SERVER, new MigrationChannelHandler(UdpMulticastServer.getInstance()));
            }

            // tcp fallback
            if (config.isRemoteTcpFallbackEnabled()) {
                if (!config.isRemoteSuperPeerEnabled()) {
                    ch.pipeline().addFirst(TCP_SERVER, new MigrationChannelHandler(new TcpServer()));
                }
                else {
                    ch.pipeline().addFirst(TCP_CLIENT, new MigrationChannelHandler(new TcpClient(config)));
                }
            }

            // udp server
            if (config.isRemoteExposeEnabled()) {
                ch.pipeline().addFirst(PORT_MAPPER, new MigrationChannelHandler(new PortMapper()));
            }
            ch.pipeline().addFirst(UDP_SERVER, new MigrationChannelHandler(new UdpServer()));
        }

        ch.pipeline().addFirst(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(final ChannelHandlerContext ctx) throws Exception {
                super.channelActive(ctx);

                final Event event = NodeUpEvent.of(Node.of(((DrasylServerChannel) ctx.channel()).identity()));
                ctx.fireUserEventTriggered(event);
            }

            @Override
            public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
                super.channelInactive(ctx);

                final Event event = NodeDownEvent.of(Node.of(((DrasylServerChannel) ctx.channel()).identity()));
                ctx.fireUserEventTriggered(event);
            }

            @Override
            public void channelUnregistered(final ChannelHandlerContext ctx) throws Exception {
                super.channelUnregistered(ctx);

                final Event event = NodeNormalTerminationEvent.of(Node.of(((DrasylServerChannel) ctx.channel()).identity()));
                ctx.fireUserEventTriggered(event);
            }
        });
    }

    private class ChannelSpawner extends SimpleChannelInboundHandler<MigrationMessage> {
        private final Map<DrasylAddress, Channel> channels = new ConcurrentHashMap<>();

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx,
                                       final Object evt) throws Exception {
            if (evt instanceof Event) {
                eventConsumer.accept((Event) evt);
            }
            else if (evt instanceof DrasylNode.OutboundMessage) {
                final DrasylAddress recipient = ((DrasylNode.OutboundMessage) evt).getRecipient();
                Object payload = ((DrasylNode.OutboundMessage) evt).getPayload();
                final CompletableFuture<Void> future = ((DrasylNode.OutboundMessage) evt).getFuture();

                final Channel channel = channels.computeIfAbsent(recipient, key -> {
                    final DrasylChannel channel1 = new DrasylChannel(ctx.channel(), (IdentityPublicKey) recipient);
                    channel1.closeFuture().addListener(f -> channels.remove(key));
                    ctx.fireChannelRead(channel1);
                    return channel1;
                });

                if (payload == null) {
                    payload = NULL;
                }

                channel.writeAndFlush(payload).addListener(f -> {
                    if (f.isSuccess()) {
                        future.complete(null);
                    }
                    else {
                        future.completeExceptionally(f.cause());
                    }
                });
            }
            else {
                super.userEventTriggered(ctx, evt);
            }
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx,
                                    final MigrationMessage addressedMsg) {
            Object msg = addressedMsg.message();
            final IdentityPublicKey sender = (IdentityPublicKey) addressedMsg.address();

            // create/get channel
            final Channel channel = channels.computeIfAbsent(sender, key -> {
                final DrasylChannel channel1 = new DrasylChannel(ctx.channel(), sender);
                channel1.closeFuture().addListener(future -> channels.remove(key));
                ctx.fireChannelRead(channel1);
                return channel1;
            });

            if (msg == null) {
                msg = NULL;
            }

            // pass message to channel
            channel.pipeline().fireChannelRead(msg);
        }
    }
}
