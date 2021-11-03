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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import org.drasyl.cli.command.perf.PerfClientNode.DirectConnectionTimeout;
import org.drasyl.cli.command.perf.PerfClientNode.OnlineTimeout;
import org.drasyl.cli.command.perf.PerfClientNode.RequestSessionTimeout;
import org.drasyl.cli.command.perf.PerfClientNode.TestOptions;
import org.drasyl.cli.command.perf.message.Ping;
import org.drasyl.cli.command.perf.message.SessionConfirmation;
import org.drasyl.cli.command.perf.message.SessionRejection;
import org.drasyl.cli.command.perf.message.SessionRequest;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.behaviour.Behaviors;
import org.drasyl.node.event.MessageEvent;
import org.drasyl.node.event.NodeNormalTerminationEvent;
import org.drasyl.node.event.NodeOnlineEvent;
import org.drasyl.node.event.NodeUnrecoverableErrorEvent;
import org.drasyl.node.event.NodeUpEvent;
import org.drasyl.node.event.PeerDirectEvent;
import org.drasyl.node.event.PeerRelayEvent;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
    private EventLoopGroup eventLoopGroup;
    private Set<DrasylAddress> directConnections;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ServerBootstrap bootstrap;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChannelFuture channelFuture;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChannelGroup channels;
    private PerfClientNode underTest;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        printStream = new PrintStream(outputStream, true);
        directConnections = new HashSet<>();
        underTest = new PerfClientNode(doneFuture, printStream, eventLoopGroup, directConnections, identity, bootstrap, channelFuture, channels);
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
                    private IdentityPublicKey server;

                    @BeforeEach
                    void setUp() {
                        underTest.setTestOptions(server, 10, 100, 850, false, false);
                    }

                    @Test
                    void shouldRequestSession(@Mock(answer = RETURNS_DEEP_STUBS) final Channel childChannel,
                                              @Mock final EventLoop eventLoop) {
                        when(channelFuture.channel().eventLoop()).thenReturn(eventLoop);
                        when(childChannel.eventLoop()).thenReturn(eventLoop);
                        doAnswer(invocation -> {
                            invocation.getArgument(0, Runnable.class).run();
                            return null;
                        }).when(eventLoop).execute(any());
                        when(channels.iterator()).thenReturn(Set.of(childChannel).iterator());
                        when(childChannel.remoteAddress()).thenReturn(server);
                        when(channelFuture.channel().isOpen()).thenReturn(true);
                        underTest.onEvent(nodeOnline);

                        verify(childChannel).writeAndFlush(any(SessionRequest.class), any());
                    }
                }

                @Nested
                class WhenDirectConnectionIsRequired {
                    @Mock
                    private IdentityPublicKey server;

                    @BeforeEach
                    void setUp() {
                        underTest.setTestOptions(server, 10, 100, 850, true, false);
                    }

                    @Test
                    void shouldTriggerDirectConnection(@Mock(answer = RETURNS_DEEP_STUBS) final Channel childChannel,
                                                       @Mock final EventLoop eventLoop) {
                        when(channelFuture.channel().eventLoop()).thenReturn(eventLoop);
                        when(childChannel.eventLoop()).thenReturn(eventLoop);
                        doAnswer(invocation -> {
                            invocation.getArgument(0, Runnable.class).run();
                            return null;
                        }).when(eventLoop).execute(any());
                        when(channels.iterator()).thenReturn(Set.of(childChannel).iterator());
                        when(childChannel.remoteAddress()).thenReturn(server);
                        when(channelFuture.channel().isOpen()).thenReturn(true);
                        underTest.onEvent(nodeOnline);

                        verify(childChannel).writeAndFlush(any(Ping.class), any());
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

                    verify(channelFuture.channel().pipeline(), never()).fireUserEventTriggered(any());
                }

                @Nested
                class OnSetServer {
                    @Test
                    void shouldRequestSession(@Mock(answer = RETURNS_DEEP_STUBS) final TestOptions serverAndOptions,
                                              @Mock(answer = RETURNS_DEEP_STUBS) final Channel childChannel,
                                              @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey address,
                                              @Mock final EventLoop eventLoop) {
                        when(channelFuture.channel().eventLoop()).thenReturn(eventLoop);
                        when(childChannel.eventLoop()).thenReturn(eventLoop);
                        doAnswer(invocation -> {
                            invocation.getArgument(0, Runnable.class).run();
                            return null;
                        }).when(eventLoop).execute(any());
                        when(serverAndOptions.getServer()).thenReturn(address);
                        when(channels.iterator()).thenReturn(Set.of(childChannel).iterator());
                        when(childChannel.remoteAddress()).thenReturn(address);
                        when(channelFuture.channel().isOpen()).thenReturn(true);
                        when(serverAndOptions.requireDirectConnection()).thenReturn(true);
                        underTest.onEvent(nodeOnline);
                        underTest.onEvent(serverAndOptions);

                        verify(childChannel).writeAndFlush(any(Ping.class), any());
                    }
                }
            }

            @Nested
            class WhenSessionHasBeenConfirmed {
                @Test
                void shouldRequestSession(@Mock final TestOptions serverAndOptions,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent messageEvent,
                                          @Mock final SessionConfirmation sessionConfirmation,
                                          @Mock final IdentityPublicKey sender,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final Channel childChannel,
                                          @Mock final EventLoop eventLoop) {
                    when(channelFuture.channel().eventLoop()).thenReturn(eventLoop);
                    when(childChannel.eventLoop()).thenReturn(eventLoop);
                    doAnswer(invocation -> {
                        invocation.getArgument(0, Runnable.class).run();
                        return null;
                    }).when(eventLoop).execute(any());
                    when(channels.iterator()).thenReturn(Set.of(childChannel).iterator()).thenReturn(Set.of(childChannel).iterator());
                    when(childChannel.remoteAddress()).thenReturn(sender);
                    when(channelFuture.channel().isOpen()).thenReturn(true);
                    when(serverAndOptions.getMessagesPerSecond()).thenReturn(100);
                    when(serverAndOptions.getTestDuration()).thenReturn(10);
                    when(serverAndOptions.getMessageSize()).thenReturn(850);
                    when(serverAndOptions.getServer()).thenReturn(sender);
                    when(messageEvent.getSender()).thenReturn(sender);
                    when(messageEvent.getPayload()).thenReturn(sessionConfirmation);

                    underTest.onEvent(nodeOnline);
                    underTest.onEvent(serverAndOptions);
                    underTest.onEvent(messageEvent);

                    verify(childChannel).writeAndFlush(any(SessionRequest.class), any());
                }
            }

            @Nested
            class WhenSessionHasBeenRejected {
                @Test
                void shouldCompleteExceptionally(@Mock final TestOptions serverAndOptions,
                                                 @Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent messageEvent,
                                                 @Mock final SessionRejection sessionRejection,
                                                 @Mock final IdentityPublicKey sender) {
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
                                                 @Mock final RequestSessionTimeout requestSessionTimeout,
                                                 @Mock final IdentityPublicKey sender) {
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

            assertThat(directConnections, hasItem(event.getPeer().getAddress()));
        }

        @Test
        void shouldRemovePeerOnRelayEvent(@Mock(answer = RETURNS_DEEP_STUBS) final PeerRelayEvent event) {
            directConnections.add(event.getPeer().getAddress());
            assertEquals(Behaviors.same(), underTest.handlePeerEvent(event));

            assertThat(directConnections, not(hasItem(event.getPeer().getAddress())));
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
        void shouldNotFail(@Mock final IdentityPublicKey server) {
            assertDoesNotThrow(() -> underTest.setTestOptions(server, 10, 100, 850, true, false));
        }
    }
}
