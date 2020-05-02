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
import org.drasyl.core.common.messages.Response;
import org.drasyl.core.models.CompressedKeyPair;
import org.drasyl.core.models.CompressedPublicKey;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.node.DrasylNodeConfig;
import org.drasyl.core.node.PeerInformation;
import org.drasyl.core.node.PeersManager;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.node.identity.IdentityManager;
import org.drasyl.core.node.identity.IdentityTestHelper;
import org.drasyl.core.server.NodeServer;
import org.drasyl.core.server.session.ServerSession;
import org.drasyl.crypto.Crypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.net.URI;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServerActionJoinTest {
    private NodeServer nodeServer;
    private ServerSession session;
    private PeersManager peersManager;
    private CompressedPublicKey compressedPublicKey;
    private PeerInformation peerInformation;

    @BeforeEach
    void setUp() throws DrasylException {
        MockitoAnnotations.initMocks(this);
        nodeServer = mock(NodeServer.class);
        session = mock(ServerSession.class);
        peersManager = mock(PeersManager.class);
        peerInformation = mock(PeerInformation.class);
        compressedPublicKey = mock(CompressedPublicKey.class);

        IdentityManager identityManager = mock(IdentityManager.class);
        CompressedKeyPair keyPair = mock(CompressedKeyPair.class);

        when(compressedPublicKey.toString()).thenReturn(IdentityTestHelper.random().getId());
        when(nodeServer.getPeersManager()).thenReturn(peersManager);
        when(peersManager.addChildren(any(Identity.class))).thenReturn(peerInformation);
        when(nodeServer.getConfig()).thenReturn(new DrasylNodeConfig(ConfigFactory.load()));
        when(nodeServer.getMyIdentity()).thenReturn(identityManager);
        when(identityManager.getKeyPair()).thenReturn(keyPair);
        when(keyPair.getPublicKey()).thenReturn(compressedPublicKey);
        when(nodeServer.getEntryPoints()).thenReturn(Set.of(URI.create("ws://testURI")));
    }

    @Test
    public void onMessageTest() {
        ServerActionJoin message = new ServerActionJoin(compressedPublicKey, Set.of());
        message.onMessage(session, nodeServer);

        verify(peersManager, times(1)).addChildren(Identity.of(compressedPublicKey));
        verify(peerInformation, times(1)).setPublicKey(message.getPublicKey());
        verify(peerInformation, times(1)).addPeerConnection(session);
        verify(peerInformation, times(1)).addEndpoint(message.getEndpoints());

        verify(session, times(1)).send(any(Response.class));
    }

    @Test
    void onResponse() {
        ServerActionJoin message = new ServerActionJoin();
        message.onResponse(Crypto.randomString(12), session, nodeServer);

        verifyNoInteractions(session);
        verifyNoInteractions(nodeServer);
    }
}