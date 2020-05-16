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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MessengerTest {
    private Identity sender;
    private Identity recipient;
    private byte[] payload;
    private PeerConnection peerConnection;
    private ConnectionsManager connectionsManager;

    @BeforeEach
    void setUp() {
        sender = mock(Identity.class);
        recipient = mock(Identity.class);
        payload = new byte[]{ 0x4f };
        peerConnection = mock(PeerConnection.class);
        connectionsManager = mock(ConnectionsManager.class);
    }

    @Test
    public void sendShouldHandleMessages() throws DrasylException {
        when(connectionsManager.getConnection(any())).thenReturn(peerConnection);

        ApplicationMessage message = new ApplicationMessage(sender, recipient, payload);
        Messenger messenger = new Messenger(connectionsManager);
        messenger.send(message);

        verify(peerConnection).send(message);
    }
}
