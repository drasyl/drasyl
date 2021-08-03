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

import io.netty.channel.ChannelFuture;
import org.drasyl.channel.DrasylBootstrap;
import org.drasyl.cli.command.wormhole.ReceivingWormholeNode.OnlineTimeout;
import org.drasyl.cli.command.wormhole.ReceivingWormholeNode.RequestText;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceivingWormholeNodeTest {
    private ByteArrayOutputStream outStream;
    private PrintStream out;
    @Mock
    private CompletableFuture<Void> doneFuture;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private RequestText request;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylBootstrap bootstrap;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private PluginManager pluginManager;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChannelFuture channelFuture;
    private ReceivingWormholeNode underTest;

    @BeforeEach
    void setUp() {
        outStream = new ByteArrayOutputStream();
        out = new PrintStream(outStream, true);
        underTest = new ReceivingWormholeNode(doneFuture, out, request, bootstrap, pluginManager, channelFuture);
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

            @Nested
            class OnMessageEvent {
                @Test
                void shouldPrintTextOnTextMessage(@Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent event,
                                                  @Mock final IdentityPublicKey publicKey) {
                    when(event.getPayload()).thenReturn(new TextMessage("Hi"));
                    when(event.getSender()).thenReturn(publicKey);
                    when(request.getSender()).thenReturn(publicKey);

                    underTest.onEvent(nodeOnline);
                    underTest.onEvent(event);

                    final String output = outStream.toString();
                    assertThat(output, containsString("Hi"));
                }

                @Test
                void shouldFailOnWrongPasswordMessage(@Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent event,
                                                      @Mock final IdentityPublicKey publicKey) {
                    when(channelFuture.channel().isOpen()).thenReturn(true);
                    when(event.getPayload()).thenReturn(new WrongPasswordMessage());
                    when(event.getSender()).thenReturn(publicKey);
                    when(request.getSender()).thenReturn(publicKey);

                    underTest.onEvent(nodeOnline);
                    underTest.onEvent(event);

                    verify(doneFuture).completeExceptionally(any());
                }
            }

            @Nested
            class OnRequestText {
                @Test
                void shouldRequestText(@Mock(answer = RETURNS_DEEP_STUBS) final RequestText event) {
                    when(channelFuture.channel().isOpen()).thenReturn(true);

                    underTest = new ReceivingWormholeNode(doneFuture, out, null, bootstrap, pluginManager, channelFuture);

                    underTest.onEvent(nodeOnline);
                    underTest.onEvent(event);

                    verify(channelFuture.channel().pipeline()).fireUserEventTriggered(any());
                }
            }
        }

        @Nested
        class OnRequestText {
            @Test
            void shouldNotRequestTextBecauseNotOffline(@Mock(answer = RETURNS_DEEP_STUBS) final RequestText event) {
                underTest = new ReceivingWormholeNode(doneFuture, out, null, bootstrap, pluginManager, channelFuture);

                underTest.onEvent(event);

                verify(channelFuture.channel().pipeline(), never()).fireUserEventTriggered(any());
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
    class TestRequestText {
        @Test
        void shouldNotFail(@Mock final IdentityPublicKey sender) {
            assertDoesNotThrow(() -> underTest.requestText(sender, "s3cr3t"));
        }
    }
}
