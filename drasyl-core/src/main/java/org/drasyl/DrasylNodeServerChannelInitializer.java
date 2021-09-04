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
package org.drasyl;

import com.google.common.hash.Hashing;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.ApplicationMessageCodec;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.event.Event;
import org.drasyl.event.InboundExceptionEvent;
import org.drasyl.event.Node;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.Identity;
import org.drasyl.intravm.IntraVmDiscovery;
import org.drasyl.localhost.LocalHostDiscovery;
import org.drasyl.monitoring.Monitoring;
import org.drasyl.peer.PeersManagerHandler;
import org.drasyl.plugin.PluginManager;
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
import org.drasyl.util.UnsignedInteger;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.network.NetworkUtil.MAX_PORT_NUMBER;

/**
 * Initialize the {@link DrasylServerChannel} used by {@link DrasylNode}.
 */
public class DrasylNodeServerChannelInitializer extends ChannelInitializer<DrasylServerChannel> {
    public static final short MIN_DERIVED_PORT = 22528;
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

        ch.pipeline().addFirst(new NodeLifecycleHandler(node));

        ch.pipeline().addFirst(new PluginManagerHandler(new PluginManager(config, identity)));

        ch.pipeline().addFirst(new PeersManagerHandler(identity));

        // convert ByteBuf <-> ApplicationMessage
        ch.pipeline().addFirst(new ApplicationMessageCodec(config.getNetworkId(), identity.getIdentityPublicKey(), identity.getProofOfWork()));

        // discover nodes running within the same jvm
        if (config.isIntraVmDiscoveryEnabled()) {
            ch.pipeline().addFirst(new IntraVmDiscovery(config.getNetworkId(), identity.getAddress()));
        }

