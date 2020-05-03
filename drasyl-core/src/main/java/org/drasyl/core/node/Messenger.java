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

package org.drasyl.core.node;

import org.drasyl.core.common.messages.Message;
import org.drasyl.core.models.Code;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.models.Event;
import org.drasyl.core.node.identity.IdentityManager;

import java.util.Optional;

public class Messenger {
    private final IdentityManager identityManager;
    private final PeersManager peersManager;

    public Messenger(IdentityManager identityManager,
                     PeersManager peersManager) {
        this.identityManager = identityManager;
        this.peersManager = peersManager;
    }

    public void send(Message message) throws DrasylException {
        try {
            sendToClient(message);
        }
        catch (ClientNotFoundException e) {
            try {
                sendToSuperPeer(message);
            }
            catch (NoSuperPeerException ex) {
                throw new DrasylException("Unable to send message: " + message.toString());
            }
        }
    }

    private void sendToClient(Message message) throws ClientNotFoundException {
        Optional<PeerInformation> peerInformation = Optional.ofNullable(peersManager.getPeer(message.getRecipient()));

        if(peerInformation.isPresent()) {
            peerInformation.get().getConnections().iterator().next().send(message);
        } else {
            throw new ClientNotFoundException("Can't found client: '" + message.getRecipient() + "'");
        }
    }

    private void sendToSuperPeer(Message message) throws NoSuperPeerException {
        // FIXME: implement
    }
}
