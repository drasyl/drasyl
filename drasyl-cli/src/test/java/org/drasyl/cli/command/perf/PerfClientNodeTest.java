/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
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
