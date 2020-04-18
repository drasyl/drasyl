/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.core.server.actions.messages;

import org.drasyl.core.server.RelayServer;
import org.drasyl.core.server.actions.ServerAction;
import org.drasyl.core.server.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ServerActionResponseTest {
    private Session session;
    private RelayServer relay;
    private String responseMsgID;
    private ServerAction msg;

    @BeforeEach
    void setUp() {
        session = mock(Session.class);
        relay = mock(RelayServer.class);
        msg = mock(ServerAction.class);

        responseMsgID = "id";
    }

    @Test
    void onMessage() {
        var message = new ServerActionResponse<>(msg, responseMsgID);

        message.onMessage(session, relay);

        verify(msg, times(1)).onResponse(responseMsgID, session, relay);
    }

    @Test
    void onResponse() {
        var message = new ServerActionResponse();

        message.onResponse(responseMsgID, session, relay);

        verifyNoInteractions(session);
        verifyNoInteractions(relay);
    }
}