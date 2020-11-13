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
import org.drasyl.identity.Identity;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.util.DrasylFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * This class represents the link between <code>DrasylNode</code> and the super peer. It is
 * responsible for maintaining the connection to the super peer and updates the data of the super
 * peer in {@link PeersManager}.
 */
@SuppressWarnings({ "java:S107", "java:S4818" })
public class SuperPeerClient extends AbstractClient {
    private static final Logger LOG = LoggerFactory.getLogger(SuperPeerClient.class);

    SuperPeerClient(final DrasylConfig config,
                    final EventLoopGroup workerGroup,
                    final Set<Endpoint> endpoints,
                    final AtomicBoolean opened,
                    final BooleanSupplier acceptedNewConnections,
                    final AtomicInteger nextEndpointPointer,
                    final AtomicInteger nextRetryDelayPointer,
                    final DrasylFunction<Endpoint, Bootstrap, ClientException> bootstrapSupplier,
                    final Channel channel) {
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

    protected SuperPeerClient(final DrasylConfig config,
                              final EventLoopGroup workerGroup,
                              final BooleanSupplier doNewConnectionsSupplier,
                              final DrasylFunction<Endpoint, Bootstrap, ClientException> bootstrapSupplier) {
        super(
                config.getSuperPeerRetryDelays(),
                workerGroup,
                config::getSuperPeerEndpoints,
                doNewConnectionsSupplier,
                bootstrapSupplier);
    }

    public SuperPeerClient(final DrasylConfig config,
                           final Identity identity,
                           final PeersManager peersManager,
                           final Pipeline pipeline,
                           final PeerChannelGroup channelGroup,
                           final EventLoopGroup workerGroup,
                           final BooleanSupplier doNewConnectionsSupplier) {
        super(
                config.getSuperPeerRetryDelays(),
                workerGroup,
                config::getSuperPeerEndpoints,
                doNewConnectionsSupplier,
                identity,
                pipeline,
                peersManager,
                config,
                channelGroup,
                config.getSuperPeerHandshakeTimeout(),
                true,
                config.getSuperPeerChannelInitializer()
        );
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}