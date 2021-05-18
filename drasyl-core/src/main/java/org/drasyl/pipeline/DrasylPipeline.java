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
package org.drasyl.pipeline;

import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.intravm.IntraVmDiscovery;
import org.drasyl.localhost.LocalHostDiscovery;
import org.drasyl.loopback.handler.LoopbackMessageHandler;
import org.drasyl.monitoring.Monitoring;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.serialization.MessageSerializer;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.remote.handler.ChunkingHandler;
import org.drasyl.remote.handler.HopCountGuard;
import org.drasyl.remote.handler.InternetDiscovery;
import org.drasyl.remote.handler.InvalidProofOfWorkFilter;
import org.drasyl.remote.handler.LocalNetworkDiscovery;
import org.drasyl.remote.handler.OtherNetworkFilter;
import org.drasyl.remote.handler.RateLimiter;
import org.drasyl.remote.handler.RemoteEnvelopeToByteBufCodec;
import org.drasyl.remote.handler.StaticRoutesHandler;
import org.drasyl.remote.handler.UdpMulticastServer;
import org.drasyl.remote.handler.UdpServer;
import org.drasyl.remote.handler.crypto.ArmHandler;
import org.drasyl.remote.handler.portmapper.PortMapper;
import org.drasyl.remote.handler.tcp.TcpClient;
import org.drasyl.remote.handler.tcp.TcpServer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.scheduler.DrasylScheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.drasyl.util.scheduler.DrasylSchedulerUtil.getInstanceHeavy;
import static org.drasyl.util.scheduler.DrasylSchedulerUtil.getInstanceLight;

/**
 * The default {@link Pipeline} implementation. Used to implement plugins for drasyl.
 */
