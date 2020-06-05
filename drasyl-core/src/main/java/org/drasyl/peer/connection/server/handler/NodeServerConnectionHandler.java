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
package org.drasyl.peer.connection.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.AbstractNettyConnection;
import org.drasyl.peer.connection.ConnectionsManager;
import org.drasyl.peer.connection.handler.AbstractThreeWayHandshakeServerHandler;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.drasyl.peer.connection.server.NodeServerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_SAME_PUBLIC_KEY;

/**
 * Acts as a guard for in- and outbound connections. A channel is only created, when a {@link
 * JoinMessage} was received. Outgoing messages are dropped unless a {@link JoinMessage} was
 * received. Every other incoming message is also dropped unless a {@link JoinMessage} was
 * received.
 * <p>
 * If a {@link JoinMessage} was not received in {@link DrasylNodeConfig#getServerHandshakeTimeout()}
 * the connection will be closed.
 * <p>
 * This handler closes the channel if an exception occurs before a {@link JoinMessage} has been
 * received.
 */
@SuppressWarnings({ "java:S107" })
public class NodeServerConnectionHandler extends AbstractThreeWayHandshakeServerHandler<JoinMessage, WelcomeMessage> {
    public static final String NODE_SERVER_CONNECTION_HANDLER = "nodeServerConnectionHandler";
    private static final Logger LOG = LoggerFactory.getLogger(NodeServerConnectionHandler.class);
    private final PeersManager peersManager;
    private final Set<URI> entryPoints;
    private final Identity identity;

    public NodeServerConnectionHandler(Identity identity,
                                       PeersManager peersManager,
                                       ConnectionsManager connectionsManager,
                                       Messenger messenger,
                                       Set<URI> entryPoints, Duration timeout) {
        super(connectionsManager, timeout, messenger);
        this.peersManager = peersManager;
        this.entryPoints = entryPoints;
        this.identity = identity;
    }

    NodeServerConnectionHandler(Identity identity,
                                PeersManager peersManager,
                                ConnectionsManager connectionsManager,
                                Messenger messenger,
                                Set<URI> entryPoints,
                                Duration timeout,
                                CompletableFuture<Void> handshakeFuture,
                                AbstractNettyConnection connection,
                                ScheduledFuture<?> timeoutFuture,
                                JoinMessage requestMessage) {
        super(connectionsManager, timeout, messenger, handshakeFuture, connection, timeoutFuture, requestMessage);
        this.peersManager = peersManager;
        this.entryPoints = entryPoints;
        this.identity = identity;
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    @Override
    protected ConnectionExceptionMessage.Error validateSessionRequest(JoinMessage requestMessage) {
        CompressedPublicKey clientPublicKey = requestMessage.getPublicKey();

        if (identity.getPublicKey().equals(clientPublicKey)) {
            return CONNECTION_ERROR_SAME_PUBLIC_KEY;
        }
        else {
            return null;
        }
    }

    @Override
    protected WelcomeMessage offerSession(ChannelHandlerContext ctx,
                                          JoinMessage requestMessage) {
        return new WelcomeMessage(identity.getPublicKey(), entryPoints, requestMessage.getId());
    }

    @Override
    protected AbstractNettyConnection createConnection(ChannelHandlerContext ctx,
                                                       JoinMessage requestMessage) {
        Identity identity = Identity.of(requestMessage.getPublicKey());

        // create peer connection
        NodeServerConnection connection = new NodeServerConnection(ctx.channel(), identity,
                Optional.ofNullable(requestMessage.getUserAgent()).orElse("U/A"), connectionsManager);

        // store peer information
        PeerInformation peerInformation = new PeerInformation();
        peerInformation.setPublicKey(requestMessage.getPublicKey());
        peerInformation.addEndpoint(requestMessage.getEndpoints());
        peersManager.addPeer(identity, peerInformation);
        peersManager.addChildren(identity);

        return connection;
    }
}
