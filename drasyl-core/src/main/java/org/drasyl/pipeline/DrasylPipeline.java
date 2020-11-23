/*
 * Copyright (c) 2020.
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

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.monitoring.Monitoring;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.intravm.IntraVmDiscovery;
import org.drasyl.peer.connection.localhost.LocalHostDiscovery;
import org.drasyl.pipeline.codec.ApplicationMessage2ObjectHolderHandler;
import org.drasyl.pipeline.codec.ByteBuf2MessageHandler;
import org.drasyl.pipeline.codec.DefaultCodec;
import org.drasyl.pipeline.codec.Message2ByteBufHandler;
import org.drasyl.pipeline.codec.ObjectHolder2ApplicationMessageHandler;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.DrasylScheduler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.drasyl.monitoring.Monitoring.MONITORING_HANDLER;
import static org.drasyl.peer.connection.intravm.IntraVmDiscovery.INTRA_VM_DISCOVERY;
import static org.drasyl.peer.connection.localhost.LocalHostDiscovery.LOCAL_HOST_DISCOVERY;
import static org.drasyl.pipeline.DirectConnectionInboundMessageSinkHandler.DIRECT_CONNECTION_INBOUND_MESSAGE_SINK_HANDLER;
import static org.drasyl.pipeline.DirectConnectionOutboundMessageSinkHandler.DIRECT_CONNECTION_OUTBOUND_MESSAGE_SINK_HANDLER;
import static org.drasyl.pipeline.HopCountGuard.HOP_COUNT_GUARD;
import static org.drasyl.pipeline.InvalidProofOfWorkFilter.INVALID_PROOF_OF_WORK_FILTER;
import static org.drasyl.pipeline.LoopbackInboundMessageSinkHandler.LOOPBACK_INBOUND_MESSAGE_SINK_HANDLER;
import static org.drasyl.pipeline.LoopbackOutboundMessageSinkHandler.LOOPBACK_OUTBOUND_MESSAGE_SINK_HANDLER;
import static org.drasyl.pipeline.OtherNetworkFilter.OTHER_NETWORK_FILTER;
import static org.drasyl.pipeline.SignatureHandler.SIGNATURE_HANDLER;
import static org.drasyl.pipeline.SuperPeerInboundMessageSinkHandler.SUPER_PEER_INBOUND_MESSAGE_SINK_HANDLER;
import static org.drasyl.pipeline.SuperPeerOutboundMessageSinkHandler.SUPER_PEER_OUTBOUND_MESSAGE_SINK_HANDLER;
import static org.drasyl.pipeline.codec.ApplicationMessage2ObjectHolderHandler.APP_MSG2OBJECT_HOLDER;
import static org.drasyl.pipeline.codec.ByteBuf2MessageHandler.BYTE_BUF_2_MESSAGE_HANDLER;
import static org.drasyl.pipeline.codec.DefaultCodec.DEFAULT_CODEC;
import static org.drasyl.pipeline.codec.Message2ByteBufHandler.MESSAGE_2_BYTE_BUF_HANDLER;
import static org.drasyl.pipeline.codec.ObjectHolder2ApplicationMessageHandler.OBJECT_HOLDER2APP_MSG;

/**
 * The default {@link Pipeline} implementation. Used to implement plugins for drasyl.
 */
public class DrasylPipeline extends DefaultPipeline {
    public DrasylPipeline(final Consumer<Event> eventConsumer,
                          final DrasylConfig config,
                          final Identity identity,
                          final PeerChannelGroup channelGroup,
                          final PeersManager peersManager,
                          final AtomicBoolean started,
                          final Set<Endpoint> endpoints) {
        this.handlerNames = new ConcurrentHashMap<>();
        this.inboundValidator = TypeValidator.ofInboundValidator(config);
        this.outboundValidator = TypeValidator.ofOutboundValidator(config);
        this.head = new HeadContext(config, this, DrasylScheduler.getInstanceHeavy(), identity, peersManager, inboundValidator, outboundValidator);
        this.tail = new TailContext(eventConsumer, config, this, DrasylScheduler.getInstanceHeavy(), identity, peersManager, inboundValidator, outboundValidator);
        this.scheduler = DrasylScheduler.getInstanceLight();
        this.config = config;
        this.identity = identity;
        this.peersManager = peersManager;

        initPointer();

        // add default codec
        addFirst(DEFAULT_CODEC, DefaultCodec.INSTANCE);
        addFirst(APP_MSG2OBJECT_HOLDER, ApplicationMessage2ObjectHolderHandler.INSTANCE);
        addFirst(OBJECT_HOLDER2APP_MSG, ObjectHolder2ApplicationMessageHandler.INSTANCE);

        // outbound message guards
        addFirst(HOP_COUNT_GUARD, HopCountGuard.INSTANCE);

        // local message delivery
        addFirst(LOOPBACK_INBOUND_MESSAGE_SINK_HANDLER, new LoopbackInboundMessageSinkHandler(started, endpoints));
        addFirst(LOOPBACK_OUTBOUND_MESSAGE_SINK_HANDLER, LoopbackOutboundMessageSinkHandler.INSTANCE);

        if (config.isLocalHostDiscoveryEnabled()) {
            addFirst(LOCAL_HOST_DISCOVERY, new LocalHostDiscovery(config, identity.getPublicKey(), endpoints));
        }

        // we trust peers within the same jvm. therefore we do not use signatures
        if (config.isIntraVmDiscoveryEnabled()) {
            addFirst(INTRA_VM_DISCOVERY, IntraVmDiscovery.INSTANCE);
        }

        if (config.isMonitoringEnabled()) {
            addFirst(MONITORING_HANDLER, new Monitoring());
        }

        addFirst(SIGNATURE_HANDLER, SignatureHandler.INSTANCE);

        // remote message delivery
        addFirst(SUPER_PEER_INBOUND_MESSAGE_SINK_HANDLER, new SuperPeerInboundMessageSinkHandler(channelGroup));
        addFirst(DIRECT_CONNECTION_INBOUND_MESSAGE_SINK_HANDLER, new DirectConnectionInboundMessageSinkHandler(channelGroup));
        addFirst(DIRECT_CONNECTION_OUTBOUND_MESSAGE_SINK_HANDLER, new DirectConnectionOutboundMessageSinkHandler(channelGroup));
        addFirst(SUPER_PEER_OUTBOUND_MESSAGE_SINK_HANDLER, new SuperPeerOutboundMessageSinkHandler(channelGroup));

        // inbound message guards
        addFirst(INVALID_PROOF_OF_WORK_FILTER, InvalidProofOfWorkFilter.INSTANCE);
        addFirst(OTHER_NETWORK_FILTER, OtherNetworkFilter.INSTANCE);

        // (de)serialize messages
        addFirst(MESSAGE_2_BYTE_BUF_HANDLER, Message2ByteBufHandler.INSTANCE);
        addFirst(BYTE_BUF_2_MESSAGE_HANDLER, ByteBuf2MessageHandler.INSTANCE);
    }

    DrasylPipeline(final Map<String, AbstractHandlerContext> handlerNames,
                   final AbstractEndHandler head,
                   final AbstractEndHandler tail,
                   final Scheduler scheduler,
                   final DrasylConfig config,
                   final Identity identity) {
        this.handlerNames = handlerNames;
        this.head = head;
        this.tail = tail;
        this.scheduler = scheduler;
        this.config = config;
        this.identity = identity;
    }
}