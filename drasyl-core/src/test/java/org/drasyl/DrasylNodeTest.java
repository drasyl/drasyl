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
import org.drasyl.event.Node;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.client.SuperPeerClient;
import org.drasyl.peer.connection.direct.DirectConnectionsManager;
import org.drasyl.peer.connection.intravm.IntraVmDiscovery;
import org.drasyl.peer.connection.server.Server;
import org.drasyl.pipeline.DrasylPipeline;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrasylNodeTest {
    @Mock
    private DrasylConfig config;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private IdentityManager identityManager;
    @Mock
    private Messenger messenger;
    @Mock
    private Server server;
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
    private DirectConnectionsManager directConnectionsManager;
    @Mock
    private CompressedPublicKey identity1;
    @Mock
    private IntraVmDiscovery intraVmDiscovery;
    @Mock
    private DrasylPipeline pipeline;

    @Nested
    class Start {
        @Test
        void shouldStartDirectConnectionsManagerIfEnabled() {
            when(config.areDirectConnectionsEnabled()).thenReturn(true);

            DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, directConnectionsManager, intraVmDiscovery, superPeerClient, server, new AtomicBoolean(false), pipeline, startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            drasylNode.start().join();

            verify(directConnectionsManager).open();
        }

        @Test
        void shouldNotStartDirectConnectionsManagerIfDisabled() {
            when(config.areDirectConnectionsEnabled()).thenReturn(false);

            DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, directConnectionsManager, intraVmDiscovery, superPeerClient, server, new AtomicBoolean(false), pipeline, startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            drasylNode.start().join();

            verify(directConnectionsManager, never()).open();
        }

        @Test
        void shouldReturnSameFutureIfStartHasAlreadyBeenTriggered() {
            DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, directConnectionsManager, intraVmDiscovery, superPeerClient, server, new AtomicBoolean(true), pipeline, startSequence, shutdownSequence) {
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

            DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, directConnectionsManager, intraVmDiscovery, superPeerClient, server, new AtomicBoolean(false), pipeline, startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            drasylNode.start().join();

            verify(drasylNode).onInternalEvent(new NodeUpEvent(Node.of(identity, server.getEndpoints())));
        }
    }

    @Nested
    class Shutdown {
        @Test
        void shouldStopDirectConnectionsManagerIfEnabled() {
            when(config.areDirectConnectionsEnabled()).thenReturn(true);

            DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, directConnectionsManager, intraVmDiscovery, superPeerClient, server, new AtomicBoolean(true), pipeline, startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            drasylNode.shutdown().join();

            verify(directConnectionsManager).close();
        }

        @Test
        void shouldStopDirectConnectionsManagerIfDisabled() {
            when(config.areDirectConnectionsEnabled()).thenReturn(false);

            DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, directConnectionsManager, intraVmDiscovery, superPeerClient, server, new AtomicBoolean(true), pipeline, startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            drasylNode.shutdown().join();

            verify(directConnectionsManager, never()).close();
        }

        @Test
        void shouldEmitDownAndNormalTerminationEventOnSuccessfulShutdown() {
            when(identityManager.getIdentity()).thenReturn(identity);

            DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, directConnectionsManager, intraVmDiscovery, superPeerClient, server, new AtomicBoolean(true), pipeline, startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            drasylNode.shutdown().join();

            verify(drasylNode).onInternalEvent(new NodeDownEvent(Node.of(identity, server.getEndpoints())));
            verify(drasylNode).onInternalEvent(new NodeNormalTerminationEvent(Node.of(identity, server.getEndpoints())));
        }

        @Test
        void shouldReturnSameFutureIfShutdownHasAlreadyBeenTriggered() {
            DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, directConnectionsManager, intraVmDiscovery, superPeerClient, server, new AtomicBoolean(false), pipeline, startSequence, shutdownSequence) {
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

            DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, directConnectionsManager, intraVmDiscovery, superPeerClient, server, started, pipeline, startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            drasylNode.send(recipient, payload);

            verify(pipeline).executeOutbound(any());
        }
    }
}
