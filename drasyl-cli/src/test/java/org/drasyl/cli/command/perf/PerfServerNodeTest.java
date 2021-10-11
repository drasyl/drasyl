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
import org.drasyl.cli.command.perf.PerfServerNode.OnlineTimeout;
import org.drasyl.cli.command.perf.message.SessionConfirmation;
import org.drasyl.cli.command.perf.message.SessionRequest;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.event.MessageEvent;
import org.drasyl.node.event.NodeNormalTerminationEvent;
import org.drasyl.node.event.NodeOfflineEvent;
import org.drasyl.node.event.NodeOnlineEvent;
import org.drasyl.node.event.NodeUnrecoverableErrorEvent;
import org.drasyl.node.event.NodeUpEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfServerNodeTest {
    @SuppressWarnings("FieldCanBeLocal")
    private ByteArrayOutputStream outputStream;
    @SuppressWarnings("FieldCanBeLocal")
    private PrintStream printStream;
    @Mock
    private CompletableFuture<Void> doneFuture;
    @Mock
    private EventLoopGroup eventLoopGroup;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ServerBootstrap bootstrap;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChannelFuture channelFuture;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChannelGroup channels;
    private PerfServerNode underTest;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        printStream = new PrintStream(outputStream, true);
        underTest = new PerfServerNode(doneFuture, printStream, eventLoopGroup, identity, bootstrap, channelFuture, channels);
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

            @Test
            void shouldPrintListeningAddress() {
                underTest.onEvent(nodeOnline);

                final String output = outputStream.toString();
                assertThat(output, containsString("Server listening on address"));
            }

            @Nested
            class WhenWaitingForSession {
                @Test
                void shouldConfirmRequest(@Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent messageEvent,
                                          @Mock final SessionRequest sessionRequest,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final Channel childChannel,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey publicKey,
                                          @Mock final EventLoop eventLoop) {
                    when(channelFuture.channel().eventLoop()).thenReturn(eventLoop);
                    doAnswer(invocation -> {
                        invocation.getArgument(0, Runnable.class).run();
                        return null;
                    }).when(eventLoop).execute(any());
                    when(channels.iterator()).thenReturn(Set.of(childChannel).iterator());
                    when(childChannel.remoteAddress()).thenReturn(publicKey);
                    when(messageEvent.getSender()).thenReturn(publicKey);
                    when(channelFuture.channel().isOpen()).thenReturn(true);
                    when(messageEvent.getPayload()).thenReturn(sessionRequest);

                    underTest.onEvent(nodeOnline);
                    underTest.onEvent(messageEvent);

                    verify(childChannel).writeAndFlush(any(SessionConfirmation.class), any());
                }

                @Test
                void shouldPrintInfoOnOfflineEvent(@Mock final NodeOfflineEvent event) {
                    underTest.onEvent(nodeOnline);
                    underTest.onEvent(event);

                    final String output = outputStream.toString();
                    assertThat(output, containsString("Lost connection to super peer"));
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
        class OnOnlineTimeout {
            @Test
            void shouldComplete(@Mock(answer = RETURNS_DEEP_STUBS) final OnlineTimeout event) {
                underTest.onEvent(event);

                verify(doneFuture).completeExceptionally(any());
            }
        }
    }

    @Nested
    class DoneFuture {
        @Test
        void shouldReturnDoneFuture() {
            assertEquals(doneFuture, underTest.doneFuture());
        }
    }
}
