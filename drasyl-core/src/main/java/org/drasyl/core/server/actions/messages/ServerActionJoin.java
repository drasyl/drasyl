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
package org.drasyl.core.server.actions.messages;

import org.drasyl.core.common.messages.Join;
import org.drasyl.core.common.messages.Response;
import org.drasyl.core.common.messages.Welcome;
import org.drasyl.core.models.CompressedPublicKey;
import org.drasyl.core.node.PeerInformation;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.server.NodeServer;
import org.drasyl.core.server.actions.ServerAction;
import org.drasyl.core.server.session.ServerSession;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

public class ServerActionJoin extends Join implements ServerAction {
    ServerActionJoin() {
        super();
    }

    ServerActionJoin(CompressedPublicKey publicKey, Set<URI> endpoints) {
        super(publicKey, endpoints);
    }

    @Override
    public void onMessage(ServerSession session, NodeServer nodeServer) {
        Objects.requireNonNull(session);
        Objects.requireNonNull(nodeServer);

        Identity peerIdentity = Identity.of(this.getPublicKey());
        PeerInformation peerInformation = new PeerInformation();
        peerInformation.setPublicKey(this.getPublicKey());
        peerInformation.addPeerConnection(session);
        peerInformation.addEndpoint(this.getEndpoints());
        nodeServer.getPeersManager().addPeer(peerIdentity, peerInformation);
        nodeServer.getPeersManager().addChildren(peerIdentity);

        session.send(new Response<>(new Welcome(nodeServer.getMyIdentity().getKeyPair().getPublicKey(), nodeServer.getEntryPoints()), this.getMessageID()));
    }

    @Override
    public void onResponse(String responseMsgID, ServerSession session, NodeServer nodeServer) {
        // This message does not come as response to the node server
    }
}
