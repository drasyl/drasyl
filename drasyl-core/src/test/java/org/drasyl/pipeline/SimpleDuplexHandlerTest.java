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
package org.drasyl.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ChunkedMessage;
import org.drasyl.pipeline.codec.DefaultCodec;
import org.drasyl.pipeline.codec.ObjectHolder;
import org.drasyl.pipeline.codec.ObjectHolder2ApplicationMessageHandler;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.JSONUtil;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimpleDuplexHandlerTest {
    @Mock
    private Identity identity;
    private DrasylConfig config;

    @BeforeEach
    void setUp() {
        config = DrasylConfig.newBuilder().build();
    }

    @Nested
    class OutboundTest {
        @Test
        void shouldTriggerOnMatchedMessage() {
            final CompressedPublicKey sender = mock(CompressedPublicKey.class);
            when(identity.getPublicKey()).thenReturn(sender);
            final byte[] payload = new byte[]{};
            final CompressedPublicKey recipient = mock(CompressedPublicKey.class);

            final SimpleDuplexHandler<Object, Event, byte[]> handler = new SimpleDuplexHandler<>() {
                @Override
                protected void matchedEventTriggered(final HandlerContext ctx, final Event event,
                                                     final CompletableFuture<Void> future) {
                    ctx.fireEventTriggered(event, future);
                }

                @Override
                protected void matchedRead(final HandlerContext ctx,
                                           final CompressedPublicKey sender,
                                           final Object msg,
                                           final CompletableFuture<Void> future) {
                    ctx.fireRead(sender, msg, future);
                }

                @Override
                protected void matchedWrite(final HandlerContext ctx,
                                            final CompressedPublicKey recipient,
                                            final byte[] msg,
                                            final CompletableFuture<Void> future) {
                    // Emit this message as inbound message to test
                    ctx.pipeline().processInbound(new ApplicationMessage(identity.getPublicKey(), recipient, msg));
                }
            };

            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    identity,
                    TypeValidator.ofInboundValidator(config),
                    TypeValidator.ofOutboundValidator(config),
                    DefaultCodec.INSTANCE, handler);
            final TestObserver<Pair<CompressedPublicKey, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
            final TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages(ApplicationMessage.class).test();
            pipeline.processOutbound(recipient, payload);

            inboundMessageTestObserver.awaitCount(1);
            inboundMessageTestObserver.assertValue(Pair.of(sender, payload));
            outboundMessageTestObserver.assertNoValues();
        }

        @Test
        void shouldPassthroughsNotMatchingMessage() {
            final SimpleDuplexHandler<Object, Event, ChunkedMessage> handler = new SimpleDuplexHandler<>(Object.class, Event.class, ChunkedMessage.class) {
                @Override
                protected void matchedEventTriggered(final HandlerContext ctx,
                                                     final Event event,
                                                     final CompletableFuture<Void> future) {
                    ctx.fireEventTriggered(event, future);
                }

                @Override
                protected void matchedRead(final HandlerContext ctx,
                                           final CompressedPublicKey sender,
                                           final Object msg,
                                           final CompletableFuture<Void> future) {
                    ctx.fireRead(sender, msg, future);
                }

                @Override
                protected void matchedWrite(final HandlerContext ctx,
                                            final CompressedPublicKey recipient,
                                            final ChunkedMessage msg,
                                            final CompletableFuture<Void> future) {
                    // Emit this message as inbound message to test
                    ctx.pipeline().processInbound(msg);
                }
            };

            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    identity,
                    TypeValidator.ofInboundValidator(config),
                    TypeValidator.ofOutboundValidator(config),
                    ObjectHolder2ApplicationMessageHandler.INSTANCE, DefaultCodec.INSTANCE, handler);
            final TestObserver<Pair<CompressedPublicKey, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
            final TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages(ApplicationMessage.class).test();

            final CompressedPublicKey sender = mock(CompressedPublicKey.class);
            final CompressedPublicKey recipient = mock(CompressedPublicKey.class);
            when(identity.getPublicKey()).thenReturn(sender);
            final byte[] payload = new byte[]{};
            pipeline.processOutbound(recipient, payload);

            outboundMessageTestObserver.awaitCount(1);
            outboundMessageTestObserver.assertValue(new ApplicationMessage(sender, recipient, Map.of(ObjectHolder.CLASS_KEY_NAME, byte[].class.getName()), payload));
            inboundMessageTestObserver.assertNoValues();
        }
    }

    @Nested
    class InboundTest {
        @Test
        void shouldTriggerOnMatchedMessage() throws JsonProcessingException {
            final SimpleDuplexHandler<byte[], Event, Object> handler = new SimpleDuplexHandler<>() {
                @Override
                protected void matchedWrite(final HandlerContext ctx,
                                            final CompressedPublicKey recipient,
                                            final Object msg,
                                            final CompletableFuture<Void> future) {
                    ctx.write(recipient, msg, future);
                }

                @Override
                protected void matchedEventTriggered(final HandlerContext ctx,
                                                     final Event event,
                                                     final CompletableFuture<Void> future) {
                    super.eventTriggered(ctx, event, future);
                }

                @Override
                protected void matchedRead(final HandlerContext ctx,
                                           final CompressedPublicKey sender,
                                           final byte[] msg,
                                           final CompletableFuture<Void> future) {
                    // Emit this message as outbound message to test
                    ctx.pipeline().processOutbound(sender, msg);
                }
            };

            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    identity,
                    TypeValidator.ofInboundValidator(config),
                    TypeValidator.ofOutboundValidator(config),
                    ObjectHolder2ApplicationMessageHandler.INSTANCE, DefaultCodec.INSTANCE, handler);
            final TestObserver<Pair<CompressedPublicKey, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
            final TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages(ApplicationMessage.class).test();
            final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

            final CompressedPublicKey sender = mock(CompressedPublicKey.class);
            when(identity.getPublicKey()).thenReturn(sender);
            final byte[] msg = JSONUtil.JACKSON_WRITER.writeValueAsBytes(new byte[]{});
            pipeline.processInbound(new ApplicationMessage(sender, sender, msg));

            outboundMessageTestObserver.awaitCount(1);
            outboundMessageTestObserver.assertValue(new ApplicationMessage(sender, sender, Map.of(ObjectHolder.CLASS_KEY_NAME, byte[].class.getName()), msg));
            inboundMessageTestObserver.assertNoValues();
            eventTestObserver.assertNoValues();
        }

        @Test
        void shouldPassthroughsNotMatchingMessage() {
            final SimpleDuplexHandler<List, Event, Object> handler = new SimpleDuplexHandler<>() {
                @Override
                protected void matchedWrite(final HandlerContext ctx,
                                            final CompressedPublicKey recipient,
                                            final Object msg,
                                            final CompletableFuture<Void> future) {
                    ctx.write(recipient, msg, future);
                }

                @Override
                protected void matchedEventTriggered(final HandlerContext ctx,
                                                     final Event event,
                                                     final CompletableFuture<Void> future) {
                    ctx.fireEventTriggered(event, future);
                }

                @Override
                protected void matchedRead(final HandlerContext ctx,
                                           final CompressedPublicKey sender,
                                           final List msg,
                                           final CompletableFuture<Void> future) {
                    // Emit this message as outbound message to test
                    ctx.pipeline().processOutbound(sender, msg);
                }
            };

            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    identity,
                    TypeValidator.ofInboundValidator(config),
                    TypeValidator.ofOutboundValidator(config),
                    ObjectHolder2ApplicationMessageHandler.INSTANCE, DefaultCodec.INSTANCE, handler);
            final TestObserver<Pair<CompressedPublicKey, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
            final TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages(ApplicationMessage.class).test();
            final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

            final byte[] payload = new byte[]{ 0x01 };
            final ApplicationMessage msg = mock(ApplicationMessage.class);

            when(msg.getPayload()).thenReturn(payload);
            doReturn(payload.getClass().getName()).when(msg).getHeader(ObjectHolder.CLASS_KEY_NAME);

            pipeline.processInbound(msg);

            inboundMessageTestObserver.awaitCount(1);
            inboundMessageTestObserver.assertValue(Pair.of(msg.getSender(), payload));
            eventTestObserver.awaitCount(1);
            eventTestObserver.assertValue(new MessageEvent(msg.getSender(), payload));
            outboundMessageTestObserver.assertNoValues();
        }

        @Test
        void shouldTriggerOnMatchedEvent() throws InterruptedException {
            final SimpleDuplexHandler<ApplicationMessage, NodeUpEvent, Object> handler = new SimpleDuplexHandler<>(ApplicationMessage.class, NodeUpEvent.class, Object.class) {
                @Override
                protected void matchedWrite(final HandlerContext ctx,
                                            final CompressedPublicKey recipient,
                                            final Object msg,
                                            final CompletableFuture<Void> future) {
                    ctx.write(recipient, msg, future);
                }

                @Override
                protected void matchedEventTriggered(final HandlerContext ctx,
                                                     final NodeUpEvent event,
                                                     final CompletableFuture<Void> future) {
                    // Do nothing
                }

                @Override
                protected void matchedRead(final HandlerContext ctx,
                                           final CompressedPublicKey sender,
                                           final ApplicationMessage msg,
                                           final CompletableFuture<Void> future) {
                    ctx.fireRead(sender, msg, future);
                }
            };

            final EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, mock(TypeValidator.class), mock(TypeValidator.class), handler);
            final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

            final NodeUpEvent event = mock(NodeUpEvent.class);
            pipeline.processInbound(event);

            eventTestObserver.await(1, TimeUnit.SECONDS);
            eventTestObserver.assertNoValues();
        }

        @Test
        void shouldPassthroughsNotMatchingEvents() {
            final SimpleDuplexHandler<ChunkedMessage, NodeUpEvent, Object> handler = new SimpleDuplexHandler<>() {
                @Override
                protected void matchedWrite(final HandlerContext ctx,
                                            final CompressedPublicKey recipient,
                                            final Object msg,
                                            final CompletableFuture<Void> future) {
                    ctx.write(recipient, msg, future);
                }

                @Override
                protected void matchedEventTriggered(final HandlerContext ctx,
                                                     final NodeUpEvent event,
                                                     final CompletableFuture<Void> future) {
                    // Do nothing
                }

                @Override
                protected void matchedRead(final HandlerContext ctx,
                                           final CompressedPublicKey sender,
                                           final ChunkedMessage msg,
                                           final CompletableFuture<Void> future) {
                    ctx.fireRead(sender, msg, future);
                }
            };

            final EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, mock(TypeValidator.class), mock(TypeValidator.class), handler);
            final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

            final Event event = mock(Event.class);
            pipeline.processInbound(event);

            eventTestObserver.awaitCount(1);
            eventTestObserver.assertValue(event);
        }
    }
}