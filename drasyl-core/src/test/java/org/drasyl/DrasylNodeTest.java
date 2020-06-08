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
package org.drasyl;

import org.drasyl.event.Event;
import org.drasyl.event.EventType;
import org.drasyl.event.Node;
import org.drasyl.identity.Address;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.intravm.IntraVmDiscovery;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.superpeer.SuperPeerClient;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DrasylNodeTest {
    private DrasylNodeConfig config;
    private IdentityManager identityManager;
    private Messenger messenger;
    private NodeServer server;
    private PeersManager peersManager;
    private Node node;
    private Event event;
    private Pair<Address, byte[]> message;
    private Address recipient;
    private Address sender;
    private byte[] payload;
    private Identity identity;
    private AtomicBoolean started;
    private CompletableFuture<Void> startSequence;
    private CompletableFuture<Void> shutdownSequence;
    private SuperPeerClient superPeerClient;
    private MessageSink messageSink;
    private Address address;
    private IntraVmDiscovery intraVmDiscovery;

    @BeforeEach
    void setUp() {
        config = mock(DrasylNodeConfig.class);
        identityManager = mock(IdentityManager.class);
        messenger = mock(Messenger.class);
        server = mock(NodeServer.class);
        peersManager = mock(PeersManager.class);
        event = mock(Event.class);
        node = mock(Node.class);
        recipient = mock(Address.class);
        sender = mock(Address.class);
        payload = new byte[]{ 0x4f };
        message = Pair.of(sender, payload);
        identity = mock(Identity.class);
        started = mock(AtomicBoolean.class);
        startSequence = mock(CompletableFuture.class);
        shutdownSequence = mock(CompletableFuture.class);
        superPeerClient = mock(SuperPeerClient.class);
        messageSink = mock(MessageSink.class);
        address = mock(Address.class);
        intraVmDiscovery = mock(IntraVmDiscovery.class);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void startShouldReturnSameFutureIfStartHasAlreadyBeenTriggered() {
        DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, intraVmDiscovery, server, superPeerClient, new AtomicBoolean(true), startSequence, shutdownSequence, messageSink) {
            @Override
            public void onEvent(Event event) {
            }
        });
        assertEquals(startSequence, drasylNode.start());
    }

    @Test
    void startShouldEmitUpEventOnSuccessfulStart() {
        when(identityManager.getAddress()).thenReturn(address);
        when(identityManager.getIdentity()).thenReturn(identity);

        DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, intraVmDiscovery, server, superPeerClient, new AtomicBoolean(false), startSequence, shutdownSequence, messageSink) {
            @Override
            public void onEvent(Event event) {
            }
        });
        drasylNode.start().join();

        verify(drasylNode).onEvent(new Event(EventType.EVENT_NODE_UP, Node.of(identity)));
    }

    @Test
    void shutdownShouldEmitDownAndNormalTerminationEventOnSuccessfulShutdown() {
        when(identityManager.getIdentity()).thenReturn(identity);

        DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, intraVmDiscovery, server, superPeerClient, new AtomicBoolean(true), startSequence, shutdownSequence, messageSink) {
            @Override
            public void onEvent(Event event) {
            }
        });
        drasylNode.shutdown().join();

        verify(drasylNode).onEvent(new Event(EventType.EVENT_NODE_DOWN, Node.of(identity)));
        verify(drasylNode).onEvent(new Event(EventType.EVENT_NODE_NORMAL_TERMINATION, Node.of(identity)));
    }

    @Test
    void shutdownShouldReturnSameFutureIfShutdownHasAlreadyBeenTriggered() {
        DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, intraVmDiscovery, server, superPeerClient, new AtomicBoolean(false), startSequence, shutdownSequence, messageSink) {
            @Override
            public void onEvent(Event event) {
            }
        });
        assertEquals(shutdownSequence, drasylNode.shutdown());
    }

    @Test
    void sendShouldCallMessenger() throws DrasylException {
        when(identityManager.getAddress()).thenReturn(address);

        DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, intraVmDiscovery, server, superPeerClient, started, startSequence, shutdownSequence, messageSink) {
            @Override
            public void onEvent(Event event) {
            }
        });
        drasylNode.send(recipient, payload);

        verify(messenger).send(any());
    }
}
