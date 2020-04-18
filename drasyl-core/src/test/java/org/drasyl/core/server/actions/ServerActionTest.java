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

package org.drasyl.core.server.actions;

import org.drasyl.core.common.messages.Response;
import org.drasyl.core.server.RelayServer;
import org.drasyl.core.server.session.Session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.Mockito.*;

class ServerActionTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private Session session;
    private RelayServer relay;

    @BeforeEach
    void setUp() {
        session = mock(Session.class);
        relay = mock(RelayServer.class);
    }

    @Test
    void deserializeTest() throws IOException {
        String msg = "{\"type\":\"RelayException\"," +
                "\"messageID\":\"37519A0114C0F3A04588FB53\",\"exception\":\"bla\"}\n";

        ServerAction message = JSON_MAPPER.readValue(msg, ServerAction.class);
        message.onMessage(session, relay);

        verify(session, times(1)).sendMessage(any(Response.class));
    }
}