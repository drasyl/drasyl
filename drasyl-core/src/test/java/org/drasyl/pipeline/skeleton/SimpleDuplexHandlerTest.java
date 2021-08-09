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
package org.drasyl.pipeline.skeleton;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.remote.protocol.RemoteMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimpleDuplexHandlerTest {
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    private DrasylConfig config;

    @BeforeEach
    void setUp() {
        config = DrasylConfig.newBuilder()
                .networkId(1)
                .build();
    }

    @Nested
    class OutboundTest {
        @Test
        void shouldTriggerOnMatchedMessage(@Mock final IdentityPublicKey sender,
                                           @Mock final IdentityPublicKey recipient) {
            when(identity.getIdentityPublicKey()).thenReturn(sender);
            final byte[] payload = new byte[]{};

            final SimpleDuplexHandler<Object, byte[], IdentityPublicKey> handler = new SimpleDuplexHandler<>() {
                @Override
                protected void matchedInbound(final ChannelHandlerContext ctx,
                                              final IdentityPublicKey sender,
                                              final Object msg) {
                    ctx.fireChannelRead(new AddressedMessage<>(msg, (Address) sender));
                }

                @Override
                protected void matchedOutbound(final ChannelHandlerContext ctx,
                                               final IdentityPublicKey recipient,
                                               final byte[] msg,
                                               final ChannelPromise promise) {
                    // Emit this message as inbound message to test
                    ctx.fireChannelRead(new AddressedMessage<>(msg, identity.getIdentityPublicKey()));
                }
            };

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.pipeline().writeAndFlush(new AddressedMessage<>(payload, recipient));

                assertEquals(new AddressedMessage<>(payload, identity.getIdentityPublicKey()), pipeline.readInbound());
                assertNull(pipeline.readOutbound());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldPassthroughsNotMatchingMessage(@Mock final IdentityPublicKey recipient) {
            final SimpleDuplexHandler<Object, MyMessage, IdentityPublicKey> handler = new SimpleDuplexHandler<>(Object.class, MyMessage.class, IdentityPublicKey.class) {
                @Override
                protected void matchedInbound(final ChannelHandlerContext ctx,
                                              final IdentityPublicKey sender,
                                              final Object msg) {
                    ctx.fireChannelRead(new AddressedMessage<>(msg, (Address) sender));
                }

                @Override
                protected void matchedOutbound(final ChannelHandlerContext ctx,
                                               final IdentityPublicKey recipient,
                                               final MyMessage msg,
                                               final ChannelPromise promise) {
                    // Emit this message as inbound message to test
                    ctx.fireChannelRead(new AddressedMessage<>((Object) msg, (Address) msg.getSender()));
                }
            };

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final byte[] payload = new byte[]{};
                pipeline.pipeline().writeAndFlush(new AddressedMessage<>(payload, recipient));

                assertEquals(new AddressedMessage<>(payload, recipient), pipeline.readOutbound());
                assertNull(pipeline.readInbound());
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class InboundTest {
        @Test
        void shouldTriggerOnMatchedMessage(@Mock final IdentityPublicKey sender) {
            final SimpleDuplexHandler<byte[], Object, Address> handler = new SimpleDuplexHandler<>() {
                @Override
                protected void matchedOutbound(final ChannelHandlerContext ctx,
                                               final Address recipient,
                                               final Object msg,
                                               final ChannelPromise promise) {
                    ctx.writeAndFlush(new AddressedMessage<>(msg, recipient), promise);
                }

                @Override
                protected void matchedInbound(final ChannelHandlerContext ctx,
                                              final Address sender,
                                              final byte[] msg) {
                    // Emit this message as outbound message to test
                    ctx.writeAndFlush(new AddressedMessage<>(msg, sender));
                }
            };

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

                final byte[] msg = new byte[]{};
                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, sender));

                assertEquals(new AddressedMessage<>(msg, sender), pipeline.readOutbound());
                assertNull(pipeline.readInbound());
                eventTestObserver.assertNoValues();
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldPassthroughsNotMatchingMessage(@Mock final RemoteMessage msg,
                                                  @Mock final IdentityPublicKey sender) {
            final SimpleDuplexHandler<List<?>, Object, Address> handler = new SimpleDuplexHandler<>() {
                @Override
                protected void matchedOutbound(final ChannelHandlerContext ctx,
                                               final Address recipient,
                                               final Object msg,
                                               final ChannelPromise promise) {
                    ctx.writeAndFlush(new AddressedMessage<>(msg, recipient), promise);
                }

                @Override
                protected void matchedInbound(final ChannelHandlerContext ctx,
                                              final Address sender,
                                              final List<?> msg) {
                    // Emit this message as outbound message to test
                    ctx.writeAndFlush(new AddressedMessage<>((Object) msg, sender));
                }
            };

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, sender));

                assertEquals(new AddressedMessage<>(msg, sender), pipeline.readInbound());
                assertNull(pipeline.readOutbound());
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    static class MyMessage implements AddressedEnvelope<IdentityPublicKey, Object> {
        @Override
        public IdentityPublicKey getSender() {
            return null;
        }

        @Override
        public IdentityPublicKey getRecipient() {
            return null;
        }

        @Override
        public Object getContent() {
            return null;
        }
    }
}

