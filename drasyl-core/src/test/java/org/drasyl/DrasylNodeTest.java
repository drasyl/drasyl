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

import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.identity.IdentityManagerException;
import org.drasyl.messenger.MessageSinkException;
import org.drasyl.messenger.Messenger;
import org.drasyl.monitoring.Monitoring;
import org.drasyl.messenger.MessengerException;
import org.drasyl.messenger.NoPathToIdentityException;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.client.SuperPeerClient;
import org.drasyl.peer.connection.direct.DirectConnectionsManager;
import org.drasyl.peer.connection.intravm.IntraVmDiscovery;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.IdentityMessage;
import org.drasyl.peer.connection.message.RelayableMessage;
import org.drasyl.peer.connection.message.WhoisMessage;
import org.drasyl.peer.connection.server.Server;
import org.drasyl.peer.connection.server.ServerException;
import org.drasyl.pipeline.DrasylPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    private final byte[] payload = new byte[]{ 0x4f };
    @Mock
    private Identity identity;
    @Mock
    private AtomicBoolean started;
    private CompletableFuture<Void> startSequence = new CompletableFuture<>();
    private CompletableFuture<Void> shutdownSequence = new CompletableFuture<>();
    @Mock
    private SuperPeerClient superPeerClient;
    @Mock
    private DirectConnectionsManager directConnectionsManager;
    @Mock
    private CompressedPublicKey publicKey;
    @Mock
    private IntraVmDiscovery intraVmDiscovery;
    @Mock
    private DrasylPipeline pipeline;
    @Mock
    private Monitoring monitoring;

    @Nested
    class Start {
        @Test
        void shouldLoadIdentity() throws IdentityManagerException {
            DrasylNode underTest = spy(new DrasylNode(config, identityManager, peersManager, messenger, pipeline, directConnectionsManager, intraVmDiscovery, superPeerClient, server, monitoring, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            underTest.start().join();

            verify(identityManager).loadOrCreateIdentity();
        }

        @Test
        void shouldStartEnabledComponents() throws ServerException {
            when(config.isIntraVmDiscoveryEnabled()).thenReturn(true);
            when(config.isServerEnabled()).thenReturn(true);
            when(config.isSuperPeerEnabled()).thenReturn(true);
            when(config.areDirectConnectionsEnabled()).thenReturn(true);
            when(config.isMonitoringEnabled()).thenReturn(true);

            DrasylNode underTest = spy(new DrasylNode(config, identityManager, peersManager, messenger, pipeline, directConnectionsManager, intraVmDiscovery, superPeerClient, server, monitoring, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            underTest.start().join();

            verify(intraVmDiscovery).open();
            verify(server).open();
            verify(superPeerClient).open();
            verify(directConnectionsManager).open();
            verify(monitoring).open();
        }

        @Test
        void shouldNotStartDisabledComponents() throws ServerException {
            when(config.isIntraVmDiscoveryEnabled()).thenReturn(false);
            when(config.isServerEnabled()).thenReturn(false);
            when(config.isSuperPeerEnabled()).thenReturn(false);
            when(config.isMonitoringEnabled()).thenReturn(false);

            DrasylNode underTest = spy(new DrasylNode(config, identityManager, peersManager, messenger, pipeline, directConnectionsManager, intraVmDiscovery, superPeerClient, server, monitoring, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            underTest.start().join();

            verify(intraVmDiscovery, never()).open();
            verify(server, never()).open();
            verify(superPeerClient, never()).open();
            verify(directConnectionsManager, never()).open();
            verify(monitoring, never()).open();
        }

        @Test
        void shouldEmitUpEventOnSuccessfulStart() {
            when(identityManager.getPublicKey()).thenReturn(publicKey);
            when(identityManager.getIdentity()).thenReturn(identity);

            DrasylNode underTest = spy(new DrasylNode(config, identityManager, peersManager, messenger, pipeline, directConnectionsManager, intraVmDiscovery, superPeerClient, server, monitoring, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            underTest.start().join();

            verify(underTest).onInternalEvent(new NodeUpEvent(Node.of(identity, server.getEndpoints())));
        }

        @Test
        void shouldEmitNodeUnrecoverableErrorEventOnFailedStart() throws ServerException {
            when(config.isIntraVmDiscoveryEnabled()).thenReturn(true);
            when(config.isServerEnabled()).thenReturn(true);
            when(config.isSuperPeerEnabled()).thenReturn(true);
            when(config.areDirectConnectionsEnabled()).thenReturn(true);
            when(identityManager.getPublicKey()).thenReturn(publicKey);
            when(identityManager.getIdentity()).thenReturn(identity);
            doThrow(new ServerException("error")).when(server).open();

            DrasylNode underTest = spy(new DrasylNode(config, identityManager, peersManager, messenger, pipeline, directConnectionsManager, intraVmDiscovery, superPeerClient, server, monitoring, started, startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            assertThrows(ExecutionException.class, underTest.start()::get);

            verify(underTest).onInternalEvent(new NodeUnrecoverableErrorEvent(Node.of(identity, server.getEndpoints()), new ServerException("error")));

            verify(directConnectionsManager).close();
            verify(superPeerClient).close();
            verify(server).close();
            verify(intraVmDiscovery).close();
        }
    }

    @Nested
    class Shutdown {
        @Test
        void shouldStopEnabledComponents() {
            when(config.isIntraVmDiscoveryEnabled()).thenReturn(true);
            when(config.isServerEnabled()).thenReturn(true);
            when(config.isSuperPeerEnabled()).thenReturn(true);
            when(config.areDirectConnectionsEnabled()).thenReturn(true);
            when(config.isMonitoringEnabled()).thenReturn(true);

            DrasylNode underTest = spy(new DrasylNode(config, identityManager, peersManager, messenger, pipeline, directConnectionsManager, intraVmDiscovery, superPeerClient, server, monitoring, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            underTest.shutdown().join();

            verify(intraVmDiscovery).close();
            verify(server).close();
            verify(superPeerClient).close();
            verify(directConnectionsManager).close();
            verify(monitoring).close();
        }

        @Test
        void shouldNotStopDisabledComponents() {
            when(config.isIntraVmDiscoveryEnabled()).thenReturn(false);
            when(config.isServerEnabled()).thenReturn(false);
            when(config.isSuperPeerEnabled()).thenReturn(false);
            when(config.areDirectConnectionsEnabled()).thenReturn(false);
            when(config.isMonitoringEnabled()).thenReturn(false);

            DrasylNode underTest = spy(new DrasylNode(config, identityManager, peersManager, messenger, pipeline, directConnectionsManager, intraVmDiscovery, superPeerClient, server, monitoring, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            underTest.shutdown().join();

            verify(intraVmDiscovery, never()).close();
            verify(server, never()).close();
            verify(superPeerClient, never()).close();
            verify(directConnectionsManager, never()).close();
            verify(monitoring, never()).close();
        }

        @Test
        void shouldReturnSameFutureIfShutdownHasAlreadyBeenTriggered() {
            DrasylNode drasylNode = spy(new DrasylNode(config, identityManager, peersManager, messenger, pipeline, directConnectionsManager, intraVmDiscovery, superPeerClient, server, monitoring, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            assertEquals(shutdownSequence, drasylNode.shutdown());
        }
    }

    @Nested
    class Send {
        private DrasylNode underTest;

        @BeforeEach
        void setUp() {
            underTest = spy(new DrasylNode(config, identityManager, peersManager, messenger, pipeline, directConnectionsManager, intraVmDiscovery, superPeerClient, server, monitoring, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
        }

        @Test
        void shouldPassMessageToPipeline(@Mock CompressedPublicKey myRecipient) {
            underTest.send(myRecipient, new byte[]{ 0x4f });

            verify(pipeline).executeOutbound(new ApplicationMessage(identityManager.getPublicKey(), myRecipient, payload));
        }

        @Test
        void recipientAsStringShouldPassMessageToPipeline() throws DrasylException, CryptoException {
            underTest.send("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458", payload);

            verify(pipeline).executeOutbound(new ApplicationMessage(
                    identityManager.getPublicKey(),
                    CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458"),
                    payload
            ));
        }

        @Test
        void payloadAsStringShouldPassMessageToPipeline(@Mock CompressedPublicKey myRecipient) throws DrasylException {
            underTest.send(myRecipient, "Hallo Welt");

            verify(pipeline).executeOutbound(new ApplicationMessage(
                    identityManager.getPublicKey(),
                    myRecipient,
                    "Hallo Welt".getBytes()
            ));
        }

        @Test
        void recipientAndPayloadAsStringShouldPassMessageToPipeline() throws DrasylException, CryptoException {
            underTest.send("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458", "Hallo Welt");

            verify(pipeline).executeOutbound(new ApplicationMessage(
                    identityManager.getPublicKey(),
                    CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458"),
                    "Hallo Welt".getBytes()
            ));
        }
    }

    @Nested
    class Pipeline {
        @Test
        void shouldReturnPipeline() {
            DrasylNode underTest = spy(new DrasylNode(config, identityManager, peersManager, messenger, pipeline, directConnectionsManager, intraVmDiscovery, superPeerClient, server, monitoring, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });

            assertEquals(pipeline, underTest.pipeline());
        }
    }

    @Nested
    class MessageSink {
        @Test
        void shouldThrowExceptionIfNotRecipient(@Mock RelayableMessage message) {
            DrasylNode underTest = spy(new DrasylNode(config, identityManager, peersManager, messenger, pipeline, directConnectionsManager, intraVmDiscovery, superPeerClient, server, monitoring, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            underTest.shutdown().join();

            assertThrows(NoPathToIdentityException.class, () -> underTest.messageSink(message));
        }

        @Test
        void shouldPassApplicationMessageToPipeline(@Mock ApplicationMessage message) throws MessageSinkException {
            when(identityManager.getPublicKey()).thenReturn(publicKey);
            when(message.getRecipient()).thenReturn(publicKey);

            DrasylNode underTest = spy(new DrasylNode(config, identityManager, peersManager, messenger, pipeline, directConnectionsManager, intraVmDiscovery, superPeerClient, server, monitoring, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            underTest.messageSink(message);

            verify(pipeline).executeInbound(message);
            verify(peersManager).addPeer(message.getSender());
        }

        @Test
        void shouldReplyToWhoisMessage(@Mock(answer = Answers.RETURNS_DEEP_STUBS) WhoisMessage message) throws MessengerException {
            when(identityManager.getPublicKey()).thenReturn(publicKey);
            when(message.getId()).thenReturn("123");
            when(message.getRecipient()).thenReturn(publicKey);

            DrasylNode underTest = spy(new DrasylNode(config, identityManager, peersManager, messenger, pipeline, directConnectionsManager, intraVmDiscovery, superPeerClient, server, monitoring, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            underTest.messageSink(message);

            verify(messenger).send(any(IdentityMessage.class));
        }

        @Test
        void shouldPassIdentityMessageToPeersManager(@Mock(answer = Answers.RETURNS_DEEP_STUBS) IdentityMessage message) throws MessengerException {
            when(identityManager.getPublicKey()).thenReturn(publicKey);
            when(message.getRecipient()).thenReturn(publicKey);

            DrasylNode underTest = spy(new DrasylNode(config, identityManager, peersManager, messenger, pipeline, directConnectionsManager, intraVmDiscovery, superPeerClient, server, monitoring, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            underTest.messageSink(message);

            verify(peersManager).setPeerInformation(message.getPublicKey(), message.getPeerInformation());
        }
    }
}
