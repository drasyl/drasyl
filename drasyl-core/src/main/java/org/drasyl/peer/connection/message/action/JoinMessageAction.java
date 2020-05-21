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

package org.drasyl.peer.connection.message.action;

import org.drasyl.identity.Identity;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerConnection;

import static java.util.Objects.requireNonNull;

public class JoinMessageAction extends AbstractMessageAction<JoinMessage> implements ServerMessageAction<JoinMessage> {
    public JoinMessageAction(JoinMessage message) {
        super(message);
    }

    @Override
    public void onMessageServer(NodeServerConnection connection,
                                NodeServer nodeServer) {
        requireNonNull(connection);
        requireNonNull(nodeServer);

        Identity peerIdentity = Identity.of(message.getPublicKey());

        // store peer information
        PeerInformation peerInformation = new PeerInformation();
        peerInformation.setPublicKey(message.getPublicKey());
        peerInformation.addEndpoint(message.getEndpoints());
        nodeServer.getPeersManager().addPeer(peerIdentity, peerInformation);
        nodeServer.getPeersManager().addChildren(peerIdentity);

        connection.send(new WelcomeMessage(nodeServer.getMyIdentity().getKeyPair().getPublicKey(), nodeServer.getEntryPoints(), message.getId()));
    }
}
