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

import org.drasyl.core.common.message.ApplicationMessage;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.node.connections.PeerConnection;
import org.drasyl.core.node.identity.Identity;

import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * The Messenger is responsible for sending messages to the recipient. Depending on the recipient,
 * the message is sent to the application, a client or the super peer.
 */
public class Messenger {
    private final ConnectionsManager connectionsManager;

    public Messenger(ConnectionsManager connectionsManager) {
        this.connectionsManager = connectionsManager;
    }

    public Messenger() {
        this(new ConnectionsManager());
    }

    public ConnectionsManager getConnectionsManager() {
        return connectionsManager;
    }

    public void send(ApplicationMessage message) throws MessengerException {
        Optional<PeerConnection> connection = ofNullable(this.connectionsManager.getConnection(message.getRecipient()));

        if (connection.isPresent()) {
            connection.get().send(message);
        }
        else {
            throw new MessengerException("Unable to send '" + message.toString() + "': Neither Connection to Recipient nor Super Peer available");
        }
    }
}
