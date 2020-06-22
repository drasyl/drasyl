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
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.messenger.MessageSink;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.intravm.IntraVmDiscovery;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.superpeer.SuperPeerClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrasylNodeTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private IdentityManager identityManager;
    @Mock
    private Messenger messenger;
    @Mock
    private NodeServer server;
    @Mock
    private PeersManager peersManager;
    @Mock
    private CompressedPublicKey recipient;
    private final byte[] payload = new byte[]{ 0x4f };
    @Mock
    private Identity identity;
    @Mock
    private AtomicBoolean started;
    @Mock
    private CompletableFuture<Void> startSequence;
    @Mock
    private CompletableFuture<Void> shutdownSequence;
    @Mock
    private SuperPeerClient superPeerClient;
    @Mock
    private MessageSink messageSink;
    @Mock
    private CompressedPublicKey identity1;
    @Mock
    private IntraVmDiscovery intraVmDiscovery;

    @Nested
    class Start {
        @Test
        void shouldReturnSameFutureIfStartHasAlreadyBeenTriggered() {
            DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, intraVmDiscovery, superPeerClient, server, new AtomicBoolean(true), startSequence, shutdownSequence, messageSink) {
                @Override
                public void onEvent(Event event) {
                }
            });
            assertEquals(startSequence, drasylNode.start());
        }

        @Test
        void shouldEmitUpEventOnSuccessfulStart() {
            when(identityManager.getPublicKey()).thenReturn(identity1);
            when(identityManager.getIdentity()).thenReturn(identity);

            DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, intraVmDiscovery, superPeerClient, server, new AtomicBoolean(false), startSequence, shutdownSequence, messageSink) {
                @Override
                public void onEvent(Event event) {
                }
            });
            drasylNode.start().join();

            verify(drasylNode).onEvent(new Event(EventType.EVENT_NODE_UP, Node.of(identity, server.getEndpoints())));
        }
    }

    @Nested
    class Shutdown {
        @Test
        void shouldEmitDownAndNormalTerminationEventOnSuccessfulShutdown() {
            when(identityManager.getIdentity()).thenReturn(identity);

            DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, intraVmDiscovery, superPeerClient, server, new AtomicBoolean(true), startSequence, shutdownSequence, messageSink) {
                @Override
                public void onEvent(Event event) {
                }
            });
            drasylNode.shutdown().join();

            verify(drasylNode).onEvent(new Event(EventType.EVENT_NODE_DOWN, Node.of(identity, server.getEndpoints())));
            verify(drasylNode).onEvent(new Event(EventType.EVENT_NODE_NORMAL_TERMINATION, Node.of(identity, server.getEndpoints())));
        }

        @Test
        void shouldReturnSameFutureIfShutdownHasAlreadyBeenTriggered() {
            DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, intraVmDiscovery, superPeerClient, server, new AtomicBoolean(false), startSequence, shutdownSequence, messageSink) {
                @Override
                public void onEvent(Event event) {
                }
            });
            assertEquals(shutdownSequence, drasylNode.shutdown());
        }
    }

    @Nested
    class Send {
        @Test
        void shouldCallMessenger() throws DrasylException {
            when(identityManager.getPublicKey()).thenReturn(identity1);

            DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, intraVmDiscovery, superPeerClient, server, started, startSequence, shutdownSequence, messageSink) {
                @Override
                public void onEvent(Event event) {
                }
            });
            drasylNode.send(recipient, payload);

            verify(messenger).send(any());
        }
    }
}
