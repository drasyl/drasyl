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
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.IdentityMessage;
import org.drasyl.peer.connection.message.MessageId;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.RelayableMessage;
import org.drasyl.peer.connection.message.WhoisMessage;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.plugins.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
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
    private final Set<Endpoint> endpoints = new HashSet<>();
    @Mock
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Messenger messenger;
    @Mock
    private PeersManager peersManager;
    @Mock
    private AtomicBoolean started;
    @Mock
    private CompressedPublicKey publicKey;
    @Mock
    private DrasylPipeline pipeline;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private PeerChannelGroup channelGroup;
    @Mock
    private AtomicBoolean acceptNewConnections;
    @Mock
    private PluginManager pluginManager;
    private List<DrasylNodeComponent> components = List.of();

    @Nested
    class Start {
        @Test
        void shouldStartEnabledComponents(@Mock final DrasylNodeComponent component) throws DrasylException {
            components = List.of(component);
            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(final Event event) {
                }
            });
            shutdownSequence.complete(null);
            underTest.start().join();

            verify(component).open();
        }

        @Test
        void shouldEmitUpEventOnSuccessfulStart() {
            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(final Event event) {
                }
            });
            shutdownSequence.complete(null);
            underTest.start().join();

            verify(underTest).onInternalEvent(new NodeUpEvent(Node.of(identity, endpoints)));
        }

        @Test
        void shouldEmitNodeUnrecoverableErrorEventOnFailedStart(@Mock final DrasylNodeComponent drasylNodeComponent) throws DrasylException {
            components = List.of(drasylNodeComponent);
            doThrow(new DrasylException("error")).when(drasylNodeComponent).open();

            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, started, startSequence, shutdownSequence) {
                @Override
                public void onEvent(final Event event) {
                }
            });
            shutdownSequence.complete(null);
            assertThrows(ExecutionException.class, underTest.start()::get);

            verify(underTest).onInternalEvent(new NodeUnrecoverableErrorEvent(Node.of(identity, endpoints), new DrasylException("error")));

            verify(drasylNodeComponent).close();
        }

        @Test
        void shouldStartPlugins() {
            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(final Event event) {
                }
            });
            shutdownSequence.complete(null);
            underTest.start().join();

            verify(pluginManager).beforeStart();
        }
    }

    @Nested
    class Shutdown {
        @Test
        void shouldStopEnabledComponents(@Mock final DrasylNodeComponent drasylNodeComponent) {
            components = List.of(drasylNodeComponent);
            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(final Event event) {
                }
            });
            startSequence.complete(null);
            underTest.shutdown().join();

            verify(drasylNodeComponent).close();
        }

        @Test
        void shouldReturnSameFutureIfShutdownHasAlreadyBeenTriggered() {
            final DrasylNode drasylNode = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(final Event event) {
                }
            });
            assertEquals(shutdownSequence, drasylNode.shutdown());
        }

        @Test
        void shouldSendQuitMessageToAllPeers() {
            final DrasylNode drasylNode = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(final Event event) {
                }
            });
            startSequence.complete(null);
            drasylNode.shutdown().join();

            verify(channelGroup).writeAndFlush(new QuitMessage(REASON_SHUTTING_DOWN));
        }

        @Test
        void shouldStopPlugins() {
            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(final Event event) {
                }
            });
            startSequence.complete(null);
            underTest.shutdown().join();

            verify(pluginManager).afterShutdown();
        }
    }

    @Nested
    class Send {
        private DrasylNode underTest;

        @BeforeEach
        void setUp() {
            underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(final Event event) {
                }
            });
        }

        @Test
        void shouldPassMessageToPipeline(@Mock final CompressedPublicKey myRecipient) {
            underTest.send(myRecipient, new byte[]{ 0x4f });

            verify(pipeline).processOutbound(myRecipient, payload);
        }

        @Test
        void recipientAsStringShouldPassMessageToPipeline() throws CryptoException {
            underTest.send("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458", payload);

            verify(pipeline).processOutbound(
                    CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458"),
                    payload
            );
        }

        @Test
        void payloadAsStringShouldPassMessageToPipeline(@Mock final CompressedPublicKey myRecipient) {
            underTest.send(myRecipient, "Hallo Welt");

            verify(pipeline).processOutbound(
                    myRecipient,
                    "Hallo Welt"
            );
        }

        @Test
        void recipientAndPayloadAsStringShouldPassMessageToPipeline() throws CryptoException {
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
            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(final Event event) {
                }
            });

            assertEquals(pipeline, underTest.pipeline());
        }
    }

    @Nested
    class IdentityMethod {
        @Test
        void shouldReturnIdentity() {
            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, new AtomicBoolean(false), startSequence, shutdownSequence) {
                @Override
                public void onEvent(final Event event) {
                }
            });

            assertEquals(identity, underTest.identity());
        }
    }

    @Nested
    class MessageSink {
        @Test
        void shouldThrowExceptionIfNotRecipient(@Mock final RelayableMessage message) {
            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(final Event event) {
                }
            });
            startSequence.complete(null);
            underTest.shutdown().join();

            assertThrows(ExecutionException.class, () -> underTest.messageSink(message).get());
        }

        @Test
        void shouldPassApplicationMessageToPipeline(@Mock final ApplicationMessage message) {
            when(identity.getPublicKey()).thenReturn(publicKey);
            when(message.getRecipient()).thenReturn(publicKey);

            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(final Event event) {
                }
            });
            underTest.messageSink(message);

            verify(pipeline).processInbound(message);
            verify(peersManager).addPeer(message.getSender());
        }

        @Test
        void shouldReplyToWhoisMessage(@Mock(answer = RETURNS_DEEP_STUBS) final WhoisMessage message) {
            when(identity.getPublicKey()).thenReturn(publicKey);
            when(message.getId()).thenReturn(new MessageId("412176952b5b81fd13f84a7c"));
            when(message.getRecipient()).thenReturn(publicKey);

            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(final Event event) {
                }
            });
            underTest.messageSink(message);

            verify(messenger).send(any(IdentityMessage.class));
        }

        @Test
        void shouldPassIdentityMessageToPeersManager(@Mock(answer = RETURNS_DEEP_STUBS) final IdentityMessage message) {
            when(identity.getPublicKey()).thenReturn(publicKey);
            when(message.getRecipient()).thenReturn(publicKey);

            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, channelGroup, messenger, endpoints, acceptNewConnections, pipeline, components, pluginManager, new AtomicBoolean(true), startSequence, shutdownSequence) {
                @Override
                public void onEvent(final Event event) {
                }
            });
            underTest.messageSink(message);

            verify(peersManager).setPeerInformation(message.getSender(), message.getPeerInformation());
        }
    }
}