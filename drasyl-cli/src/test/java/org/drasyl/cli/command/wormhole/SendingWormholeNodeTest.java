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
package org.drasyl.cli.command.wormhole;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.channel.group.ChannelGroup;
import org.drasyl.cli.command.wormhole.SendingWormholeNode.OnlineTimeout;
import org.drasyl.cli.command.wormhole.SendingWormholeNode.SetText;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.event.MessageEvent;
import org.drasyl.node.event.NodeNormalTerminationEvent;
import org.drasyl.node.event.NodeOnlineEvent;
import org.drasyl.node.event.NodeUnrecoverableErrorEvent;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendingWormholeNodeTest {
    @Mock
    private CompletableFuture<Void> doneFuture;
    @SuppressWarnings("FieldCanBeLocal")
    private ByteArrayOutputStream outStream;
    @SuppressWarnings("FieldCanBeLocal")
    private PrintStream out;
    @SuppressWarnings("FieldCanBeLocal")
    private final String password = "123";
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ServerBootstrap bootstrap;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChannelFuture channelFuture;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChannelGroup channels;
    private SendingWormholeNode underTest;

    @BeforeEach
    void setUp() {
        outStream = new ByteArrayOutputStream();
        out = new PrintStream(outStream, true);
        underTest = new SendingWormholeNode(doneFuture, out, password, identity, bootstrap, channelFuture, channels);
    }

    @Nested
    class OnEvent {
        @Nested
        class OnNodeUnrecoverableErrorEvent {
            @Test
            void shouldCompleteExceptionally(@Mock(answer = RETURNS_DEEP_STUBS) final NodeUnrecoverableErrorEvent event) {
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
        class OnNodeOnlineEvent {
            @Mock(answer = RETURNS_DEEP_STUBS)
            private NodeOnlineEvent nodeOnline;

            @Test
            void shouldSendTextOnPasswordMessageWithCorrectPassword(@Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent event,
                                                                    @Mock(answer = RETURNS_DEEP_STUBS) final Channel childChannel,
                                                                    @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey publicKey,
                                                                    @Mock final EventLoop eventLoop) {
                when(channelFuture.channel().eventLoop()).thenReturn(eventLoop);
                when(childChannel.eventLoop()).thenReturn(eventLoop);
                doAnswer(invocation -> {
                    invocation.getArgument(0, Runnable.class).run();
                    return null;
                }).when(eventLoop).execute(any());
                when(channels.iterator()).thenReturn(Set.of(childChannel).iterator());
                when(childChannel.remoteAddress()).thenReturn(publicKey);
                when(channelFuture.channel().isOpen()).thenReturn(true);
                when(event.getPayload()).thenReturn(new PasswordMessage("123"));
                when(event.getSender()).thenReturn(publicKey);
                underTest.setText("Hi");

                underTest.onEvent(nodeOnline);
                underTest.onEvent(event);

                verify(childChannel).writeAndFlush(any(TextMessage.class), any());
            }

            @Test
            void shouldSendTextOnPasswordMessageWithWrongPassword(@Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent event,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final Channel childChannel,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey publicKey,
                                                                  @Mock final EventLoop eventLoop) {
                when(channelFuture.channel().eventLoop()).thenReturn(eventLoop);
                when(childChannel.eventLoop()).thenReturn(eventLoop);
                doAnswer(invocation -> {
                    invocation.getArgument(0, Runnable.class).run();
                    return null;
                }).when(eventLoop).execute(any());
                when(channels.iterator()).thenReturn(Set.of(childChannel).iterator());
                when(childChannel.remoteAddress()).thenReturn(publicKey);
                when(channelFuture.channel().isOpen()).thenReturn(true);
                when(event.getPayload()).thenReturn(new PasswordMessage("456"));
                when(event.getSender()).thenReturn(publicKey);
                underTest.setText("Hi");

                underTest.onEvent(nodeOnline);
                underTest.onEvent(event);

                verify(childChannel).writeAndFlush(any(WrongPasswordMessage.class), any());
            }

            @Nested
            class OnSetText {
                @Test
                void shouldNotFail(@Mock(answer = RETURNS_DEEP_STUBS) final SetText event) {
                    assertDoesNotThrow(() -> {
                        underTest.onEvent(nodeOnline);
                        underTest.onEvent(event);
                    });
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

    @Nested
    class TestSetText {
        @Test
        void shouldNotFail() {
            assertDoesNotThrow(() -> underTest.setText("Hello you!"));
        }
    }
}
