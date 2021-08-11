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
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.intravm.IntraVmDiscovery;
import org.drasyl.localhost.LocalHostDiscovery;
import org.drasyl.loopback.handler.LoopbackMessageHandler;
import org.drasyl.monitoring.Monitoring;
import org.drasyl.peer.PeersManager;
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
import org.drasyl.remote.protocol.InvalidMessageFormatException;
import org.drasyl.remote.protocol.UnarmedMessage;

import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.Null.NULL;

/**
 * A special {@link ChannelInboundHandler} to initialize the default drasyl {@link
 * io.netty.channel.ServerChannel} behavior.
 */
public class DrasylServerChannelInitializer extends ChannelInitializer<Channel> {
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
    private final DrasylConfig config;
    private final Serialization inboundSerialization;
    private final Serialization outboundSerialization;
    private final PeersManager peersManager;
    private final Identity identity;

    public DrasylServerChannelInitializer(final DrasylConfig config,
                                          final Serialization inboundSerialization,
                                          final Serialization outboundSerialization,
                                          final PeersManager peersManager,
                                          final Identity identity) {
        this.config = requireNonNull(config);
        this.inboundSerialization = requireNonNull(inboundSerialization);
        this.outboundSerialization = requireNonNull(outboundSerialization);
        this.peersManager = requireNonNull(peersManager);
        this.identity = requireNonNull(identity);
    }

    @SuppressWarnings({ "java:S138", "java:S1541", "java:S3776" })
    @Override
    protected void initChannel(final Channel ch) {
        ch.pipeline().addFirst(CHILD_CHANNEL_ROUTER, new ChildChannelRouter());

        // convert outbound messages addresses to us to inbound messages
        ch.pipeline().addFirst(LOOPBACK_MESSAGE_HANDLER, new LoopbackMessageHandler(identity));

        // discover nodes running within the same jvm
        if (config.isIntraVmDiscoveryEnabled()) {
            ch.pipeline().addFirst(INTRA_VM_DISCOVERY, new IntraVmDiscovery(peersManager, identity));
        }

        if (config.isRemoteEnabled()) {
            // convert Object <-> ApplicationMessage
            ch.pipeline().addFirst(MESSAGE_SERIALIZER, new MessageSerializer(identity, inboundSerialization, outboundSerialization));

            // route outbound messages to pre-configured ip addresses
            if (!config.getRemoteStaticRoutes().isEmpty()) {
                ch.pipeline().addFirst(STATIC_ROUTES_HANDLER, new StaticRoutesHandler(peersManager));
            }

            if (config.isRemoteLocalHostDiscoveryEnabled()) {
                // discover nodes running on the same local computer
                ch.pipeline().addFirst(LOCAL_HOST_DISCOVERY, new LocalHostDiscovery(identity, peersManager));
            }

            // discovery nodes on the local network
            if (config.isRemoteLocalNetworkDiscoveryEnabled()) {
                ch.pipeline().addFirst(LOCAL_NETWORK_DISCOVER, new LocalNetworkDiscovery(identity, peersManager));
            }

            // discover nodes on the internet
            ch.pipeline().addFirst(INTERNET_DISCOVERY, new InternetDiscovery(config, identity, peersManager));

            // outbound message guards
            ch.pipeline().addFirst(HOP_COUNT_GUARD, HopCountGuard.INSTANCE);

            if (config.isMonitoringEnabled()) {
                ch.pipeline().addFirst(MONITORING_HANDLER, new Monitoring());
            }

            ch.pipeline().addFirst(RATE_LIMITER, new RateLimiter(identity));

            ch.pipeline().addFirst(UNARMED_MESSAGE_READER, new SimpleChannelInboundHandler<AddressedMessage<?, ?>>() {
                @Override
                protected void channelRead0(final ChannelHandlerContext ctx,
                                            final AddressedMessage<?, ?> msg) throws InvalidMessageFormatException {
                    if (msg.message() instanceof UnarmedMessage) {
                        ctx.fireChannelRead(new AddressedMessage<>(((UnarmedMessage) msg.message()).read(), msg.address()));
                    }
                    else {
                        ctx.fireChannelRead(msg.retain());
                    }
                }
            });

            // arm outbound and disarm inbound messages
            if (config.isRemoteMessageArmEnabled()) {
                ch.pipeline().addFirst(ARM_HANDLER, new ArmHandler(
                        config.getRemoteMessageArmSessionMaxCount(),
                        config.getRemoteMessageArmSessionMaxAgreements(),
                        config.getRemoteMessageArmSessionExpireAfter(),
                        config.getRemoteMessageArmSessionRetryInterval(), identity));
            }

            // filter out inbound messages with invalid proof of work or other network id
            ch.pipeline().addFirst(INVALID_PROOF_OF_WORK_FILTER, new InvalidProofOfWorkFilter(identity));
            ch.pipeline().addFirst(OTHER_NETWORK_FILTER, OtherNetworkFilter.INSTANCE);

            // split messages too big for udp
            ch.pipeline().addFirst(CHUNKING_HANDLER, new ChunkingHandler(identity));
            // convert RemoteMessage <-> ByteBuf
            ch.pipeline().addFirst(REMOTE_MESSAGE_TO_BYTE_BUF_CODEC, RemoteMessageToByteBufCodec.INSTANCE);

            if (config.isRemoteLocalNetworkDiscoveryEnabled()) {
                ch.pipeline().addFirst(UDP_MULTICAST_SERVER, new UdpMulticastServer(identity));
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
                ch.pipeline().addFirst(PORT_MAPPER, new PortMapper(identity));
            }
            ch.pipeline().addFirst(UDP_SERVER, new UdpServer(identity));
        }
    }

    /**
     * Routes inbound messages to the correct child channel and broadcast events to all child
     * channels.
     */
    private static class ChildChannelRouter extends SimpleChannelInboundHandler<AddressedMessage<?, ?>> {
        @Override
        protected void channelRead0(final ChannelHandlerContext ctx,
                                    final AddressedMessage<?, ?> msg) {
            if (msg.address() instanceof IdentityPublicKey) {
                Object o = msg.message();
                final IdentityPublicKey sender = (IdentityPublicKey) msg.address();

                // create/get channel
                final Channel channel = ((DefaultDrasylServerChannel) ctx.channel()).getOrCreateChildChannel(ctx, sender);

                if (o == null) {
                    o = NULL;
                }

                // pass message to channel
                channel.pipeline().fireChannelRead(o);
            }
            else {
                ctx.fireChannelRead(msg);
            }
        }
    }
}
