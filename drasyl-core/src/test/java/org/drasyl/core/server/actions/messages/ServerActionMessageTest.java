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

import com.typesafe.config.ConfigFactory;
import org.drasyl.core.common.messages.Message;
import org.drasyl.core.common.messages.Response;
import org.drasyl.core.common.messages.Status;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.node.DrasylNodeConfig;
import org.drasyl.core.node.PeersManager;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.server.NodeServer;
import org.drasyl.core.server.session.ServerSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServerActionMessageTest {
    private ServerSession serverSession;
    private NodeServer nodeServer;
    private String responseMsgID;
    private Identity localUID, remoteUID;
    private byte[] blob;

    @BeforeEach
    void setUp() throws DrasylException {
        serverSession = mock(ServerSession.class);
        nodeServer = mock(NodeServer.class);
        PeersManager peersManager = mock(PeersManager.class);

        responseMsgID = "id";
        localUID = Identity.of("ead3151c64");
        remoteUID = Identity.of("abf391dbde");
        blob = new byte[]{ 0x00, 0x01, 0x03 };

        when(nodeServer.getPeersManager()).thenReturn(peersManager);
        when(nodeServer.getConfig()).thenReturn(new DrasylNodeConfig(ConfigFactory.load()));
    }

    @Test
    public void onMessagePeerFoundTest() throws DrasylException {
        ServerActionMessage message = new ServerActionMessage(localUID, remoteUID, blob);

        message.onMessage(serverSession, nodeServer);

        verify(serverSession).send(new Response<>(Status.OK, message.getMessageID()));
        verify(nodeServer, times(1)).send(any(Message.class));
    }

    @Test
    public void onMessagePeerNotFoundTest() throws DrasylException {
        doThrow(DrasylException.class).when(nodeServer).send(any());

        ServerActionMessage message = new ServerActionMessage(localUID, remoteUID, blob);

        message.onMessage(serverSession, nodeServer);

        verify(serverSession).send(new Response<>(Status.NOT_FOUND, message.getMessageID()));
    }

    @Test
    public void forwardedMessageShouldBeEquals() throws DrasylException {
        ArgumentCaptor<Message> arg = ArgumentCaptor.forClass(Message.class);

        ServerActionMessage message = new ServerActionMessage(localUID, remoteUID, blob);

        message.onMessage(serverSession, nodeServer);

        verify(serverSession).send(new Response<>(Status.OK, message.getMessageID()));
        verify(nodeServer, times(1)).send(arg.capture());

        assertEquals(message, arg.getValue());
    }

    @Test
    public void onNullTest() throws DrasylException {
        ServerActionMessage message = new ServerActionMessage(localUID, Identity.of("8cd8225cbd"), blob);

        Assertions.assertThrows(NullPointerException.class, () -> {
            message.onMessage(null, nodeServer);
        });

        Assertions.assertThrows(NullPointerException.class, () -> {
            message.onMessage(serverSession, null);
        });

        verify(serverSession, never()).send(any());
        verify(nodeServer, never()).send(any());
    }

    @Test
    void onResponse() {
        ServerActionMessage message = new ServerActionMessage();

        message.onResponse(responseMsgID, serverSession, nodeServer);

        verifyNoInteractions(serverSession);
        verifyNoInteractions(nodeServer);
    }
}