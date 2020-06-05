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

import org.drasyl.DrasylException;
import org.drasyl.identity.Address;
import org.drasyl.peer.connection.ConnectionsManager;
import org.drasyl.peer.connection.PeerConnection;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessengerTest {
    private Address sender;
    private Address recipient;
    private byte[] payload;
    private PeerConnection peerConnection;
    private ConnectionsManager connectionsManager;

    @BeforeEach
    void setUp() {
        sender = mock(Address.class);
        recipient = mock(Address.class);
        payload = new byte[]{ 0x4f };
        peerConnection = mock(PeerConnection.class);
        connectionsManager = mock(ConnectionsManager.class);
    }

    @Test
    void sendShouldHandleMessages() throws DrasylException {
        when(connectionsManager.getConnection(any())).thenReturn(peerConnection);

        ApplicationMessage message = new ApplicationMessage(sender, recipient, payload);
        Messenger messenger = new Messenger(connectionsManager);
        messenger.send(message);

        verify(peerConnection).send(message);
    }
}