public class DrasylPipeline extends AbstractPipeline {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylPipeline.class);
    public static final String LOOPBACK_MESSAGE_HANDLER = "LOOPBACK_OUTBOUND_MESSAGE_SINK_HANDLER";
    public static final String INTRA_VM_DISCOVERY = "INTRA_VM_DISCOVERY";
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
    public static final String REMOTE_ENVELOPE_TO_BYTE_BUF_CODEC = "REMOTE_ENVELOPE_TO_BYTE_BUF_CODEC";
    public static final String UDP_MULTICAST_SERVER = "UDP_MULTICAST_SERVER";
    public static final String TCP_SERVER = "TCP_SERVER";
    public static final String TCP_CLIENT = "TCP_CLIENT";
    public static final String PORT_MAPPER = "PORT_MAPPER";
    public static final String UDP_SERVER = "UDP_SERVER";

    @SuppressWarnings({ "java:S107", "java:S1541", "java:S3776" })
    DrasylPipeline(final Consumer<Event> eventConsumer,
                   final DrasylConfig config,
                   final Identity identity,
                   final PeersManager peersManager,
                   final Supplier<UdpServer> udpServerProvider,
                   final Supplier<UdpMulticastServer> udpMulticastServerProvider,
                   final Supplier<TcpServer> tcpServerProvider,
                   final Supplier<TcpClient> tcpClientProvider) {
        super(
                new ConcurrentHashMap<>(),
                getInstanceLight(),
                getInstanceHeavy(),
                config,
                identity,
                peersManager,
                new Serialization(config.getSerializationSerializers(), config.getSerializationsBindingsInbound()),
                new Serialization(config.getSerializationSerializers(), config.getSerializationsBindingsOutbound()),
                config.getMessageBufferSize() > 0 ? new Semaphore(config.getMessageBufferSize()) : null);
        this.head = new HeadContext(config, this, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
        this.tail = new TailContext(eventConsumer, config, this, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

        initPointer();

        // convert outbound messages addresses to us to inbound messages
        addFirst(LOOPBACK_MESSAGE_HANDLER, new LoopbackMessageHandler());

        // discover nodes running within the same jvm.
        if (config.isIntraVmDiscoveryEnabled()) {
            addFirst(INTRA_VM_DISCOVERY, IntraVmDiscovery.INSTANCE);
        }

        if (config.isRemoteEnabled()) {
            // convert Object <-> RemoteEnvelope<Application>
            addFirst(MESSAGE_SERIALIZER, MessageSerializer.INSTANCE);

            // route outbound messages to pre-configures ip addresses
            if (!config.getRemoteStaticRoutes().isEmpty()) {
                addFirst(STATIC_ROUTES_HANDLER, StaticRoutesHandler.INSTANCE);
            }

            if (config.isRemoteLocalHostDiscoveryEnabled()) {
                // discover nodes running on the same local computer
                addFirst(LOCAL_HOST_DISCOVERY, new LocalHostDiscovery());
            }

            // discovery nodes on the local network
            if (config.isRemoteLocalNetworkDiscoveryEnabled()) {
                addFirst(LOCAL_NETWORK_DISCOVER, new LocalNetworkDiscovery());
            }

            // discover nodes on the internet
            addFirst(INTERNET_DISCOVERY, new InternetDiscovery(config));

            // outbound message guards
            addFirst(HOP_COUNT_GUARD, HopCountGuard.INSTANCE);

            if (config.isMonitoringEnabled()) {
                addFirst(MONITORING_HANDLER, new Monitoring());
            }

            addFirst(RATE_LIMITER, new RateLimiter());

            // arm outbound and disarm inbound messages
            if (config.isRemoteMessageArmEnabled()) {
                addFirst(ARM_HANDLER, new ArmHandler());
            }

            // filter out inbound messages with invalid proof of work or other network id
            addFirst(INVALID_PROOF_OF_WORK_FILTER, InvalidProofOfWorkFilter.INSTANCE);
            addFirst(OTHER_NETWORK_FILTER, OtherNetworkFilter.INSTANCE);

            // split messages too big for udp
            addFirst(CHUNKING_HANDLER, new ChunkingHandler());

            // convert RemoteEnvelope <-> ByteBuf
            addFirst(REMOTE_ENVELOPE_TO_BYTE_BUF_CODEC, RemoteEnvelopeToByteBufCodec.INSTANCE);

            if (config.isRemoteLocalNetworkDiscoveryEnabled()) {
                addFirst(UDP_MULTICAST_SERVER, udpMulticastServerProvider.get());
            }

            // tcp fallback
            if (config.isRemoteTcpFallbackEnabled()) {
                if (!config.isRemoteSuperPeerEnabled()) {
                    addFirst(TCP_SERVER, tcpServerProvider.get());
                }
                else {
                    addFirst(TCP_CLIENT, tcpClientProvider.get());
                }
            }

            // udp server
            if (config.isRemoteExposeEnabled()) {
                addFirst(PORT_MAPPER, new PortMapper());
            }
            addFirst(UDP_SERVER, udpServerProvider.get());
        }
    }

    DrasylPipeline(final Map<String, AbstractHandlerContext> handlerNames,
                   final AbstractEndHandler head,
                   final AbstractEndHandler tail,
                   final DrasylScheduler dependentScheduler,
                   final DrasylConfig config,
                   final Identity identity,
                   final Semaphore outboundMessagesBuffer) {
        super(
                handlerNames,
                dependentScheduler,
                getInstanceHeavy(),
                config,
                identity,
                new PeersManager(event -> {
                }, identity),
                new Serialization(config.getSerializationSerializers(), config.getSerializationsBindingsInbound()),
                new Serialization(config.getSerializationSerializers(), config.getSerializationsBindingsOutbound()),
                outboundMessagesBuffer);
        this.head = head;
        this.tail = tail;
    }

    public DrasylPipeline(final Consumer<Event> eventConsumer,
                          final DrasylConfig config,
                          final Identity identity,
                          final PeersManager peersManager) {
        this(eventConsumer, config, identity, peersManager, UdpServer::new, UdpMulticastServer::getInstance, TcpServer::new, () -> new TcpClient(config));
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
