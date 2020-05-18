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

package org.drasyl.peer.connection.message.action;

import org.drasyl.identity.*;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.ConnectionsManager;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerClientConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.net.URI;
import java.util.Set;

import static org.mockito.Mockito.*;

class JoinMessageActionTest {
    private JoinMessage message;
    private NodeServer nodeServer;
    private NodeServerClientConnection session;
    private PeersManager peersManager;
    private CompressedPublicKey compressedPublicKey;
    private String id;
    private IdentityManager identityManager;
    private CompressedKeyPair keyPair;
    private Messenger messenger;
    private ConnectionsManager connectionsManager;
    private String correspondingId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        message = mock(JoinMessage.class);
        nodeServer = mock(NodeServer.class);
        session = mock(NodeServerClientConnection.class);
        peersManager = mock(PeersManager.class);
        compressedPublicKey = mock(CompressedPublicKey.class);
        id = "id";
        correspondingId = "correspondingId";

        identityManager = mock(IdentityManager.class);
        keyPair = mock(CompressedKeyPair.class);
        messenger = mock(Messenger.class);
        connectionsManager = mock(ConnectionsManager.class);

        when(compressedPublicKey.toString()).thenReturn(IdentityTestHelper.random().getId());
        when(nodeServer.getPeersManager()).thenReturn(peersManager);
        when(nodeServer.getMyIdentity()).thenReturn(identityManager);
        when(identityManager.getKeyPair()).thenReturn(keyPair);
        when(keyPair.getPublicKey()).thenReturn(compressedPublicKey);
        when(nodeServer.getEntryPoints()).thenReturn(Set.of(URI.create("ws://testURI")));
        when(nodeServer.getMessenger()).thenReturn(messenger);
        when(messenger.getConnectionsManager()).thenReturn(connectionsManager);
    }

    @Test
    void onMessageServerShouldAddPeerToPeersManager() {
        when(message.getPublicKey()).thenReturn(compressedPublicKey);
        when(message.getEndpoints()).thenReturn(Set.of());
        when(message.getId()).thenReturn(id);

        JoinMessageAction action = new JoinMessageAction(message);
        action.onMessageServer(session, nodeServer);

        PeerInformation myPeerInformation = new PeerInformation();
        myPeerInformation.setPublicKey(message.getPublicKey());
        myPeerInformation.addEndpoint(message.getEndpoints());

        verify(peersManager).addPeer(Identity.of(compressedPublicKey), myPeerInformation);
        verify(peersManager).addChildren(Identity.of(compressedPublicKey));

        verify(session).send(new WelcomeMessage(nodeServer.getMyIdentity().getKeyPair().getPublicKey(), nodeServer.getEntryPoints(), id));
    }
}