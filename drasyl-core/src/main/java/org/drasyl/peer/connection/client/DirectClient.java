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

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Used by {@link org.drasyl.peer.connection.direct.DirectConnectionsManager} to establish a direct
 * connection to another peer.
 */
@SuppressWarnings({ "java:S107" })
public class DirectClient extends AbstractClient {
    private static final Logger LOG = LoggerFactory.getLogger(DirectClient.class);
    private final BooleanSupplier directConnectionDemand;
    private final Runnable onFailure;

    public DirectClient(final DrasylConfig config,
                        final Identity identity,
                        final PeersManager peersManager,
                        final Pipeline pipeline,
                        final PeerChannelGroup channelGroup,
                        final EventLoopGroup workerGroup,
                        final Supplier<Set<Endpoint>> endpointsSupplier,
                        final BooleanSupplier directConnectionDemand,
                        final Runnable onFailure,
                        final BooleanSupplier acceptNewConnectionsSupplier) {
        super(
                config.getDirectConnectionsRetryDelays(),
                workerGroup,
                endpointsSupplier,
                acceptNewConnectionsSupplier,
                identity,
                pipeline,
                peersManager,
                config,
                channelGroup,
                config.getDirectConnectionsIdleRetries(),
                config.getDirectConnectionsIdleTimeout(),
                config.getDirectConnectionsHandshakeTimeout(),
                false,
                config.getDirectConnectionsChannelInitializer()
        );
        this.directConnectionDemand = directConnectionDemand;
        this.onFailure = onFailure;
    }

    DirectClient(final List<Duration> retryDelays,
                 final EventLoopGroup workerGroup,
                 final Supplier<Set<Endpoint>> endpointsSupplier,
                 final AtomicBoolean opened,
                 final BooleanSupplier acceptNewConnectionsSupplier,
                 final AtomicInteger nextEndpointPointer,
                 final AtomicInteger nextRetryDelayPointer,
                 final DrasylFunction<Endpoint, Bootstrap, ClientException> bootstrapSupplier,
                 final Channel channel,
                 final BooleanSupplier directConnectionDemand,
                 final Runnable onFailure) {
        super(retryDelays, workerGroup, endpointsSupplier, opened, acceptNewConnectionsSupplier, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, channel);
        this.directConnectionDemand = directConnectionDemand;
        this.onFailure = onFailure;
    }

    /**
     * Should only make a new connection attempt if there is still a demand for it.
     *
     * @return if a retry should be made
     */
    @Override
    protected boolean shouldRetry() {
        return directConnectionDemand.getAsBoolean() && super.shouldRetry();
    }

    /**
     * Call <code>onFailure</code> if the client can permanently not establish a connection
     * anymore.
     */
    @Override
    protected void failed() {
        super.failed();

        getLogger().debug("Permanently unable to connect to peer. Close and remove this client.");

        close();
        onFailure.run();
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}