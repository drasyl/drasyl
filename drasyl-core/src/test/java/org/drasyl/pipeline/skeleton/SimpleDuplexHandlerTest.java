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

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.HandlerMask;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimpleDuplexHandlerTest {
    private final int networkId = 1;
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
                protected void matchedEvent(final HandlerContext ctx, final Event event,
                                            final CompletableFuture<Void> future) {
                    ctx.passEvent(event, future);
                }

                @Override
                protected void matchedInbound(final HandlerContext ctx,
                                              final IdentityPublicKey sender,
                                              final Object msg,
                                              final CompletableFuture<Void> future) {
                    ctx.passInbound(sender, msg, future);
                }

                @Override
                protected void matchedOutbound(final HandlerContext ctx,
                                               final IdentityPublicKey recipient,
                                               final byte[] msg,
                                               final CompletableFuture<Void> future) {
                    // Emit this message as inbound message to test
                    ctx.pipeline().processInbound(identity.getIdentityPublicKey(), msg);
                }
            };

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<AddressedEnvelope<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessagesWithSender().test();
                final TestObserver<Object> outboundMessageTestObserver = pipeline.outboundMessages().test();
                pipeline.processOutbound(recipient, payload);

                inboundMessageTestObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new DefaultAddressedEnvelope<>(sender, null, payload));
                outboundMessageTestObserver.assertNoValues();
            }
        }

        @Test
        void shouldPassthroughsNotMatchingMessage(@Mock final IdentityPublicKey sender,
                                                  @Mock final IdentityPublicKey recipient) {
            final SimpleDuplexEventAwareHandler<Object, Event, MyMessage, IdentityPublicKey> handler = new SimpleDuplexEventAwareHandler<>(Object.class, Event.class, MyMessage.class, IdentityPublicKey.class) {
                @Override
                protected void matchedEvent(final HandlerContext ctx,
                                            final Event event,
                                            final CompletableFuture<Void> future) {
                    ctx.passEvent(event, future);
                }

                @Override
                protected void matchedInbound(final HandlerContext ctx,
                                              final IdentityPublicKey sender,
                                              final Object msg,
                                              final CompletableFuture<Void> future) {
                    ctx.passInbound(sender, msg, future);
                }

                @Override
                protected void matchedOutbound(final HandlerContext ctx,
                                               final IdentityPublicKey recipient,
                                               final MyMessage msg,
                                               final CompletableFuture<Void> future) {
                    // Emit this message as inbound message to test
                    ctx.pipeline().processInbound(msg.getSender(), msg);
                }
            };

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Object> inboundMessageTestObserver = pipeline.inboundMessages().test();
                final TestObserver<Object> outboundMessageTestObserver = pipeline.outboundMessages().test();

                final byte[] payload = new byte[]{};
                pipeline.processOutbound(recipient, payload);

                outboundMessageTestObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(payload);
                inboundMessageTestObserver.assertNoValues();
            }
        }
    }

    @Nested
    class InboundTest {
        @Test
        void shouldTriggerOnMatchedMessage(@Mock final IdentityPublicKey sender) {
            final SimpleDuplexEventAwareHandler<byte[], Event, Object, Address> handler = new SimpleDuplexEventAwareHandler<>() {
                @Override
                protected void matchedOutbound(final HandlerContext ctx,
                                               final Address recipient,
                                               final Object msg,
                                               final CompletableFuture<Void> future) {
                    ctx.passOutbound(recipient, msg, future);
                }

                @Override
                protected void matchedEvent(final HandlerContext ctx,
                                            final Event event,
                                            final CompletableFuture<Void> future) {
                    super.onEvent(ctx, event, future);
                }

                @Override
                protected void matchedInbound(final HandlerContext ctx,
                                              final Address sender,
                                              final byte[] msg,
                                              final CompletableFuture<Void> future) {
                    // Emit this message as outbound message to test
                    ctx.pipeline().processOutbound(sender, msg);
                }
            };

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Object> inboundMessageTestObserver = pipeline.inboundMessages().test();
                final TestObserver<Object> outboundMessageTestObserver = pipeline.outboundMessages().test();
                final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

                final byte[] msg = new byte[]{};
                pipeline.processInbound(sender, msg);

                outboundMessageTestObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(msg);
                inboundMessageTestObserver.assertNoValues();
                eventTestObserver.assertNoValues();
            }
        }

        @SuppressWarnings("rawtypes")
        @Test
        void shouldPassthroughsNotMatchingMessage(@Mock final RemoteEnvelope msg,
                                                  @Mock final IdentityPublicKey sender) {
            final SimpleDuplexHandler<List<?>, Object, Address> handler = new SimpleDuplexHandler<>() {
                @Override
                protected void matchedOutbound(final HandlerContext ctx,
                                               final Address recipient,
                                               final Object msg,
                                               final CompletableFuture<Void> future) {
                    ctx.passOutbound(recipient, msg, future);
                }

                @Override
                protected void matchedEvent(final HandlerContext ctx,
                                            final Event event,
                                            final CompletableFuture<Void> future) {
                    ctx.passEvent(event, future);
                }

                @Override
                protected void matchedInbound(final HandlerContext ctx,
                                              final Address sender,
                                              final List<?> msg,
                                              final CompletableFuture<Void> future) {
                    // Emit this message as outbound message to test
                    ctx.pipeline().processOutbound(sender, msg);
                }
            };

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<AddressedEnvelope<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessagesWithSender().test();
                final TestObserver<RemoteEnvelope> outboundMessageTestObserver = pipeline.outboundMessages(RemoteEnvelope.class).test();
                final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

                pipeline.processInbound(sender, msg);

                inboundMessageTestObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new DefaultAddressedEnvelope<>(sender, null, msg));
                eventTestObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(MessageEvent.of(sender, msg));
                outboundMessageTestObserver.assertNoValues();
            }
        }

        @SuppressWarnings("rawtypes")
        @Test
        void shouldTriggerOnMatchedEvent(@Mock final NodeUpEvent event) throws InterruptedException {
            final SimpleDuplexEventAwareHandler<RemoteEnvelope, NodeUpEvent, Object, Address> handler = new SimpleDuplexEventAwareHandler<>(RemoteEnvelope.class, NodeUpEvent.class, Object.class, IdentityPublicKey.class) {
                @Override
                protected void matchedOutbound(final HandlerContext ctx,
                                               final Address recipient,
                                               final Object msg,
                                               final CompletableFuture<Void> future) {
                    ctx.passOutbound(recipient, msg, future);
                }

                @Override
                protected void matchedEvent(final HandlerContext ctx,
                                            final NodeUpEvent event,
                                            final CompletableFuture<Void> future) {
                    // Do nothing
                }

                @Override
                protected void matchedInbound(final HandlerContext ctx,
                                              final Address sender,
                                              final RemoteEnvelope msg,
                                              final CompletableFuture<Void> future) {
                    ctx.passInbound(sender, msg, future);
                }
            };

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

                pipeline.processInbound(event);

                eventTestObserver.await(1, TimeUnit.SECONDS);
                eventTestObserver.assertNoValues();
            }
        }

        @Test
        void shouldPassthroughsNotMatchingEvents(@Mock final Event event) {
            final SimpleDuplexEventAwareHandler<MyMessage, NodeUpEvent, Object, Address> handler = new SimpleDuplexEventAwareHandler<>() {
                @Override
                protected void matchedOutbound(final HandlerContext ctx,
                                               final Address recipient,
                                               final Object msg,
                                               final CompletableFuture<Void> future) {
                    ctx.passOutbound(recipient, msg, future);
                }

                @Override
                protected void matchedEvent(final HandlerContext ctx,
                                            final NodeUpEvent event,
                                            final CompletableFuture<Void> future) {
                    // Do nothing
                }

                @Override
                protected void matchedInbound(final HandlerContext ctx,
                                              final Address sender,
                                              final MyMessage msg,
                                              final CompletableFuture<Void> future) {
                    ctx.passInbound(sender, msg, future);
                }
            };

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

                pipeline.processInbound(event);

                eventTestObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(event);
            }
        }

        @Test
        void shouldReturnCorrectHandlerMask() {
            final int mask = HandlerMask.ALL
                    & ~HandlerMask.ON_EXCEPTION_MASK
                    & ~HandlerMask.ON_EVENT_MASK;

            assertEquals(mask, HandlerMask.mask(SimpleDuplexHandler.class));
        }

        @Test
        void shouldReturnCorrectHandlerMaskForEventAwareHandler() {
            final int mask = HandlerMask.ALL
                    & ~HandlerMask.ON_EXCEPTION_MASK;

            assertEquals(mask, HandlerMask.mask(SimpleDuplexEventAwareHandler.class));
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

