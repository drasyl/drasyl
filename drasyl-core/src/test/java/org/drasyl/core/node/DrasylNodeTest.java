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
package org.drasyl.core.node;

import org.drasyl.core.client.SuperPeerClient;
import org.drasyl.core.common.models.Pair;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.models.Event;
import org.drasyl.core.models.Node;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.node.identity.IdentityManager;
import org.drasyl.core.server.NodeServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.drasyl.core.models.Code.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class DrasylNodeTest {
    private DrasylNodeConfig config;
    private IdentityManager identityManager;
    private Messenger messenger;
    private NodeServer server;
    private PeersManager peersManager;
    private Node node;
    private Event event;
    private Pair<Identity, byte[]> message;
    private Identity recipient;
    private Identity sender;
    private byte[] payload;
    private Identity identity;
    private AtomicBoolean started;
    private CompletableFuture<Void> startSequence;
    private CompletableFuture<Void> shutdownSequence;
    private SuperPeerClient superPeerClient;
    private ConnectionsManager connectionsManager;

    @BeforeEach
    void setUp() {
        config = mock(DrasylNodeConfig.class);
        identityManager = mock(IdentityManager.class);
        messenger = mock(Messenger.class);
        server = mock(NodeServer.class);
        peersManager = mock(PeersManager.class);
        event = mock(Event.class);
        node = mock(Node.class);
        recipient = mock(Identity.class);
        sender = mock(Identity.class);
        payload = new byte[]{ 0x4f };
        message = Pair.of(sender, payload);
        identity = mock(Identity.class);
        started = mock(AtomicBoolean.class);
        startSequence = mock(CompletableFuture.class);
        shutdownSequence = mock(CompletableFuture.class);
        superPeerClient = mock(SuperPeerClient.class);
        connectionsManager = mock(ConnectionsManager.class);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    public void startShouldReturnSameFutureIfStartHasAlreadyBeenTriggered() {
        DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, server, superPeerClient, new AtomicBoolean(true), startSequence, shutdownSequence) {
            @Override
            public void onEvent(Event event) {
            }
        });
        assertEquals(startSequence, drasylNode.start());
    }

    @Test
    public void startShouldEmitUpEventOnSuccessfulStart() {
        when(identityManager.getIdentity()).thenReturn(identity);
        when(messenger.getConnectionsManager()).thenReturn(connectionsManager);

        DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, server, superPeerClient, new AtomicBoolean(false), startSequence, shutdownSequence) {
            @Override
            public void onEvent(Event event) {
            }
        });
        drasylNode.start().join();

        verify(drasylNode).onEvent(new Event(NODE_UP, new Node(identity)));
    }

    @Test
    public void shutdownShouldEmitDownAndNormalTerminationEventOnSuccessfulShutdown() {
        when(identityManager.getIdentity()).thenReturn(identity);
        when(messenger.getConnectionsManager()).thenReturn(connectionsManager);

        DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, server, superPeerClient, new AtomicBoolean(true), startSequence, shutdownSequence) {
            @Override
            public void onEvent(Event event) {
            }
        });
        drasylNode.shutdown().join();

        verify(drasylNode).onEvent(new Event(NODE_DOWN, new Node(identity)));
        verify(drasylNode).onEvent(new Event(NODE_NORMAL_TERMINATION, new Node(identity)));
    }

    @Test
    public void shutdownShouldReturnSameFutureIfShutdownHasAlreadyBeenTriggered() {
        DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, server, superPeerClient, new AtomicBoolean(false), startSequence, shutdownSequence) {
            @Override
            public void onEvent(Event event) {
            }
        });
        assertEquals(shutdownSequence, drasylNode.shutdown());
    }

    @Test
    public void sendShouldCallMessenger() throws DrasylException {
        when(identityManager.getIdentity()).thenReturn(identity);

        DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, server, superPeerClient, started, startSequence, shutdownSequence) {
            @Override
            public void onEvent(Event event) {
            }
        });
        drasylNode.send(recipient, payload);

        verify(messenger).send(any());
    }
}
