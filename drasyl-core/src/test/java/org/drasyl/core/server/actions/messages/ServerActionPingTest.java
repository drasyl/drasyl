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

import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ServerActionPingTest {
    private ClientConnection clientConnection;
    private NodeServer server;
    private String responseMsgID;

    @BeforeEach
    void setUp() {
        clientConnection = mock(ClientConnection.class);
        server = mock(NodeServer.class);

        responseMsgID = "id";
    }

    @Test
    void onMessage() {
        ServerActionPing message = new ServerActionPing();

        message.onMessage(clientConnection, server);

        verifyNoInteractions(clientConnection);
        verifyNoInteractions(server);
    }

    @Test
    void onResponse() {
        ServerActionPing message = new ServerActionPing();

        message.onResponse(responseMsgID, clientConnection, server);

        verifyNoInteractions(clientConnection);
        verifyNoInteractions(server);
    }
}