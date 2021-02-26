/*
 * Copyright (c) 2020-2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.pipeline;

import io.netty.channel.EventLoopGroup;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.intravm.IntraVmDiscovery;
import org.drasyl.localhost.LocalHostDiscovery;
import org.drasyl.loopback.handler.InboundMessageGuard;
import org.drasyl.loopback.handler.LoopbackMessageHandler;
import org.drasyl.monitoring.Monitoring;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.handler.AddressedEnvelopeHandler;
import org.drasyl.pipeline.serialization.MessageSerializer;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.remote.handler.ArmHandler;
import org.drasyl.remote.handler.ByteBuf2MessageHandler;
import org.drasyl.remote.handler.ChunkingHandler;
import org.drasyl.remote.handler.HopCountGuard;
import org.drasyl.remote.handler.InternetDiscoveryHandler;
import org.drasyl.remote.handler.InvalidProofOfWorkFilter;
import org.drasyl.remote.handler.Message2ByteBufHandler;
import org.drasyl.remote.handler.OtherNetworkFilter;
import org.drasyl.remote.handler.StaticRoutesHandler;
import org.drasyl.remote.handler.UdpServer;
import org.drasyl.remote.handler.portmapper.PortMapper;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.drasyl.util.scheduler.DrasylSchedulerUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.drasyl.intravm.IntraVmDiscovery.INTRA_VM_DISCOVERY;
import static org.drasyl.localhost.LocalHostDiscovery.LOCAL_HOST_DISCOVERY;
import static org.drasyl.loopback.handler.InboundMessageGuard.INBOUND_MESSAGE_GUARD;
import static org.drasyl.loopback.handler.LoopbackMessageHandler.LOOPBACK_MESSAGE_HANDLER;
import static org.drasyl.monitoring.Monitoring.MONITORING_HANDLER;
import static org.drasyl.pipeline.handler.AddressedEnvelopeHandler.ADDRESSED_ENVELOPE_HANDLER;
import static org.drasyl.pipeline.serialization.MessageSerializer.MESSAGE_SERIALIZER;
import static org.drasyl.remote.handler.ArmHandler.ARM_HANDLER;
import static org.drasyl.remote.handler.ByteBuf2MessageHandler.BYTE_BUF_2_MESSAGE_HANDLER;
import static org.drasyl.remote.handler.ChunkingHandler.CHUNKING_HANDLER;
import static org.drasyl.remote.handler.HopCountGuard.HOP_COUNT_GUARD;
import static org.drasyl.remote.handler.InternetDiscoveryHandler.INTERNET_DISCOVERY_HANDLER;
import static org.drasyl.remote.handler.InvalidProofOfWorkFilter.INVALID_PROOF_OF_WORK_FILTER;
import static org.drasyl.remote.handler.Message2ByteBufHandler.MESSAGE_2_BYTE_BUF_HANDLER;
import static org.drasyl.remote.handler.OtherNetworkFilter.OTHER_NETWORK_FILTER;
import static org.drasyl.remote.handler.StaticRoutesHandler.STATIC_ROUTES_HANDLER;
import static org.drasyl.remote.handler.UdpServer.UDP_SERVER;
import static org.drasyl.remote.handler.portmapper.PortMapper.PORT_MAPPER;

/**
 * The default {@link Pipeline} implementation. Used to implement plugins for drasyl.
 */
public class DrasylPipeline extends AbstractPipeline {
    @SuppressWarnings({ "java:S107" })
    public DrasylPipeline(final Consumer<Event> eventConsumer,
                          final DrasylConfig config,
                          final Identity identity,
                          final PeersManager peersManager,
                          final EventLoopGroup bossGroup) {
        this.handlerNames = new ConcurrentHashMap<>();
        this.inboundSerialization = new Serialization(config.getSerializationSerializers(), config.getSerializationsBindingsInbound());
        this.outboundSerialization = new Serialization(config.getSerializationSerializers(), config.getSerializationsBindingsOutbound());
        this.dependentScheduler = DrasylSchedulerUtil.getInstanceLight();
        this.independentScheduler = DrasylSchedulerUtil.getInstanceHeavy();
        this.head = new HeadContext(config, this, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
        this.tail = new TailContext(eventConsumer, config, this, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
        this.config = config;
        this.identity = identity;
        this.peersManager = peersManager;

        initPointer();

        // convert msg <-> AddressedEnvelopeHandler(msg)
        addFirst(ADDRESSED_ENVELOPE_HANDLER, AddressedEnvelopeHandler.INSTANCE);

        // drop messages not addressed to us
        addFirst(INBOUND_MESSAGE_GUARD, new InboundMessageGuard());

        // convert outbound messages addresses to us to inbound messages
        addFirst(LOOPBACK_MESSAGE_HANDLER, new LoopbackMessageHandler());

        // discover nodes running within the same jvm.
        if (config.isIntraVmDiscoveryEnabled()) {
            addFirst(INTRA_VM_DISCOVERY, IntraVmDiscovery.INSTANCE);
        }

        if (config.isRemoteEnabled()) {
            // convert messages from/to byte arrays
            addFirst(MESSAGE_SERIALIZER, MessageSerializer.INSTANCE);

            // route outbound messages to pre-configures ip addresses
            if (!config.getRemoteStaticRoutes().isEmpty()) {
                addFirst(STATIC_ROUTES_HANDLER, StaticRoutesHandler.INSTANCE);
            }

            if (config.isRemoteLocalHostDiscoveryEnabled()) {
                // discover nodes running on the same local computer
                addFirst(LOCAL_HOST_DISCOVERY, new LocalHostDiscovery());
            }

            // register at super peers/discover nodes in other networks
            addFirst(INTERNET_DISCOVERY_HANDLER, new InternetDiscoveryHandler(config));

            // outbound message guards
            addFirst(HOP_COUNT_GUARD, HopCountGuard.INSTANCE);

            if (config.isMonitoringEnabled()) {
                addFirst(MONITORING_HANDLER, new Monitoring());
            }

            // arm (sign/encrypt) outbound and disarm (verify/decrypt) inbound messages
            if (config.isRemoteMessageArmEnabled()) {
                addFirst(ARM_HANDLER, ArmHandler.INSTANCE);
            }

            // filter out inbound messages with invalid proof of work or other network id
            addFirst(INVALID_PROOF_OF_WORK_FILTER, InvalidProofOfWorkFilter.INSTANCE);
            addFirst(OTHER_NETWORK_FILTER, OtherNetworkFilter.INSTANCE);

            // split messages too big for udp
            addFirst(CHUNKING_HANDLER, new ChunkingHandler());

            // udp server
            addFirst(MESSAGE_2_BYTE_BUF_HANDLER, Message2ByteBufHandler.INSTANCE);
            addFirst(BYTE_BUF_2_MESSAGE_HANDLER, ByteBuf2MessageHandler.INSTANCE);
            if (config.isRemoteExposeEnabled()) {
                addFirst(PORT_MAPPER, new PortMapper());
            }
            addFirst(UDP_SERVER, new UdpServer(bossGroup));
        }
    }

    DrasylPipeline(final Map<String, AbstractHandlerContext> handlerNames,
                   final AbstractEndHandler head,
                   final AbstractEndHandler tail,
                   final DrasylScheduler scheduler,
                   final DrasylConfig config,
                   final Identity identity) {
        this.handlerNames = handlerNames;
        this.head = head;
        this.tail = tail;
        this.dependentScheduler = scheduler;
        this.config = config;
        this.identity = identity;
    }
}
