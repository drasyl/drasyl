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

import org.drasyl.messenger.Messenger;
import org.drasyl.peer.connection.ConnectionsManager;
import org.drasyl.peer.connection.PeerConnection;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;
import static org.mockito.Mockito.*;

class QuitMessageActionTest {
    private QuitMessage message;
    private NodeServerConnection clientConnection;
    private NodeServer server;
    private String id;
    private Messenger messenger;
    private ConnectionsManager connectionsManager;
    private PeerConnection.CloseReason reason;

    @BeforeEach
    void setUp() {
        message = mock(QuitMessage.class);
        clientConnection = mock(NodeServerConnection.class);
        server = mock(NodeServer.class);
        id = "id";
        messenger = mock(Messenger.class);
        connectionsManager = mock(ConnectionsManager.class);
        reason = PeerConnection.CloseReason.REASON_SHUTTING_DOWN;

        when(message.getId()).thenReturn(id);
        when(server.getMessenger()).thenReturn(messenger);
        when(messenger.getConnectionsManager()).thenReturn(connectionsManager);
        when(message.getReason()).thenReturn(reason);
    }

    @Test
    void onMessageServerShouldSendStatusOkAndCloseConnection() {
        QuitMessageAction action = new QuitMessageAction(message);

        action.onMessageServer(clientConnection, server);

        verify(clientConnection).send(new StatusMessage(STATUS_OK, message.getId()));
        verify(connectionsManager).closeConnection(clientConnection, reason);
    }
}