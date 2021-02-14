/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.cli.command.perf;

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.behaviour.Behaviors;
import org.drasyl.cli.command.perf.PerfClientNode.DirectConnectionTimeout;
import org.drasyl.cli.command.perf.PerfClientNode.OnlineTimeout;
import org.drasyl.cli.command.perf.PerfClientNode.RequestSessionTimeout;
import org.drasyl.cli.command.perf.PerfClientNode.TestOptions;
import org.drasyl.cli.command.perf.message.SessionConfirmation;
import org.drasyl.cli.command.perf.message.SessionRejection;
import org.drasyl.cli.command.perf.message.SessionRequest;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.event.PeerRelayEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfClientNodeTest {
    @SuppressWarnings("FieldCanBeLocal")
    private ByteArrayOutputStream outputStream;
    @SuppressWarnings("FieldCanBeLocal")
    private PrintStream printStream;
    @Mock
    private CompletableFuture<Void> doneFuture;
    @Mock
    private Scheduler perfScheduler;
    private Set<CompressedPublicKey> directConnections;
    @Mock
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    private final AtomicReference<CompletableFuture<Void>> startFuture = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> shutdownFuture = new AtomicReference<>();
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Pipeline pipeline;
    @Mock
    private PluginManager pluginManager;
    @Mock
    private Scheduler scheduler;
    private PerfClientNode underTest;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        printStream = new PrintStream(outputStream, true);
        directConnections = new HashSet<>();
        underTest = new PerfClientNode(doneFuture, printStream, perfScheduler, directConnections, config, identity, peersManager, pipeline, pluginManager, startFuture, shutdownFuture, scheduler);
    }

    @Nested
    class OnEvent {
        @Nested
        class OnNodeUnrecoverableErrorEvent {
            @Test
            void shouldCompleteExceptionally(@Mock final NodeUnrecoverableErrorEvent event) {
                underTest.onEvent(event);

                verify(doneFuture).completeExceptionally(any());
            }
        }

        @Nested
        class OnNodeNormalTerminationEvent {
            @Test
            void shouldComplete(@Mock final NodeNormalTerminationEvent event) {
                underTest.onEvent(event);

                verify(doneFuture).complete(null);
            }
        }

        @Nested
        class OnNodeUpEvent {
            @Test
            void shouldNotFail(@Mock final NodeUpEvent event) {
                assertDoesNotThrow(() -> underTest.onEvent(event));
            }
        }

        @Nested
        class OnNodeOnlineEvent {
            @Mock
            private NodeOnlineEvent nodeOnline;

            @Nested
            class WhenServerIsKnown {
                @Nested
                class WhenDirectConnectionIsNotRequired {
                    @Mock
                    private CompressedPublicKey server;

                    @BeforeEach
                    void setUp() {
                        underTest.setTestOptions(server, 10, 100, 850, false, false);
                    }

                    @Test
                    void shouldRequestSession() {
                        underTest.onEvent(nodeOnline);

                        verify(pipeline).processOutbound(any(), any(SessionRequest.class));
                    }
                }

                @Nested
                class WhenDirectConnectionIsRequired {
                    @Mock
                    private CompressedPublicKey server;

                    @BeforeEach
                    void setUp() {
                        underTest.setTestOptions(server, 10, 100, 850, true, false);
                    }

                    @Test
                    void shouldTriggerDirectConnection() {
                        underTest.onEvent(nodeOnline);

                        verify(pipeline).processOutbound(any(), eq(new byte[0]));
                    }

                    @Test
                    void shouldFailOnDirectConnectionTimeout(@Mock final DirectConnectionTimeout directConnectionTimeout) {
                        underTest.onEvent(nodeOnline);
                        underTest.onEvent(directConnectionTimeout);

                        verify(doneFuture).completeExceptionally(any());
                    }
                }
            }

            @Nested
            class WhenServerIsNotKnown {
                @Test
                void shouldWaitForServer() {
                    underTest.onEvent(nodeOnline);

                    verify(pipeline, never()).processOutbound(any(), any());
                }

                @Nested
                class OnSetServer {
                    @Test
                    void shouldRequestSession(@Mock final TestOptions serverAndOptions) {
                        when(serverAndOptions.requireDirectConnection()).thenReturn(true);
                        underTest.onEvent(nodeOnline);
                        underTest.onEvent(serverAndOptions);

                        verify(pipeline).processOutbound(any(), eq(new byte[0]));
                    }
                }
            }

            @Nested
            class WhenSessionHasBeenConfirmed {
                @Test
                void shouldRequestSession(@Mock final TestOptions serverAndOptions,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent messageEvent,
                                          @Mock final SessionConfirmation sessionConfirmation,
                                          @Mock final CompressedPublicKey sender) {
                    when(serverAndOptions.getMessagesPerSecond()).thenReturn(100);
                    when(serverAndOptions.getTestDuration()).thenReturn(10);
                    when(serverAndOptions.getMessageSize()).thenReturn(850);
                    when(serverAndOptions.getServer()).thenReturn(sender);
                    when(messageEvent.getSender()).thenReturn(sender);
                    when(messageEvent.getPayload()).thenReturn(sessionConfirmation);

                    underTest.onEvent(nodeOnline);
                    underTest.onEvent(serverAndOptions);
                    underTest.onEvent(messageEvent);

                    verify(pipeline).processOutbound(any(), any(SessionRequest.class));
                }
            }

            @Nested
            class WhenSessionHasBeenRejected {
                @Test
                void shouldCompleteExceptionally(@Mock final TestOptions serverAndOptions,
                                                 @Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent messageEvent,
                                                 @Mock final SessionRejection sessionRejection,
                                                 @Mock final CompressedPublicKey sender) {
                    when(serverAndOptions.getMessagesPerSecond()).thenReturn(100);
                    when(serverAndOptions.getTestDuration()).thenReturn(10);
                    when(serverAndOptions.getMessageSize()).thenReturn(850);
                    when(serverAndOptions.getServer()).thenReturn(sender);
                    when(messageEvent.getSender()).thenReturn(sender);
                    when(messageEvent.getPayload()).thenReturn(sessionRejection);

                    underTest.onEvent(nodeOnline);
                    underTest.onEvent(serverAndOptions);
                    underTest.onEvent(messageEvent);

                    verify(doneFuture).completeExceptionally(any());
                }
            }

            @Nested
            class WhenSessionRequestTimeout {
                @Test
                void shouldCompleteExceptionally(@Mock final TestOptions serverAndOptions,
                                                 @Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent messageEvent,
                                                 @Mock final RequestSessionTimeout requestSessionTimeout,
                                                 @Mock final CompressedPublicKey sender) {
                    when(serverAndOptions.getMessagesPerSecond()).thenReturn(100);
                    when(serverAndOptions.getTestDuration()).thenReturn(10);
                    when(serverAndOptions.getMessageSize()).thenReturn(850);
                    when(serverAndOptions.getServer()).thenReturn(sender);

                    underTest.onEvent(nodeOnline);
                    underTest.onEvent(serverAndOptions);
                    underTest.onEvent(requestSessionTimeout);

                    verify(doneFuture).completeExceptionally(any());
                }
            }
        }

        @Nested
        class OnOnlineTimeout {
            @Test
            void shouldComplete(@Mock(answer = RETURNS_DEEP_STUBS) final OnlineTimeout event) {
                underTest.onEvent(event);

                verify(doneFuture).completeExceptionally(any());
            }
        }
    }

    @Nested
    class handlePeerEvent {
        @Test
        void shouldAddPeerOnDirectEvent(@Mock(answer = RETURNS_DEEP_STUBS) final PeerDirectEvent event) {
            assertEquals(Behaviors.same(), underTest.handlePeerEvent(event));

            assertThat(directConnections, hasItem(event.getPeer().getPublicKey()));
        }

        @Test
        void shouldRemovePeerOnRelayEvent(@Mock(answer = RETURNS_DEEP_STUBS) final PeerRelayEvent event) {
            directConnections.add(event.getPeer().getPublicKey());
            assertEquals(Behaviors.same(), underTest.handlePeerEvent(event));

            assertThat(directConnections, not(hasItem(event.getPeer().getPublicKey())));
        }
    }

    @Nested
    class DoneFuture {
        @Test
        void shouldReturnDoneFuture() {
            assertEquals(doneFuture, underTest.doneFuture());
        }
    }

    @Nested
    class TestTestOptions {
        @Test
        void shouldNotFail(@Mock final CompressedPublicKey server) {
            assertDoesNotThrow(() -> underTest.setTestOptions(server, 10, 100, 850, true, false));
        }
    }
}
