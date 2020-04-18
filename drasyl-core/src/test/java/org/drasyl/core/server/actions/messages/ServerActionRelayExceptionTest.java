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

import org.drasyl.core.common.messages.RelayException;
import org.drasyl.core.common.messages.Response;
import org.drasyl.core.server.RelayServer;
import org.drasyl.core.server.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ServerActionRelayExceptionTest {
    private Session session;
    private RelayServer relay;
    private String responseMsgID;

    @BeforeEach
    void setUp() {
        session = mock(Session.class);
        relay = mock(RelayServer.class);

        responseMsgID = "id";
    }

    @Test
    void onMessage() {
        ServerActionException message = new ServerActionException();

        message.onMessage(session, relay);

        verify(session, times(1)).sendMessage(any(Response.class));
        verifyNoInteractions(relay);
    }

    @Test
    void onResponse() {
        ServerActionException message = new ServerActionException();

        message.onResponse(responseMsgID, session, relay);

        verify(session, times(1)).setResult(eq(responseMsgID), any(RelayException.class));
        verifyNoInteractions(relay);
    }
}