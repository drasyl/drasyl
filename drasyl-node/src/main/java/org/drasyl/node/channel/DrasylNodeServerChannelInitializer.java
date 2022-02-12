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
package org.drasyl.node.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.EncoderException;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.crypto.Crypto;
import org.drasyl.handler.discovery.IntraVmDiscovery;
import org.drasyl.handler.monitoring.TelemetryHandler;
import org.drasyl.handler.remote.ApplicationMessageToPayloadCodec;
import org.drasyl.handler.remote.ByteToRemoteMessageCodec;
import org.drasyl.handler.remote.InvalidProofOfWorkFilter;
import org.drasyl.handler.remote.LocalHostDiscovery;
import org.drasyl.handler.remote.LocalNetworkDiscovery;
import org.drasyl.handler.remote.OtherNetworkFilter;
import org.drasyl.handler.remote.RateLimiter;
import org.drasyl.handler.remote.StaticRoutesHandler;
import org.drasyl.handler.remote.UdpMulticastServer;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.crypto.ProtocolArmHandler;
import org.drasyl.handler.remote.crypto.UnarmedMessageDecoder;
import org.drasyl.handler.remote.internet.TraversingInternetDiscoveryChildrenHandler;
import org.drasyl.handler.remote.internet.TraversingInternetDiscoverySuperPeerHandler;
import org.drasyl.handler.remote.portmapper.PortMapper;
import org.drasyl.handler.remote.protocol.HopCount;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.handler.remote.tcp.TcpClient;
import org.drasyl.handler.remote.tcp.TcpServer;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.PeerEndpoint;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.InboundExceptionEvent;
import org.drasyl.node.event.Node;
import org.drasyl.node.event.NodeDownEvent;
import org.drasyl.node.event.NodeNormalTerminationEvent;
import org.drasyl.node.event.NodeUnrecoverableErrorEvent;
import org.drasyl.node.event.NodeUpEvent;
import org.drasyl.node.handler.PeersManagerHandler;
import org.drasyl.node.handler.plugin.PluginsHandler;
import org.drasyl.util.Murmur3;
import org.drasyl.util.UnsignedInteger;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.remote.UdpMulticastServer.MULTICAST_ADDRESS;
import static org.drasyl.util.RandomUtil.randomLong;
import static org.drasyl.util.network.NetworkUtil.MAX_PORT_NUMBER;

/**
 * Initialize the {@link DrasylServerChannel} used by {@link DrasylNode}.
 */
public class DrasylNodeServerChannelInitializer extends ChannelInitializer<DrasylServerChannel> {
    public static final short MIN_DERIVED_PORT = 22528;
    private static final UdpMulticastServer UDP_MULTICAST_SERVER = new UdpMulticastServer();
    private static final ByteToRemoteMessageCodec BYTE_TO_REMOTE_MESSAGE_CODEC = new ByteToRemoteMessageCodec();
    private static final UnarmedMessageDecoder UNARMED_MESSAGE_DECODER = new UnarmedMessageDecoder();
    private static final boolean TELEMETRY_ENABLED = SystemPropertyUtil.getBoolean("org.drasyl.telemetry.enabled", false);
    private static final boolean TELEMETRY_IP_ENABLED = SystemPropertyUtil.getBoolean("org.drasyl.telemetry.ip.enabled", false);
    private static final int TELEMETRY_INTERVAL_SECONDS = SystemPropertyUtil.getInt("org.drasyl.telemetry.interval", 60);
    private static final URI TELEMETRY_URI;

    static {
        final URI uri;
        try {
            uri = new URI(SystemPropertyUtil.get("org.drasyl.telemetry.uri", "https://ping.drasyl.network/"));
        }
        catch (final URISyntaxException e) {
            throw new RuntimeException(e); // NOSONARR
        }
        TELEMETRY_URI = uri;
    }

    private final DrasylConfig config;
    private final DrasylNode node;
    private final Identity identity;

    public DrasylNodeServerChannelInitializer(final DrasylConfig config,
                                              final Identity identity,
                                              final DrasylNode node) {
        this.config = requireNonNull(config);
        this.identity = requireNonNull(identity);
        this.node = requireNonNull(node);
    }

    @SuppressWarnings("java:S1188")
    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        node.channels = new DefaultChannelGroup(ch.eventLoop());
        ch.pipeline().addLast(new NodeLifecycleHeadHandler(identity));

        if (config.isRemoteEnabled()) {
            ipStage(ch);
            serializationStage(ch);
            gatekeeperStage(ch);
        }
        discoveryStage(ch);

        ch.pipeline().addLast(new PeersManagerHandler(identity));
        ch.pipeline().addLast(new PluginsHandler(config, identity));
        ch.pipeline().addLast(new NodeLifecycleTailHandler(node));

