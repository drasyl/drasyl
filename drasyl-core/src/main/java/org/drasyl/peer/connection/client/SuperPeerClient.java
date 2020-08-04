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
package org.drasyl.peer.connection.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.util.DrasylFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * This class represents the link between <code>DrasylNode</code> and the super peer. It is
 * responsible for maintaining the connection to the super peer and updates the data of the super
 * peer in {@link PeersManager}.
 */
@SuppressWarnings({ "java:S107", "java:S4818" })
public class SuperPeerClient extends AbstractClient {
    private static final Logger LOG = LoggerFactory.getLogger(SuperPeerClient.class);

    SuperPeerClient(DrasylConfig config,
                    EventLoopGroup workerGroup,
                    Set<URI> endpoints,
                    AtomicBoolean opened,
                    BooleanSupplier acceptedNewConnections,
                    AtomicInteger nextEndpointPointer,
                    AtomicInteger nextRetryDelayPointer,
                    DrasylFunction<URI, Bootstrap, ClientException> bootstrapSupplier,
                    Channel channel) {
        super(
                config.getSuperPeerRetryDelays(),
                workerGroup,
                () -> endpoints,
                opened,
                acceptedNewConnections,
                nextEndpointPointer,
                nextRetryDelayPointer,
                bootstrapSupplier,
                channel
        );
    }

    protected SuperPeerClient(DrasylConfig config,
                              EventLoopGroup workerGroup,
                              BooleanSupplier acceptNewConnectionsSupplier,
                              DrasylFunction<URI, Bootstrap, ClientException> bootstrapSupplier) {
        super(
                config.getSuperPeerRetryDelays(),
                workerGroup,
                config::getSuperPeerEndpoints,
                acceptNewConnectionsSupplier,
                bootstrapSupplier);
    }

    public SuperPeerClient(DrasylConfig config,
                           Identity identity,
                           PeersManager peersManager,
                           Messenger messenger,
                           PeerChannelGroup channelGroup,
                           EventLoopGroup workerGroup,
                           Consumer<Event> eventConsumer,
                           BooleanSupplier acceptNewConnectionsSupplier) {
        super(
                config.getSuperPeerRetryDelays(),
                workerGroup,
                config::getSuperPeerEndpoints,
                acceptNewConnectionsSupplier,
                identity,
                messenger,
                peersManager,
                config,
                channelGroup,
                config.getSuperPeerIdleRetries(),
                config.getSuperPeerIdleTimeout(),
                config.getSuperPeerHandshakeTimeout(),
                eventConsumer,
                true,
                config.getSuperPeerPublicKey(),
                config.getSuperPeerChannelInitializer()
        );
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}