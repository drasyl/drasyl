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
package org.drasyl.peer.connection.superpeer.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.DrasylException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.AbstractNettyConnection;
import org.drasyl.peer.connection.ConnectionsManager;
import org.drasyl.peer.connection.handler.AbstractThreeWayHandshakeClientHandler;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.StatusMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.drasyl.peer.connection.superpeer.SuperPeerClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_WRONG_PUBLIC_KEY;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_NOT_FOUND;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;

/**
 * This handler performs the handshake with the server and processes incoming messages during the
 * session.
 * <p>
 * The handshake is initiated by a {@link JoinMessage} sent by the client, which is answered with a
 * {@link WelcomeMessage} from the server. The client must then confirm this message with a {@link
 * StatusMessage}.
 */
public class SuperPeerClientConnectionHandler extends AbstractThreeWayHandshakeClientHandler<JoinMessage, WelcomeMessage> {
    public static final String SUPER_PEER_CLIENT_CONNECTION_HANDLER = "superPeerClientConnectionHandler";
    private static final Logger LOG = LoggerFactory.getLogger(SuperPeerClientConnectionHandler.class);
    private final CompressedPublicKey expectedPublicKey;
    private final CompressedPublicKey ownPublicKey;
    private final PeersManager peersManager;
    private final ConnectionsManager connectionsManager;
    private final Messenger messenger;

    public SuperPeerClientConnectionHandler(CompressedPublicKey expectedPublicKey,
                                            CompressedPublicKey ownPublicKey,
                                            Set<URI> endpoints,
                                            Duration timeout,
                                            PeersManager peersManager,
                                            ConnectionsManager connectionsManager,
                                            Messenger messenger) {
        super(connectionsManager, timeout, new JoinMessage(ownPublicKey, endpoints));
        this.expectedPublicKey = expectedPublicKey;
        this.ownPublicKey = ownPublicKey;
        this.peersManager = peersManager;
        this.connectionsManager = connectionsManager;
        this.messenger = messenger;
    }

    SuperPeerClientConnectionHandler(CompressedPublicKey expectedPublicKey,
                                     CompressedPublicKey ownPublicKey,
                                     PeersManager peersManager,
                                     ConnectionsManager connectionsManager,
                                     Messenger messenger,
                                     Duration timeout,
                                     CompletableFuture<Void> handshakeFuture,
                                     AbstractNettyConnection connection,
                                     ScheduledFuture<?> timeoutFuture,
                                     JoinMessage requestMessage) {
        super(connectionsManager, timeout, handshakeFuture, connection, timeoutFuture, requestMessage);
        this.expectedPublicKey = expectedPublicKey;
        this.ownPublicKey = ownPublicKey;
        this.peersManager = peersManager;
        this.connectionsManager = connectionsManager;
        this.messenger = messenger;
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
                messenger.send(applicationMessage);
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
    protected ConnectionExceptionMessage.Error validateSessionOffer(WelcomeMessage offerMessage) {
        CompressedPublicKey superPeerPublicKey = offerMessage.getPublicKey();
        if (expectedPublicKey != null && !superPeerPublicKey.equals(expectedPublicKey)) {
            return CONNECTION_ERROR_WRONG_PUBLIC_KEY;
        }
        else if (superPeerPublicKey.equals(ownPublicKey)) {
            return CONNECTION_ERROR_WRONG_PUBLIC_KEY;
        }
        else {
            return null;
        }
    }

    @Override
    protected AbstractNettyConnection createConnection(final ChannelHandlerContext ctx,
                                                       WelcomeMessage offerMessage) {
        Identity identity = Identity.of(offerMessage.getPublicKey());

        // create peer connection
        SuperPeerClientConnection connection = new SuperPeerClientConnection(ctx.channel(), identity, offerMessage.getUserAgent(), connectionsManager);

        // store peer information
        PeerInformation peerInformation = new PeerInformation();
        peerInformation.setPublicKey(offerMessage.getPublicKey());
        peerInformation.addEndpoint(offerMessage.getEndpoints());
        peersManager.addPeer(identity, peerInformation);
        peersManager.setSuperPeer(identity);

        return connection;
    }
}
