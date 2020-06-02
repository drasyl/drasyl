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
import org.drasyl.DrasylException;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.Address;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.connection.AbstractNettyConnection;
import org.drasyl.peer.connection.handler.AbstractThreeWayHandshakeClientHandler;
import org.drasyl.peer.connection.message.*;
import org.drasyl.peer.connection.superpeer.SuperPeerClient;
import org.drasyl.peer.connection.superpeer.SuperPeerClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

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
    private final SuperPeerClient superPeerClient;

    public SuperPeerClientConnectionHandler(String expectedPublicKey,
                                            CompressedPublicKey ownPublicKey,
                                            Set<URI> endpoints,
                                            SuperPeerClient superPeerClient,
                                            Duration timeout) {
        super(superPeerClient.getConnectionsManager(), timeout, new JoinMessage(ownPublicKey, endpoints));
        CompressedPublicKey expectedPublicKey1;
        if (expectedPublicKey == null || expectedPublicKey.equals("")) {
            expectedPublicKey1 = null;
        }
        else {
            try {
                expectedPublicKey1 = CompressedPublicKey.of(expectedPublicKey);
            }
            catch (CryptoException e) {
                LOG.error("", e);
                expectedPublicKey1 = null;
            }
        }
        this.expectedPublicKey = expectedPublicKey1;
        this.ownPublicKey = ownPublicKey;
        this.superPeerClient = superPeerClient;
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
                superPeerClient.getMessenger().send(applicationMessage);
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
        Address address = Address.of(offerMessage.getPublicKey());

        // create peer connection
        SuperPeerClientConnection connection = new SuperPeerClientConnection(ctx.channel(), address, offerMessage.getUserAgent(), superPeerClient.getConnectionsManager());

        // store peer information
        PeerInformation peerInformation = new PeerInformation();
        peerInformation.setPublicKey(offerMessage.getPublicKey());
        peerInformation.addEndpoint(offerMessage.getEndpoints());
        superPeerClient.getPeersManager().addPeer(address, peerInformation);
        superPeerClient.getPeersManager().setSuperPeer(address);

        return connection;
    }
}
