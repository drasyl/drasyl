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
package org.drasyl.messenger;

import org.drasyl.event.Event;
import org.drasyl.peer.connection.ConnectionsManager;
import org.drasyl.peer.connection.PeerConnection;
import org.drasyl.peer.connection.message.ApplicationMessage;

import java.util.Optional;
import java.util.function.Consumer;

import static java.util.Optional.ofNullable;

/**
 * The Messenger is responsible for handling the outgoing message flow and sending messages to the
 * recipient.
 */
public class Messenger {
    private final ConnectionsManager connectionsManager;

    Messenger(ConnectionsManager connectionsManager) {
        this.connectionsManager = connectionsManager;
    }

    public Messenger(Consumer<Event> eventConsumer) {
        this(new ConnectionsManager(eventConsumer));
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
