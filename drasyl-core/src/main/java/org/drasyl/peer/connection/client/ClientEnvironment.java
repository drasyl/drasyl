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

import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.pipeline.Pipeline;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * This class encapsulates all information needed by a {@link ClientChannelInitializer}.
 */
public class ClientEnvironment {
    private final DrasylConfig config;
    private final Identity identity;
    private final Endpoint endpoint;
    private final Pipeline pipeline;
    private final PeerChannelGroup channelGroup;
    private final PeersManager peersManager;
    private final boolean joinAsChildren;
    private final Duration handshakeTimeout;

    @SuppressWarnings({ "java:S107" })
    public ClientEnvironment(final DrasylConfig config,
                             final Identity identity,
                             final Endpoint endpoint,
                             final Pipeline pipeline,
                             final PeerChannelGroup channelGroup,
                             final PeersManager peersManager,
                             final boolean joinAsChildren,
                             final Duration handshakeTimeout) {
        this.config = requireNonNull(config);
        this.identity = requireNonNull(identity);
        this.endpoint = requireNonNull(endpoint);
        this.pipeline = requireNonNull(pipeline);
        this.channelGroup = requireNonNull(channelGroup);
        this.peersManager = requireNonNull(peersManager);
        this.joinAsChildren = joinAsChildren;
        this.handshakeTimeout = requireNonNull(handshakeTimeout);
    }

    public DrasylConfig getConfig() {
        return config;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public Identity getIdentity() {
        return identity;
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public PeersManager getPeersManager() {
        return peersManager;
    }

    public boolean joinAsChildren() {
        return joinAsChildren;
    }

    public Duration getHandshakeTimeout() {
        return handshakeTimeout;
    }

    public PeerChannelGroup getChannelGroup() {
        return channelGroup;
    }
}