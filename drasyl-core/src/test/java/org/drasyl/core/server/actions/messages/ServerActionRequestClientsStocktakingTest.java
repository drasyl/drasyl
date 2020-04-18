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

import org.drasyl.core.common.messages.ClientsStocktaking;
import org.drasyl.core.common.messages.Response;
import org.drasyl.core.common.models.SessionUID;
import org.drasyl.core.server.RelayServer;
import org.drasyl.core.server.RelayServerConfig;
import org.drasyl.core.server.session.Session;
import org.drasyl.core.server.session.util.ClientSessionBucket;
import com.google.common.collect.Sets;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

import java.net.URISyntaxException;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.*;

class ServerActionRequestClientsStocktakingTest {
    private Session session;
    private RelayServer relay;
    private String responseMsgID;
    private ClientSessionBucket clientSessionBucket;

    @Captor
    private ArgumentCaptor<Response> captor;

    @BeforeEach
    void setUp() throws URISyntaxException {
        MockitoAnnotations.initMocks(this);

        session = mock(Session.class);
        relay = mock(RelayServer.class);
        clientSessionBucket = mock(ClientSessionBucket.class);

        responseMsgID = "id";

        when(relay.getClientBucket()).thenReturn(clientSessionBucket);
        when(relay.getConfig()).thenReturn(new RelayServerConfig(ConfigFactory.load()));
    }

    @Test
    void onMessage() {
        ServerActionRequestClientsStocktaking message = new ServerActionRequestClientsStocktaking();
        Set<SessionUID> clients = Sets.newHashSet(SessionUID.of("junit1"), SessionUID.of("junit2"));

        when(clientSessionBucket.getClientUIDs()).thenReturn(clients);

        message.onMessage(session, relay);

        verify(session).sendMessage(captor.capture());

        ClientsStocktaking asm = (ClientsStocktaking) captor.getValue().getMessage();
        assertThat(asm.getClientUIDs(), containsInAnyOrder(SessionUID.of("junit1"), SessionUID.of("junit2")));
    }

    @Test
    void onResponse() {
        ServerActionRequestClientsStocktaking message = new ServerActionRequestClientsStocktaking();

        message.onResponse(responseMsgID, session, relay);

        verifyNoInteractions(session);
        verifyNoInteractions(relay);
    }
}