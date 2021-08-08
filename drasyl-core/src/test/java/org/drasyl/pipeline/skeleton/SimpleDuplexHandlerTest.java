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
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.channel.MigrationInboundMessage;
import org.drasyl.channel.MigrationOutboundMessage;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.remote.protocol.RemoteMessage;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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
                                              final Object msg,
                                              final CompletableFuture<Void> future) {
                    ctx.fireChannelRead(new MigrationInboundMessage<>(msg, (Address) sender, future));
                }

                @Override
                protected void matchedOutbound(final ChannelHandlerContext ctx,
                                               final IdentityPublicKey recipient,
                                               final byte[] msg,
                                               final CompletableFuture<Void> future) {
                    // Emit this message as inbound message to test
                    ctx.fireChannelRead(new MigrationInboundMessage<>((Object) msg, (Address) identity.getIdentityPublicKey(), new CompletableFuture<Void>()));
                }
            };

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final TestObserver<AddressedEnvelope<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessagesWithSender().test();
                final TestObserver<Object> outboundMessageTestObserver = pipeline.drasylOutboundMessages().test();
                pipeline.pipeline().writeAndFlush(new MigrationOutboundMessage<>((Object) payload, (Address) recipient));

                inboundMessageTestObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new DefaultAddressedEnvelope<>(sender, null, payload));
                outboundMessageTestObserver.assertNoValues();
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
                                              final Object msg,
                                              final CompletableFuture<Void> future) {
                    ctx.fireChannelRead(new MigrationInboundMessage<>(msg, (Address) sender, future));
                }

                @Override
                protected void matchedOutbound(final ChannelHandlerContext ctx,
                                               final IdentityPublicKey recipient,
                                               final MyMessage msg,
                                               final CompletableFuture<Void> future) {
                    // Emit this message as inbound message to test
                    ctx.fireChannelRead(new MigrationInboundMessage<>((Object) msg, (Address) msg.getSender(), new CompletableFuture<Void>()));
                }
            };

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final TestObserver<Object> inboundMessageTestObserver = pipeline.drasylInboundMessages().test();
                final TestObserver<Object> outboundMessageTestObserver = pipeline.drasylOutboundMessages().test();

                final byte[] payload = new byte[]{};
                pipeline.pipeline().writeAndFlush(new MigrationOutboundMessage<>((Object) payload, (Address) recipient));

                outboundMessageTestObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(payload);
                inboundMessageTestObserver.assertNoValues();
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
                                               final CompletableFuture<Void> future) {
                    FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new MigrationOutboundMessage<>(msg, recipient)))).combine(future);
                }

                @Override
                protected void matchedInbound(final ChannelHandlerContext ctx,
                                              final Address sender,
                                              final byte[] msg,
                                              final CompletableFuture<Void> future) {
                    // Emit this message as outbound message to test
                    FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new MigrationOutboundMessage<>((Object) msg, sender)))).combine(new CompletableFuture<Void>());
                }
            };

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final TestObserver<Object> inboundMessageTestObserver = pipeline.drasylInboundMessages().test();
                final TestObserver<Object> outboundMessageTestObserver = pipeline.drasylOutboundMessages().test();
                final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

                final byte[] msg = new byte[]{};
                pipeline.pipeline().fireChannelRead(new MigrationInboundMessage<>((Object) msg, (Address) sender));

                outboundMessageTestObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(msg);
                inboundMessageTestObserver.assertNoValues();
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
                                               final CompletableFuture<Void> future) {
                    FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new MigrationOutboundMessage<>(msg, recipient)))).combine(future);
                }

                @Override
                protected void matchedInbound(final ChannelHandlerContext ctx,
                                              final Address sender,
                                              final List<?> msg,
                                              final CompletableFuture<Void> future) {
                    // Emit this message as outbound message to test
                    FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new MigrationOutboundMessage<>((Object) msg, sender)))).combine(new CompletableFuture<Void>());
                }
            };

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final TestObserver<AddressedEnvelope<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessagesWithSender().test();
                final TestObserver<RemoteMessage> outboundMessageTestObserver = pipeline.drasylOutboundMessages(RemoteMessage.class).test();
                final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

                pipeline.pipeline().fireChannelRead(new MigrationInboundMessage<>((Object) msg, (Address) sender));

                inboundMessageTestObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new DefaultAddressedEnvelope<>(sender, null, msg));
                eventTestObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(MessageEvent.of(sender, msg));
                outboundMessageTestObserver.assertNoValues();
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