        if (config.isRemoteEnabled()) {
            // route outbound messages to pre-configured ip addresses
            if (!config.getRemoteStaticRoutes().isEmpty()) {
                ch.pipeline().addFirst(new StaticRoutesHandler(config.getRemoteStaticRoutes()));
            }

            if (config.isRemoteLocalHostDiscoveryEnabled()) {
                // discover nodes running on the same local computer
                ch.pipeline().addFirst(new LocalHostDiscovery(
                        config.getNetworkId(),
                        config.isRemoteLocalHostDiscoveryWatchEnabled(),
                        config.getRemoteBindHost(),
                        config.getRemoteLocalHostDiscoveryLeaseTime(),
                        config.getRemoteLocalHostDiscoveryPath(),
                        identity.getAddress()
                ));
            }

            // discover nodes on the local network
            if (config.isRemoteLocalNetworkDiscoveryEnabled()) {
                ch.pipeline().addFirst(new LocalNetworkDiscovery(
                        config.getNetworkId(),
                        config.getRemotePingInterval(),
                        config.getRemotePingTimeout(),
                        identity.getAddress(),
                        identity.getProofOfWork()
                ));
            }

            // discover nodes on the internet
            ch.pipeline().addFirst(new InternetDiscovery(
                    config.getNetworkId(),
                    config.getRemotePingMaxPeers(),
                    config.getRemotePingInterval(),
                    config.getRemotePingTimeout(),
                    config.getRemotePingCommunicationTimeout(),
                    config.isRemoteSuperPeerEnabled(),
                    config.getRemoteSuperPeerEndpoints(),
                    config.getRemoteUniteMinInterval(),
                    identity.getAddress(),
                    identity.getProofOfWork()
            ));

            // outbound message guard
            ch.pipeline().addFirst(new HopCountGuard(config.getRemoteMessageHopLimit()));

            if (config.isMonitoringEnabled()) {
                ch.pipeline().addFirst(new Monitoring(
                        config.getMonitoringHostTag(),
                        config.getMonitoringInfluxUri(),
                        config.getMonitoringInfluxUser(),
                        config.getMonitoringInfluxPassword(),
                        config.getMonitoringInfluxDatabase(),
                        config.getMonitoringInfluxReportingFrequency()
                ));
            }

            ch.pipeline().addFirst(new RateLimiter(identity.getAddress()));

            ch.pipeline().addFirst(new UnarmedMessageDecoder());

            // arm outbound and disarm inbound messages
            if (config.isRemoteMessageArmEnabled()) {
                ch.pipeline().addFirst(new ArmHandler(
                        config.getNetworkId(),
                        config.getRemoteMessageArmSessionMaxCount(),
                        config.getRemoteMessageArmSessionMaxAgreements(),
                        config.getRemoteMessageArmSessionExpireAfter(),
                        config.getRemoteMessageArmSessionRetryInterval(),
                        identity
                ));
            }

            // filter out inbound messages with invalid proof of work or other network id
            ch.pipeline().addFirst(new InvalidProofOfWorkFilter(identity.getAddress()));
            ch.pipeline().addFirst(new OtherNetworkFilter(config.getNetworkId()));

            // split messages too big for udp
            ch.pipeline().addFirst(new ChunkingHandler(
                    config.getRemoteMessageMaxContentLength(),
                    config.getRemoteMessageMtu(),
                    config.getRemoteMessageComposedMessageTransferTimeout(),
                    identity.getAddress()
            ));

            // convert RemoteMessage <-> ByteBuf
            ch.pipeline().addFirst(RemoteMessageToByteBufCodec.INSTANCE);

            // multicast server (lan discovery)
            if (config.isRemoteLocalNetworkDiscoveryEnabled()) {
                ch.pipeline().addFirst(UdpMulticastServer.INSTANCE);
            }

            // tcp fallback
            if (config.isRemoteTcpFallbackEnabled()) {
                if (!config.isRemoteSuperPeerEnabled()) {
                    ch.pipeline().addFirst(new TcpServer(
                            config.getRemoteTcpFallbackServerBindHost(),
                            config.getRemoteTcpFallbackServerBindPort(),
                            config.getRemotePingTimeout()
                    ));
                }
                else {
                    ch.pipeline().addFirst(new TcpClient(
                            config.getRemoteSuperPeerEndpoints(),
                            config.getRemoteTcpFallbackClientTimeout(),
                            config.getRemoteTcpFallbackClientAddress()
                    ));
                }
            }

            // port mapping (PCP, NAT-PMP, UPnP-IGD, etc.)
            if (config.isRemoteExposeEnabled()) {
                ch.pipeline().addFirst(new PortMapper());
            }

            // udp server
            ch.pipeline().addFirst(new UdpServer(config.getRemoteBindHost(), udpServerPort(config.getRemoteBindPort(), identity.getAddress())));
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
            final long identityHash = UnsignedInteger.of(Hashing.murmur3_32().hashBytes(address.toByteArray()).asBytes()).getValue();
            return (int) (MIN_DERIVED_PORT + identityHash % (MAX_PORT_NUMBER - MIN_DERIVED_PORT));
        }
        else {
            return remoteBindPort;
        }
    }

    private static class PluginManagerHandler extends ChannelInboundHandlerAdapter {
        private final PluginManager pluginManager;

        PluginManagerHandler(final PluginManager pluginManager) {
            this.pluginManager = pluginManager;
        }

        @Override
        public void channelRegistered(final ChannelHandlerContext ctx) throws Exception {
            super.channelRegistered(ctx);

            pluginManager.beforeStart(ctx);
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);

            pluginManager.afterStart(ctx);
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);

            pluginManager.beforeShutdown(ctx);
        }

        @Override
        public void channelUnregistered(final ChannelHandlerContext ctx) throws Exception {
            super.channelUnregistered(ctx);

            pluginManager.afterShutdown(ctx);
        }
    }

    private static class NodeLifecycleHandler extends ChannelInboundHandlerAdapter {
        private static final Logger LOG = LoggerFactory.getLogger(NodeLifecycleHandler.class);
        private final DrasylNode node;
        private boolean errorOccurred;

        NodeLifecycleHandler(final DrasylNode node) {
            this.node = requireNonNull(node);
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);

            LOG.info("Start drasyl node with identity `{}`...", ctx.channel().localAddress());
            userEventTriggered(ctx, NodeUpEvent.of(Node.of((Identity) ctx.channel().localAddress())));
            LOG.info("drasyl node with identity `{}` has started", ctx.channel().localAddress());
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);

            if (!errorOccurred) {
                LOG.info("Shutdown drasyl node with identity `{}`...", ctx.channel().localAddress());
                userEventTriggered(ctx, NodeDownEvent.of(Node.of((Identity) ctx.channel().localAddress())));
                userEventTriggered(ctx, NodeNormalTerminationEvent.of(Node.of((Identity) ctx.channel().localAddress())));
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

                ctx.executor().execute(() -> node.onEvent((Event) evt));
            }

            // drop all other events
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx,
                                    final Throwable e) {
            if (e instanceof UdpServer.BindFailedException || e instanceof TcpServer.BindFailedException) {
                LOG.warn("drasyl node faced unrecoverable error and must shut down:", e);
                userEventTriggered(ctx, NodeUnrecoverableErrorEvent.of(Node.of((Identity) ctx.channel().localAddress()), e));
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

    private static class UnarmedMessageDecoder extends MessageToMessageDecoder<AddressedMessage<UnarmedMessage, ?>> {
        @Override
        public boolean acceptInboundMessage(final Object msg) {
            return msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof UnarmedMessage;
        }

        @Override
        protected void decode(final ChannelHandlerContext ctx,
                              final AddressedMessage<UnarmedMessage, ?> msg,
                              final List<Object> out) throws Exception {
            out.add(new AddressedMessage<>(msg.message().retain().read(), msg.address()));
        }
    }
}
