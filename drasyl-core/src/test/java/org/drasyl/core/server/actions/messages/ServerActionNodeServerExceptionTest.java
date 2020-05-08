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

import org.drasyl.core.common.messages.Response;
import org.drasyl.core.server.NodeServer;
import org.drasyl.core.node.connections.ClientConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ServerActionNodeServerExceptionTest {
    private ClientConnection clientConnection;
    private NodeServer nodeServer;
    private String responseMsgID;

    @BeforeEach
    void setUp() {
        clientConnection = mock(ClientConnection.class);
        nodeServer = mock(NodeServer.class);

        responseMsgID = "id";
    }

    @Test
    void onMessage() {
        ServerActionException message = new ServerActionException();

        message.onMessage(clientConnection, nodeServer);

        verify(clientConnection, times(1)).send(any(Response.class));
        verifyNoInteractions(nodeServer);
    }

    @Test
    void onResponse() {
        ServerActionException message = new ServerActionException();

        message.onResponse(responseMsgID, clientConnection, nodeServer);

        verify(clientConnection, times(1)).setResponse(any(Response.class));
        verifyNoInteractions(nodeServer);
    }
}