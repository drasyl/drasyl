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

import ch.qos.logback.classic.Level;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.MessageSinkException;
import org.drasyl.messenger.Messenger;
import org.drasyl.messenger.MessengerException;
import org.drasyl.messenger.NoPathToPublicKeyException;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.IdentityMessage;
import org.drasyl.peer.connection.message.MessageId;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.RelayableMessage;
import org.drasyl.peer.connection.message.WhoisMessage;
import org.drasyl.peer.connection.server.Server;
import org.drasyl.pipeline.DrasylPipeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static ch.qos.logback.classic.Level.TRACE;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrasylNodeTest {
    private final CompletableFuture<Void> startSequence = new CompletableFuture<>();
    private final CompletableFuture<Void> shutdownSequence = new CompletableFuture<>();
    private final byte[] payload = new byte[]{ 0x4f };
    @Mock
    private DrasylConfig config;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private Messenger messenger;
    @Mock
    private PeersManager peersManager;
    @Mock
    private AtomicBoolean started;
    private final Set<URI> endpoints = new HashSet<>();
    @Mock
    private CompressedPublicKey publicKey;
    @Mock
    private DrasylPipeline pipeline;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private PeerChannelGroup channelGroup;
    @Mock
    private AtomicBoolean acceptNewConnections;
    @Mock
    private List<DrasylNodeComponent> drasylNodeComponents;

    @Nested
    class Start {
        @Test
        void shouldStartEnabledComponents(@Mock DrasylNodeComponent drasylNodeComponent) throws DrasylException {
            drasylNodeComponents = List.of(drasylNodeComponent);
            DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, drasylNodeComponents, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            shutdownSequence.complete(null);
            underTest.start().join();

            verify(drasylNodeComponent).open();
        }

        @Test
        void shouldEmitUpEventOnSuccessfulStart() {
            DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, drasylNodeComponents, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            shutdownSequence.complete(null);
            underTest.start().join();

            verify(underTest).onInternalEvent(new NodeUpEvent(Node.of(identity, endpoints)));
        }

        @Test
        void shouldEmitNodeUnrecoverableErrorEventOnFailedStart(@Mock DrasylNodeComponent drasylNodeComponent) throws DrasylException {
            drasylNodeComponents = List.of(drasylNodeComponent);
            doThrow(new DrasylException("error")).when(drasylNodeComponent).open();

            DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, drasylNodeComponents, started, startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            shutdownSequence.complete(null);
            assertThrows(ExecutionException.class, underTest.start()::get);

            verify(underTest).onInternalEvent(new NodeUnrecoverableErrorEvent(Node.of(identity, endpoints), new DrasylException("error")));

            verify(drasylNodeComponent).close();
        }
    }

    @Nested
    class Shutdown {
        @Test
        void shouldStopEnabledComponents(@Mock DrasylNodeComponent drasylNodeComponent) {
            drasylNodeComponents = List.of(drasylNodeComponent);
            DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, drasylNodeComponents, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            startSequence.complete(null);
            underTest.shutdown().join();

            verify(drasylNodeComponent).close();
        }

        @Test
        void shouldReturnSameFutureIfShutdownHasAlreadyBeenTriggered() {
            DrasylNode drasylNode = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, drasylNodeComponents, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            assertEquals(shutdownSequence, drasylNode.shutdown());
        }

        @Test
        void shouldSendQuitMessageToAllPeers() {
            DrasylNode drasylNode = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, drasylNodeComponents, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            startSequence.complete(null);
            drasylNode.shutdown().join();

            verify(channelGroup).writeAndFlush(new QuitMessage(REASON_SHUTTING_DOWN));
        }
    }

    @Nested
    class Send {
        private DrasylNode underTest;

        @BeforeEach
        void setUp() {
            underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, drasylNodeComponents, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
        }

        @Test
        void shouldPassMessageToPipeline(@Mock CompressedPublicKey myRecipient) {
            underTest.send(myRecipient, new byte[]{ 0x4f });

            verify(pipeline).processOutbound(myRecipient, payload);
        }

        @Test
        void recipientAsStringShouldPassMessageToPipeline() throws DrasylException, CryptoException {
            underTest.send("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458", payload);

            verify(pipeline).processOutbound(
                    CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458"),
                    payload
            );
        }

        @Test
        void payloadAsStringShouldPassMessageToPipeline(@Mock CompressedPublicKey myRecipient) {
            underTest.send(myRecipient, "Hallo Welt");

            verify(pipeline).processOutbound(
                    myRecipient,
                    "Hallo Welt"
            );
        }

        @Test
        void recipientAndPayloadAsStringShouldPassMessageToPipeline() throws DrasylException, CryptoException {
            underTest.send("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458", "Hallo Welt");

            verify(pipeline).processOutbound(
                    CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458"),
                    "Hallo Welt"
            );
        }
    }

    @Nested
    class Pipeline {
        @Test
        void shouldReturnPipeline() {
            DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, drasylNodeComponents, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });

            assertEquals(pipeline, underTest.pipeline());
        }
    }

    @Nested
    class IdentityMethod {
        @Test
        void shouldReturnIdentity() {
            DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, drasylNodeComponents, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });

            assertEquals(identity, underTest.identity());
        }
    }

    @Nested
    class MessageSink {
        @Test
        void shouldThrowExceptionIfNotRecipient(@Mock RelayableMessage message) {
            DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, drasylNodeComponents, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            startSequence.complete(null);
            underTest.shutdown().join();

            assertThrows(NoPathToPublicKeyException.class, () -> underTest.messageSink(message));
        }

        @Test
        void shouldPassApplicationMessageToPipeline(@Mock ApplicationMessage message) throws MessageSinkException {
            when(identity.getPublicKey()).thenReturn(publicKey);
            when(message.getRecipient()).thenReturn(publicKey);

            DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, drasylNodeComponents, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            underTest.messageSink(message);

            verify(pipeline).processInbound(message);
            verify(peersManager).addPeer(message.getSender());
        }

        @Test
        void shouldReplyToWhoisMessage(@Mock(answer = Answers.RETURNS_DEEP_STUBS) WhoisMessage message) throws MessengerException {
            when(identity.getPublicKey()).thenReturn(publicKey);
            when(message.getId()).thenReturn(new MessageId("123"));
            when(message.getRecipient()).thenReturn(publicKey);

            DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, drasylNodeComponents, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            underTest.messageSink(message);

            verify(messenger).send(any(IdentityMessage.class));
        }

        @Test
        void shouldPassIdentityMessageToPeersManager(@Mock(answer = Answers.RETURNS_DEEP_STUBS) IdentityMessage message) throws MessengerException {
            when(identity.getPublicKey()).thenReturn(publicKey);
            when(message.getRecipient()).thenReturn(publicKey);

            DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, drasylNodeComponents, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(Event event) {
                }
            });
            underTest.messageSink(message);

            verify(peersManager).setPeerInformation(message.getPublicKey(), message.getPeerInformation());
        }
    }

    @Nested
    class SetLogLevel {
        private Level logLevel;

        @Test
        void shouldSetLogLevel() {
            DrasylNode.setLogLevel(TRACE);

            assertTrue(LoggerFactory.getLogger(DrasylNode.class).isTraceEnabled());
            assertTrue(LoggerFactory.getLogger(Server.class).isTraceEnabled());
        }

        @BeforeEach
        void setUp() {
            logLevel = DrasylNode.getLogLevel();
        }

        @AfterEach
        void tearDown() {
            // restore default log level
            DrasylNode.setLogLevel(logLevel);
        }
    }

    @Nested
    class GetLogLevel {
        @Test
        void shouldReturnLogLevel() {
            assertNull(DrasylNode.getLogLevel());
        }
    }
}
