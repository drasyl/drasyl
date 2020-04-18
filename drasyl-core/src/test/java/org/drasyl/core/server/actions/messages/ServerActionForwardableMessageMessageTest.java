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
import org.drasyl.core.common.messages.Status;
import org.drasyl.core.common.models.Pair;
import org.drasyl.core.common.models.SessionUID;
import org.drasyl.core.server.RelayServer;
import org.drasyl.core.server.RelayServerConfig;
import org.drasyl.core.server.session.Session;
import org.drasyl.core.server.session.util.ClientSessionBucket;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServerActionForwardableMessageMessageTest {
    private Session session;
    private RelayServer relay;
    private String responseMsgID;
    private SessionUID localUID, remoteUID;
    private byte[] blob;
    private ClientSessionBucket clientSessionBucket;

    @BeforeEach
    void setUp() throws URISyntaxException {
        session = mock(Session.class);
        relay = mock(RelayServer.class);
        clientSessionBucket = mock(ClientSessionBucket.class);

        responseMsgID = "id";
        localUID = SessionUID.of("junit1");
        remoteUID = SessionUID.of("junit2");
        blob = new byte[]{0x00, 0x01, 0x03};

        Map<SessionUID, Session> localClients = new HashMap<>();
        localClients.put(localUID, session);
        Set<SessionUID> remoteClients = new HashSet<>();
        remoteClients.add(remoteUID);

        when(relay.getClientBucket()).thenReturn(clientSessionBucket);
        when(relay.getConfig()).thenReturn(new RelayServerConfig(ConfigFactory.load()));
        when(clientSessionBucket.aggregateChannelMembers(any())).thenReturn(Pair.of(localClients, remoteClients));
    }

    @Test
    public void onMessageOneRemoteReceiverTest() {
        ServerActionForwardableMessage message = new ServerActionForwardableMessage(localUID, remoteUID, blob);

        message.onMessage(session, relay);


        verify(session).sendMessage(new Response<>(Status.OK, message.getMessageID()));
        verify(relay, never()).forwardMessage(any(), any(), any());
        verify(relay, never()).broadcastMessageLocally(any(), any());
    }

    @Test
    public void onMessageOneLocalReceiverTest() {
        ServerActionForwardableMessage message = new ServerActionForwardableMessage(localUID, localUID, blob);

        message.onMessage(session, relay);

        verify(session).sendMessage(new Response<>(Status.OK, message.getMessageID()));
        verify(relay).broadcastMessageLocally(eq(message), any());
    }

    @Test
    public void onMessageNoReceiverTest() {
        ServerActionForwardableMessage message = new ServerActionForwardableMessage(localUID, SessionUID.of("junit3"), blob);

        message.onMessage(session, relay);

        verify(session).sendMessage(new Response<>(Status.NOT_FOUND, message.getMessageID()));

        verify(relay, never()).forwardMessage(any(), any(), any());
        verify(relay, never()).broadcastMessageLocally(any(), any());
    }

    @Test
    public void onNullTest() {
        ServerActionForwardableMessage message = new ServerActionForwardableMessage(localUID, SessionUID.of("junit3"), blob);

        Assertions.assertThrows(NullPointerException.class, () -> {
            message.onMessage(null, relay);
        });

        verify(session, never()).sendMessage(any());
        verify(relay, never()).forwardMessage(any(), any(), any());
        verify(relay, never()).broadcastMessageLocally(any(), any());
    }

    @Test
    public void onMessageALLTest() {
        ServerActionForwardableMessage message = new ServerActionForwardableMessage(localUID, SessionUID.ALL, blob);

        message.onMessage(session, relay);

        verify(session).sendMessage(new Response<>(Status.OK, message.getMessageID()));
        verify(session, never()).sendMessage(message);
        verify(relay, never()).forwardMessage(any(), any(), any());
    }

    @Test
    public void onMessageALLButEmptyBucketsTest() {
        ServerActionForwardableMessage message = new ServerActionForwardableMessage(localUID, SessionUID.ALL, blob);

        when(clientSessionBucket.aggregateChannelMembers(any())).thenReturn(Pair.of(new HashMap<>(), new HashSet<>()));

        message.onMessage(session, relay);

        verify(session).sendMessage(new Response<>(Status.NOT_FOUND, message.getMessageID()));
        verify(relay, never()).broadcastMessageLocally(any(), any());
        verify(session, never()).sendMessage(message);
        verify(relay, never()).forwardMessage(any(), any(), any());
    }

    @Test
    void onResponse() {
        ServerActionForwardableMessage message = new ServerActionForwardableMessage();

        message.onResponse(responseMsgID, session, relay);

        verifyNoInteractions(session);
        verifyNoInteractions(relay);
    }
}