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
    private final short idleRetries;
    private final Duration idleTimeout;
    private final Duration handshakeTimeout;

    @SuppressWarnings({ "java:S107" })
    public ClientEnvironment(final DrasylConfig config,
                             final Identity identity,
                             final Endpoint endpoint,
                             final Pipeline pipeline,
                             final PeerChannelGroup channelGroup,
                             final PeersManager peersManager,
                             final boolean joinAsChildren,
                             final short idleRetries,
                             final Duration idleTimeout,
                             final Duration handshakeTimeout) {
        this.config = config;
        this.identity = identity;
        this.endpoint = endpoint;
        this.pipeline = pipeline;
        this.channelGroup = channelGroup;
        this.peersManager = peersManager;
        this.joinAsChildren = joinAsChildren;
        this.idleRetries = idleRetries;
        this.idleTimeout = idleTimeout;
        this.handshakeTimeout = handshakeTimeout;
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

    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    public short getIdleRetries() {
        return idleRetries;
    }

    public PeerChannelGroup getChannelGroup() {
        return channelGroup;
    }
}