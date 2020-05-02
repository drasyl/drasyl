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

import com.google.common.collect.Sets;
import com.typesafe.config.ConfigFactory;
import org.drasyl.core.common.messages.ClientsStocktaking;
import org.drasyl.core.common.messages.Response;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.node.DrasylNodeConfig;
import org.drasyl.core.node.PeersManager;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.node.identity.IdentityTestHelper;
import org.drasyl.core.server.NodeServer;
import org.drasyl.core.server.session.ServerSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.*;

class ServerActionRequestClientsStocktakingTest {
    private ServerSession serverSession;
    private NodeServer server;
    private String responseMsgID;
    private PeersManager peersManager;
    @Captor
    private ArgumentCaptor<Response<ClientsStocktaking>> captor;

    @BeforeEach
    void setUp() throws DrasylException {
        MockitoAnnotations.initMocks(this);

        serverSession = mock(ServerSession.class);
        server = mock(NodeServer.class);
        peersManager = mock(PeersManager.class);

        responseMsgID = "id";

        when(server.getPeersManager()).thenReturn(peersManager);
        when(server.getConfig()).thenReturn(new DrasylNodeConfig(ConfigFactory.load()));
    }

    @Test
    void onMessage() {
        Identity identity1 = IdentityTestHelper.random();
        Identity identity2 = IdentityTestHelper.random();

        ServerActionRequestClientsStocktaking message = new ServerActionRequestClientsStocktaking();
        Set<Identity> clients = Sets.newHashSet(identity1, identity2);

        when(peersManager.getChildren()).thenReturn(clients);

        message.onMessage(serverSession, server);

        verify(serverSession).send(captor.capture());

        ClientsStocktaking asm = captor.getValue().getMessage();
        assertThat(asm.getIdentities(), containsInAnyOrder(identity1, identity2));
    }

    @Test
    void onResponse() {
        ServerActionRequestClientsStocktaking message = new ServerActionRequestClientsStocktaking();

        message.onResponse(responseMsgID, serverSession, server);

        verifyNoInteractions(serverSession);
        verifyNoInteractions(server);
    }
}