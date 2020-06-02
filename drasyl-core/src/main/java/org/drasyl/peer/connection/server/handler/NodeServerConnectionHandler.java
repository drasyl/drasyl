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
import org.drasyl.DrasylException;
import org.drasyl.identity.Address;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.connection.AbstractNettyConnection;
import org.drasyl.peer.connection.ConnectionsManager;
import org.drasyl.peer.connection.handler.AbstractThreeWayHandshakeServerHandler;
import org.drasyl.peer.connection.message.*;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_SAME_PUBLIC_KEY;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_NOT_FOUND;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;

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
    private final CompressedPublicKey publicKey;
    private final NodeServer server;

    public NodeServerConnectionHandler(CompressedPublicKey publicKey,
                                       Duration timeout,
                                       NodeServer server) {
        super(server.getConnectionsManager(), timeout);
        this.publicKey = publicKey;
        this.server = server;
    }

    NodeServerConnectionHandler(ConnectionsManager connectionsManager,
                                Duration timeout,
                                CompletableFuture<Void> handshakeFuture,
                                AbstractNettyConnection connection,
                                ScheduledFuture<?> timeoutFuture,
                                JoinMessage requestMessage,
                                CompressedPublicKey publicKey,
                                NodeServer server) {
        super(connectionsManager, timeout, handshakeFuture, connection, timeoutFuture, requestMessage);
        this.publicKey = publicKey;
        this.server = server;
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    @Override
    protected void processMessageAfterHandshake(AbstractNettyConnection connection,
                                                Message message) {
        if (message instanceof ApplicationMessage) {
            ApplicationMessage applicationMessage = (ApplicationMessage) message;
            try {
                server.getMessenger().send(applicationMessage);
                connection.send(new StatusMessage(STATUS_OK, applicationMessage.getId()));
            }
            catch (DrasylException e) {
                connection.send(new StatusMessage(STATUS_NOT_FOUND, applicationMessage.getId()));
            }
        }
        else {
            LOG.debug("Could not process the message {}", message);
        }
    }

    @Override
    protected ConnectionExceptionMessage.Error validateSessionRequest(JoinMessage requestMessage) {
        CompressedPublicKey clientPublicKey = requestMessage.getPublicKey();

        if (publicKey.equals(clientPublicKey)) {
            return CONNECTION_ERROR_SAME_PUBLIC_KEY;
        }
        else {
            return null;
        }
    }

    @Override
    protected WelcomeMessage offerSession(ChannelHandlerContext ctx,
                                          JoinMessage requestMessage) {
        return new WelcomeMessage(server.getIdentityManager().getIdentity().getPublicKey(), server.getEntryPoints(), requestMessage.getId());
    }

    @Override
    protected AbstractNettyConnection createConnection(ChannelHandlerContext ctx,
                                                       JoinMessage requestMessage) {
        Address address = Address.of(requestMessage.getPublicKey());

        // create peer connection
        NodeServerConnection connection = new NodeServerConnection(ctx.channel(), address,
                Optional.ofNullable(requestMessage.getUserAgent()).orElse("U/A"), server.getConnectionsManager());

        // store peer information
        PeerInformation peerInformation = new PeerInformation();
        peerInformation.setPublicKey(requestMessage.getPublicKey());
        peerInformation.addEndpoint(requestMessage.getEndpoints());
        server.getPeersManager().addPeer(address, peerInformation);
        server.getPeersManager().addChildren(address);

        return connection;
    }
}
