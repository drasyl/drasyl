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
package org.drasyl.peer.connection.server;

import io.netty.channel.Channel;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.AbstractNettyConnection;
import org.drasyl.peer.connection.ConnectionsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The {@link NodeServerConnection} object models the clients of a drasyl node server.
 */
@SuppressWarnings({ "squid:S00107", "java:S2160" })
public class NodeServerConnection extends AbstractNettyConnection {
    private static final Logger LOG = LoggerFactory.getLogger(NodeServerConnection.class);

    /**
     * Creates a new connection with an unknown User-Agent.
     *
     * @param channel            channel of the connection
     * @param identity           the identity of this {@link NodeServerConnection}
     * @param connectionsManager reference to the {@link ConnectionsManager}
     */
    public NodeServerConnection(Channel channel,
                                Identity identity,
                                ConnectionsManager connectionsManager) {
        super(channel, identity, connectionsManager);
    }

    /**
     * Creates a new connection.
     *
     * @param channel            channel of the connection
     * @param identity           the identity of this {@link NodeServerConnection}
     * @param userAgent          the User-Agent string
     * @param connectionsManager reference to the {@link ConnectionsManager}
     */
    public NodeServerConnection(Channel channel,
                                Identity identity,
                                String userAgent,
                                ConnectionsManager connectionsManager) {
        super(channel, identity, userAgent, connectionsManager);
    }

    protected NodeServerConnection(Channel myChannel,
                                   String userAgent,
                                   Identity identity,
                                   AtomicBoolean isClosed,
                                   CompletableFuture<Boolean> closedCompletable,
                                   ConnectionsManager connectionsManager) {
        super(myChannel, userAgent, identity, isClosed, closedCompletable, connectionsManager);
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}
