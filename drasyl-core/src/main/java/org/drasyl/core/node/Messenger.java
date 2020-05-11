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
import org.drasyl.core.common.models.Pair;
import org.drasyl.core.models.Code;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.models.Event;
import org.drasyl.core.node.connections.PeerConnection;
import org.drasyl.core.node.identity.IdentityManager;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;

import static java.util.Optional.ofNullable;

/**
 * The Messenger is responsible for sending messages to the recipient. Depending on the recipient,
 * the message is sent to the application, a client or the super peer.
 */
public class Messenger {
    private final IdentityManager identityManager;
    private final Consumer<Event> onEvent;
    private final ConnectionsManager connectionsManager;

    public Messenger(IdentityManager identityManager,
                     Consumer<Event> onEvent,
                     ConnectionsManager connectionsManager) {
        this.identityManager = identityManager;
        this.onEvent = onEvent;
        this.connectionsManager = connectionsManager;
    }

    public Messenger(IdentityManager identityManager, Consumer<Event> onEvent) {
        this(identityManager, onEvent, new ConnectionsManager());
    }

    public ConnectionsManager getConnectionsManager() {
        return connectionsManager;
    }

    public void send(ApplicationMessage message) throws DrasylException {
        // TODO: Use PeerConnection for sending to our node. Then we would have a common interface
        //  for sending to our Peer, Clients and the Super Peer
        if (identityManager.getIdentity().equals(message.getRecipient())) {
            // Our node is the receiver, create message event
            onEvent.accept(new Event(Code.MESSAGE, Pair.of(message.getSender(), message.getPayload())));
        }
        else {
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
    }

    private void sendToClient(ApplicationMessage message) throws ClientNotFoundException {
        Optional<PeerConnection> connection = ofNullable(this.connectionsManager.getConnection(message.getRecipient()));

        if (connection.isPresent()) {
            connection.get().send(message);
        }
        else {
            throw new ClientNotFoundException("Can't found client: '" + message.getRecipient() + "'");
        }
    }

    private void sendToSuperPeer(ApplicationMessage message) throws NoSuperPeerException {
        Optional<PeerConnection> connection = ofNullable(this.connectionsManager.getConnection(message.getRecipient()));

        if (connection.isPresent()) {
            connection.get().send(message);
        }
        else {
            throw new NoSuperPeerException();
        }
    }
}
