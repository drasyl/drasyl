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
import org.drasyl.core.common.messages.Status;
import org.drasyl.core.server.NodeServer;
import org.drasyl.core.server.session.ServerSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ServerActionStatusTest {
    private ServerSession serverSession;
    private NodeServer server;
    private String responseMsgID;

    @BeforeEach
    void setUp() {
        serverSession = mock(ServerSession.class);
        server = mock(NodeServer.class);

        responseMsgID = "id";
    }

    @Test
    void onMessage() {
        ServerActionStatus status = new ServerActionStatus();

        status.onMessage(serverSession, server);

        verify(serverSession).send(eq(new Response<>(Status.NOT_IMPLEMENTED, status.getMessageID())));
        verify(serverSession, never()).setResponse(any());
    }

    @Test
    void onResponse() {
        ServerActionStatus status = new ServerActionStatus();

        status.onResponse(responseMsgID, serverSession, server);

        verify(serverSession).setResponse(any(Response.class));
        verify(serverSession, never()).send(any());
    }
}