        if (TELEMETRY_ENABLED) {
            ch.pipeline().addLast(new TelemetryHandler(TELEMETRY_INTERVAL_SECONDS, TELEMETRY_URI, TELEMETRY_IP_ENABLED));
        }
    }

    /**
     * Send/receive messages via IP.
     */
    private void ipStage(final DrasylServerChannel ch) {
        // udp server
        ch.pipeline().addLast(new UdpServer(config.getRemoteBindHost(), udpServerPort(config.getRemoteBindPort(), identity.getAddress())));

        // port mapping (PCP, NAT-PMP, UPnP-IGD, etc.)
        if (config.isRemoteExposeEnabled()) {
            ch.pipeline().addLast(new PortMapper());
        }

        // tcp fallback
        if (config.isRemoteTcpFallbackEnabled()) {
            if (!config.isRemoteSuperPeerEnabled()) {
                ch.pipeline().addLast(new TcpServer(
                        config.getRemoteTcpFallbackServerBindHost(),
                        config.getRemoteTcpFallbackServerBindPort(),
                        config.getRemotePingTimeout()
                ));
            }
            else {
                ch.pipeline().addLast(new TcpClient(
                        config.getRemoteSuperPeerEndpoints().stream().map(PeerEndpoint::toInetSocketAddress).collect(Collectors.toSet()),
                        config.getRemoteTcpFallbackClientTimeout(),
                        config.getRemoteTcpFallbackClientAddress()
                ));
            }
        }

        // multicast server (lan discovery)
        if (config.isRemoteLocalNetworkDiscoveryEnabled()) {
            ch.pipeline().addLast(UDP_MULTICAST_SERVER);
        }
    }

    /**
     * This stage serializes {@link RemoteMessage} to {@link io.netty.buffer.ByteBuf} and vice
     * versa.
     */
    @SuppressWarnings("java:S2325")
    private void serializationStage(final DrasylServerChannel ch) {
        ch.pipeline().addLast(BYTE_TO_REMOTE_MESSAGE_CODEC);
    }

    /**
     * This stage adds some security services (encryption, sign/verify, throttle, detect message
     * loops, foreight network filter, proof of work checker, etc...).
     */
    private void gatekeeperStage(final DrasylServerChannel ch) {
        // filter out inbound messages with invalid proof of work or other network id
        ch.pipeline().addLast(new OtherNetworkFilter(config.getNetworkId()));
        ch.pipeline().addLast(new InvalidProofOfWorkFilter());

        // arm outbound and disarm inbound messages
        if (config.isRemoteMessageArmProtocolEnabled()) {
            ch.pipeline().addLast(new ProtocolArmHandler(
                    identity,
                    Crypto.INSTANCE,
                    config.getRemoteMessageArmProtocolSessionMaxCount(),
                    config.getRemoteMessageArmProtocolSessionExpireAfter()));
        }

        // fully read unarmed messages (local network discovery)
        ch.pipeline().addLast(UNARMED_MESSAGE_DECODER);

        ch.pipeline().addLast(new RateLimiter());
    }

    private void discoveryStage(final DrasylServerChannel ch) {
        if (config.isRemoteEnabled()) {
            // discover nodes on the internet
            if (config.isRemoteSuperPeerEnabled()) {
                final Map<IdentityPublicKey, InetSocketAddress> superPeerAddresses = config.getRemoteSuperPeerEndpoints().stream().collect(Collectors.toMap(PeerEndpoint::getIdentityPublicKey, PeerEndpoint::toInetSocketAddress));
                ch.pipeline().addLast(new TraversingInternetDiscoveryChildrenHandler(
                        config.getNetworkId(),
                        identity.getIdentityPublicKey(),
                        identity.getIdentitySecretKey(),
                        identity.getProofOfWork(),
                        randomLong(config.getRemotePingInterval().toMillis()),
                        config.getRemotePingInterval().toMillis(),
                        config.getRemotePingTimeout().toMillis(),
                        config.getRemotePingTimeout().multipliedBy(2).toMillis(),
                        superPeerAddresses,
                        config.getRemotePingCommunicationTimeout().toMillis(),
                        config.getRemotePingMaxPeers()));
            }
            else {
                ch.pipeline().addLast(new TraversingInternetDiscoverySuperPeerHandler(
                        config.getNetworkId(),
                        identity.getIdentityPublicKey(),
                        identity.getProofOfWork(),
                        config.getRemotePingInterval().toMillis(),
                        config.getRemotePingTimeout().toMillis(),
                        config.getRemotePingTimeout().multipliedBy(2).toMillis(),
                        HopCount.of(config.getRemoteMessageHopLimit()),
                        config.getRemoteUniteMinInterval().toMillis()
                ));
            }

            // discover nodes on the local network
            if (config.isRemoteLocalNetworkDiscoveryEnabled()) {
                ch.pipeline().addLast(new LocalNetworkDiscovery(
                        config.getNetworkId(),
                        config.getRemotePingInterval().toMillis(),
                        config.getRemotePingTimeout().toMillis(),
                        identity.getIdentityPublicKey(),
                        identity.getProofOfWork(),
                        MULTICAST_ADDRESS
                ));
            }

            if (config.isRemoteLocalHostDiscoveryEnabled()) {
                // discover nodes running on the same local computer
                ch.pipeline().addLast(new LocalHostDiscovery(
                        config.getNetworkId(),
                        config.isRemoteLocalHostDiscoveryWatchEnabled(),
                        config.getRemoteBindHost(),
                        config.getRemoteLocalHostDiscoveryLeaseTime(),
                        config.getRemoteLocalHostDiscoveryPath()
                ));
            }

            // route outbound messages to pre-configured ip addresses
            if (!config.getRemoteStaticRoutes().isEmpty()) {
                ch.pipeline().addLast(new StaticRoutesHandler(config.getRemoteStaticRoutes()));
            }

            // convert ByteBuf <-> ApplicationMessage
            ch.pipeline().addLast(new ApplicationMessageToPayloadCodec(config.getNetworkId(), identity.getIdentityPublicKey(), identity.getProofOfWork()));
        }

        // discover nodes running within the same jvm
        if (config.isIntraVmDiscoveryEnabled()) {
            ch.pipeline().addLast(new IntraVmDiscovery(config.getNetworkId()));
        }
    }

    private static int udpServerPort(final int remoteBindPort, final DrasylAddress address) {
        if (remoteBindPort == -1) {
            /*
             derive a port in the range between MIN_DERIVED_PORT and {MAX_PORT_NUMBER from its
             own identity. this is done because we also expose this port via
             UPnP-IGD/NAT-PMP/PCP and some NAT devices behave unexpectedly when multiple nodes
             in the local network try to expose the same local port.
             a completely random port would have the disadvantage that every time the node is
             started it would use a new port and this would make discovery more difficult
            */
            final long identityHash = UnsignedInteger.of(Murmur3.murmur3_x86_32BytesLE(address.toByteArray())).getValue();
            return (int) (MIN_DERIVED_PORT + identityHash % (MAX_PORT_NUMBER - MIN_DERIVED_PORT));
        }
        else {
            return remoteBindPort;
        }
    }

    /**
     * Emits {@link NodeNormalTerminationEvent} on {@link #channelActive(ChannelHandlerContext)} and
     * {@link NodeUnrecoverableErrorEvent} or {@link InboundExceptionEvent} on {@link
     * #exceptionCaught(ChannelHandlerContext, Throwable)}. This handler must be placed at the tail
     * of the pipeline.
     */
    private static class NodeLifecycleTailHandler extends ChannelInboundHandlerAdapter {
        private static final Logger LOG = LoggerFactory.getLogger(NodeLifecycleTailHandler.class);
        private final DrasylNode node;
        private boolean errorOccurred;

        NodeLifecycleTailHandler(final DrasylNode node) {
            this.node = requireNonNull(node);
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            ctx.fireChannelActive();

            LOG.info("drasyl node with identity `{}` has started", ctx.channel().localAddress());
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) {
            ctx.fireChannelInactive();

            if (!errorOccurred) {
                userEventTriggered(ctx, NodeNormalTerminationEvent.of(Node.of(node.identity())));
                LOG.info("drasyl node with identity `{}` has shut down", ctx.channel().localAddress());
            }
        }

        @SuppressWarnings("java:S2221")
        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx,
                                       final Object evt) {
            if (evt instanceof Event) {
                if (evt instanceof NodeUnrecoverableErrorEvent) {
                    errorOccurred = true;
                }
                else if (errorOccurred && evt instanceof NodeDownEvent) {
                    // swallow event
                    return;
                }

                node.onEvent((Event) evt);
            }
            else {
                ctx.fireUserEventTriggered(evt);
            }
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx,
                                    final Throwable e) {
            if (e instanceof UdpServer.BindFailedException || e instanceof TcpServer.BindFailedException) {
                LOG.warn("drasyl node faced unrecoverable error and must shut down:", e);
                userEventTriggered(ctx, NodeUnrecoverableErrorEvent.of(Node.of(node.identity()), e));
                ctx.close();
            }
            else if (e instanceof EncoderException) {
                LOG.error(e);
            }
            else {
                userEventTriggered(ctx, InboundExceptionEvent.of(e));
            }
        }
    }

    /**
     * Emits {@link NodeUpEvent} on {@link #channelActive(ChannelHandlerContext)} and {@link
     * NodeDownEvent} on {@link #channelInactive(ChannelHandlerContext)}. This handler must be
     * placed at the head of the pipeline.
     */
    private static class NodeLifecycleHeadHandler extends ChannelInboundHandlerAdapter {
        private static final Logger LOG = LoggerFactory.getLogger(NodeLifecycleHeadHandler.class);
        private final Identity identity;

        public NodeLifecycleHeadHandler(final Identity identity) {
            this.identity = requireNonNull(identity);
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            LOG.info("Start drasyl node with identity `{}`...", ctx.channel().localAddress());
            ctx.fireUserEventTriggered(NodeUpEvent.of(Node.of(identity)));
            ctx.fireChannelActive();
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) {
            LOG.info("Shutdown drasyl node with identity `{}`...", ctx.channel().localAddress());
            ctx.fireUserEventTriggered(NodeDownEvent.of(Node.of(identity)));
            ctx.fireChannelInactive();
        }
    }
}
