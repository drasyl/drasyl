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

import org.drasyl.core.common.messages.Response;
import org.drasyl.core.common.models.IPAddress;
import org.drasyl.core.common.models.SessionChannel;
import org.drasyl.core.common.models.SessionUID;
import org.drasyl.core.server.RelayServer;
import org.drasyl.core.server.RelayServerConfig;
import org.drasyl.core.server.session.Session;
import org.drasyl.core.server.session.util.ClientSessionBucket;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServerActionJoinTest {
    private RelayServer relay;
    private String responseMsgID;
    private Session localClient, relayClient;
    private ClientSessionBucket clientSessionBucket;

    private SessionUID clientUID;
    private List<IPAddress> backups;

    @Captor
    private ArgumentCaptor<Response> captor;

    @BeforeEach
    void setUp() throws URISyntaxException {
        MockitoAnnotations.initMocks(this);
        relay = mock(RelayServer.class);
        localClient = mock(Session.class);
        relayClient = mock(Session.class);
        clientSessionBucket = mock(ClientSessionBucket.class);

        responseMsgID = "id";
        clientUID = SessionUID.of("clientUID");
        backups = new ArrayList<>();
        IntStream.range(1, 5).forEach(i -> backups.add(new IPAddress("localhost", i)));

        when(relay.getClientBucket()).thenReturn(clientSessionBucket);
        when(relay.getConfig()).thenReturn(new RelayServerConfig(ConfigFactory.load()));
    }

    @Test
    public void onMessageIamResponsibleWithoutOldRelayAndChannelsTest() throws URISyntaxException {
        onMessageIamResponsibleSetup();

        Set<SessionChannel> sessionChannels = Set.of(SessionChannel.of("channel1"), SessionChannel.of("channel2"));

        ServerActionJoin message = new ServerActionJoin(clientUID, sessionChannels);
        message.onMessage(localClient, relay);

        verify(clientSessionBucket, times(1)).addLocalClientSession(clientUID, localClient, sessionChannels);


        verify(clientSessionBucket, never()).addLocalClientSession(clientUID, localClient,
                SessionChannel.of(new RelayServerConfig(ConfigFactory.load()).getRelayDefaultChannel()));
        verify(clientSessionBucket, never()).transferRemoteToLocal(any(), any());
        verify(relayClient, never()).sendMessage(any());

        sendBackupRelayServersTest();
    }

    private void onMessageIamResponsibleSetup() {
        when(clientSessionBucket.getLocalClientSession(clientUID)).thenReturn(null);
    }

    private void sendBackupRelayServersTest() {
        verify(localClient, times(1)).sendMessage(captor.capture());
    }

    @Test
    void onResponse() {
        ServerActionJoin message = new ServerActionJoin();
        message.onResponse(responseMsgID, localClient, relay);

        verifyNoInteractions(localClient);
        verifyNoInteractions(relay);
    }
}