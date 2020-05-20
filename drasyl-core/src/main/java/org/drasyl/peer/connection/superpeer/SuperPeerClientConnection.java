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
package org.drasyl.peer.connection.superpeer;

import io.netty.channel.Channel;
import io.reactivex.rxjava3.core.SingleEmitter;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.AbstractNettyConnection;
import org.drasyl.peer.connection.ConnectionsManager;
import org.drasyl.peer.connection.message.ResponseMessage;
import org.drasyl.peer.connection.server.NodeServerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings({ "java:S2160" })
public class SuperPeerClientConnection extends AbstractNettyConnection {
    private static final Logger LOG = LoggerFactory.getLogger(SuperPeerClientConnection.class);

    /**
     * Creates a new connection with an unknown User-Agent.
     *
     * @param channel            channel of the connection
     * @param endpoint           the URI of the target system
     * @param identity           the identity of this {@link NodeServerConnection}
     * @param connectionsManager reference to the {@link ConnectionsManager}
     */
    public SuperPeerClientConnection(Channel channel,
                                     URI endpoint,
                                     Identity identity, ConnectionsManager connectionsManager) {
        super(channel, endpoint, identity, connectionsManager);
    }

    /**
     * Creates a new connection.
     *
     * @param channel            channel of the connection
     * @param endpoint           the URI of the target system
     * @param identity           the identity of this {@link NodeServerConnection}
     * @param userAgent          the User-Agent string
     * @param connectionsManager reference to the {@link ConnectionsManager}
     */
    public SuperPeerClientConnection(Channel channel,
                                     URI endpoint,
                                     Identity identity,
                                     String userAgent, ConnectionsManager connectionsManager) {
        super(channel, endpoint, identity, userAgent, connectionsManager);
    }

    public SuperPeerClientConnection(Channel myChannel,
                                     String userAgent,
                                     Identity identity,
                                     URI endpoint,
                                     AtomicBoolean isClosed,
                                     ConcurrentHashMap<String, SingleEmitter<ResponseMessage<?, ?>>> emitters,
                                     CompletableFuture<Boolean> closedCompletable,
                                     ConnectionsManager connectionsManager) {
        super(myChannel, userAgent, identity, endpoint, isClosed, emitters, closedCompletable, connectionsManager);
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}
