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
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.DrasylAddress;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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
        ch.pipeline().addFirst(CHANNEL_SPAWNER, new ChannelSpawner());

        if (((DrasylServerChannel) ch).drasylConfig().isRemoteEnabled()) {
            // convert Object <-> ApplicationMessage
            ch.pipeline().addFirst(MESSAGE_SERIALIZER, MessageSerializer.INSTANCE);

            // discover nodes on the internet
            ch.pipeline().addFirst(INTERNET_DISCOVERY, new InternetDiscovery(((DrasylServerChannel) ch).drasylConfig()));

            // arm outbound and disarm inbound messages
            if (((DrasylServerChannel) ch).drasylConfig().isRemoteMessageArmEnabled()) {
                ch.pipeline().addFirst(ARM_HANDLER, new ArmHandler(
                        ((DrasylServerChannel) ch).drasylConfig().getRemoteMessageArmSessionMaxCount(),
                        ((DrasylServerChannel) ch).drasylConfig().getRemoteMessageArmSessionMaxAgreements(),
                        ((DrasylServerChannel) ch).drasylConfig().getRemoteMessageArmSessionExpireAfter(),
                        ((DrasylServerChannel) ch).drasylConfig().getRemoteMessageArmSessionRetryInterval()));
            }

            // convert RemoteMessage <-> ByteBuf
            ch.pipeline().addFirst(REMOTE_MESSAGE_TO_BYTE_BUF_CODEC, RemoteMessageToByteBufCodec.INSTANCE);

            // udp server
            if (((DrasylServerChannel) ch).drasylConfig().isRemoteExposeEnabled()) {
                ch.pipeline().addFirst(PORT_MAPPER, new PortMapper());
            }
            ch.pipeline().addFirst(UDP_SERVER, new UdpServer());
        }
    }

    private class ChannelSpawner extends SimpleChannelInboundHandler<AddressedObject> {
        private Identity identity;
        private final Map<DrasylAddress, Channel> channels = new ConcurrentHashMap<>();

        @Override
        public void channelUnregistered(final ChannelHandlerContext ctx) throws Exception {
            eventConsumer.accept(NodeNormalTerminationEvent.of(Node.of(identity)));

            super.channelUnregistered(ctx);
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) throws Exception {
            identity = ((DrasylServerChannel) ctx.channel()).localAddress0();

            eventConsumer.accept(NodeUpEvent.of(Node.of(identity)));

            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
            eventConsumer.accept(NodeDownEvent.of(Node.of(identity)));

            super.channelInactive(ctx);
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx,
                                       final Object evt) throws Exception {
            if (evt instanceof Event) {
                eventConsumer.accept((Event) evt);
            }

            super.userEventTriggered(ctx, evt);
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx,
                                    final AddressedObject addressedMsg) throws Exception {
            final Object msg = addressedMsg.content();
            final IdentityPublicKey sender = addressedMsg.sender();

            // create/get channel
            final Channel channel = channels.computeIfAbsent(sender, key -> {
                final DrasylChannel channel1 = new DrasylChannel(ctx.channel(), sender);
                channel1.closeFuture().addListener(future -> channels.remove(key));
                ctx.fireChannelRead(channel1);
                return channel1;
            });

            // pass message to channel
            channel.pipeline().fireChannelRead(msg);
        }
    }
}
