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
            CompressedPublicKey sender = mock(CompressedPublicKey.class);
            when(identity.getPublicKey()).thenReturn(sender);
            byte[] payload = new byte[]{};
            CompressedPublicKey recipient = mock(CompressedPublicKey.class);

            SimpleDuplexHandler<Object, Event, byte[]> handler = new SimpleDuplexHandler<>() {
                @Override
                protected void matchedEventTriggered(HandlerContext ctx, Event event,
                                                     CompletableFuture<Void> future) {
                    ctx.fireEventTriggered(event, future);
                }

                @Override
                protected void matchedRead(HandlerContext ctx,
                                           CompressedPublicKey sender,
                                           Object msg,
                                           CompletableFuture<Void> future) {
                    ctx.fireRead(sender, msg, future);
                }

                @Override
                protected void matchedWrite(HandlerContext ctx,
                                            CompressedPublicKey recipient,
                                            byte[] msg,
                                            CompletableFuture<Void> future) {
                    // Emit this message as inbound message to test
                    ctx.pipeline().processInbound(new ApplicationMessage(identity.getPublicKey(), recipient, msg, msg.getClass()));
                }
            };

            EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, TypeValidator.of(config), DefaultCodec.INSTANCE, handler);
            TestObserver<Pair<CompressedPublicKey, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
            TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages().test();
            pipeline.processOutbound(recipient, payload);

            inboundMessageTestObserver.awaitCount(1);
            inboundMessageTestObserver.assertValue(Pair.of(sender, payload));
            outboundMessageTestObserver.assertNoValues();
        }

        @Test
        void shouldPassthroughsNotMatchingMessage() {
            SimpleDuplexHandler<Object, Event, ChunkedMessage> handler = new SimpleDuplexHandler<>(Object.class, Event.class, ChunkedMessage.class) {
                @Override
                protected void matchedEventTriggered(HandlerContext ctx,
                                                     Event event,
                                                     CompletableFuture<Void> future) {
                    ctx.fireEventTriggered(event, future);
                }

                @Override
                protected void matchedRead(HandlerContext ctx,
                                           CompressedPublicKey sender,
                                           Object msg,
                                           CompletableFuture<Void> future) {
                    ctx.fireRead(sender, msg, future);
                }

                @Override
                protected void matchedWrite(HandlerContext ctx,
                                            CompressedPublicKey recipient,
                                            ChunkedMessage msg,
                                            CompletableFuture<Void> future) {
                    // Emit this message as inbound message to test
                    ctx.pipeline().processInbound(msg);
                }
            };

            EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, TypeValidator.of(config), ObjectHolder2ApplicationMessageHandler.INSTANCE, DefaultCodec.INSTANCE, handler);
            TestObserver<Pair<CompressedPublicKey, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
            TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages().test();

            CompressedPublicKey sender = mock(CompressedPublicKey.class);
            CompressedPublicKey recipient = mock(CompressedPublicKey.class);
            when(identity.getPublicKey()).thenReturn(sender);
            byte[] payload = new byte[]{};
            pipeline.processOutbound(recipient, payload);

            outboundMessageTestObserver.awaitCount(1);
            outboundMessageTestObserver.assertValue(new ApplicationMessage(sender, recipient, payload, byte[].class));
            inboundMessageTestObserver.assertNoValues();
        }
    }

    @Nested
    class InboundTest {
        @Test
        void shouldTriggerOnMatchedMessage() throws JsonProcessingException {
            SimpleDuplexHandler<byte[], Event, Object> handler = new SimpleDuplexHandler<>() {
                @Override
                protected void matchedWrite(HandlerContext ctx,
                                            CompressedPublicKey recipient,
                                            Object msg,
                                            CompletableFuture<Void> future) {
                    ctx.write(recipient, msg, future);
                }

                @Override
                protected void matchedEventTriggered(HandlerContext ctx,
                                                     Event event,
                                                     CompletableFuture<Void> future) {
                    super.eventTriggered(ctx, event, future);
                }

                @Override
                protected void matchedRead(HandlerContext ctx,
                                           CompressedPublicKey sender,
                                           byte[] msg,
                                           CompletableFuture<Void> future) {
                    // Emit this message as outbound message to test
                    ctx.pipeline().processOutbound(sender, msg);
                }
            };

            EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, TypeValidator.of(config), ObjectHolder2ApplicationMessageHandler.INSTANCE, DefaultCodec.INSTANCE, handler);
            TestObserver<Pair<CompressedPublicKey, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
            TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages().test();
            TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

            CompressedPublicKey sender = mock(CompressedPublicKey.class);
            when(identity.getPublicKey()).thenReturn(sender);
            byte[] msg = JSONUtil.JACKSON_WRITER.writeValueAsBytes(new byte[]{});
            pipeline.processInbound(new ApplicationMessage(sender, sender, msg, byte[].class));

            outboundMessageTestObserver.awaitCount(1);
            outboundMessageTestObserver.assertValue(new ApplicationMessage(sender, sender, msg, byte[].class));
            inboundMessageTestObserver.assertNoValues();
            eventTestObserver.assertNoValues();
        }

        @Test
        void shouldPassthroughsNotMatchingMessage() {
            SimpleDuplexHandler<List, Event, Object> handler = new SimpleDuplexHandler<>() {
                @Override
                protected void matchedWrite(HandlerContext ctx,
                                            CompressedPublicKey recipient,
                                            Object msg,
                                            CompletableFuture<Void> future) {
                    ctx.write(recipient, msg, future);
                }

                @Override
                protected void matchedEventTriggered(HandlerContext ctx,
                                                     Event event,
                                                     CompletableFuture<Void> future) {
                    ctx.fireEventTriggered(event, future);
                }

                @Override
                protected void matchedRead(HandlerContext ctx,
                                           CompressedPublicKey sender,
                                           List msg,
                                           CompletableFuture<Void> future) {
                    // Emit this message as outbound message to test
                    ctx.pipeline().processOutbound(sender, msg);
                }
            };

            EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, TypeValidator.of(config), ObjectHolder2ApplicationMessageHandler.INSTANCE, DefaultCodec.INSTANCE, handler);
            TestObserver<Pair<CompressedPublicKey, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
            TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages().test();
            TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

            byte[] payload = new byte[]{ 0x01 };
            ApplicationMessage msg = mock(ApplicationMessage.class);

            when(msg.getPayload()).thenReturn(payload);
            doReturn(payload.getClass()).when(msg).getPayloadClazz();

            pipeline.processInbound(msg);

            inboundMessageTestObserver.awaitCount(1);
            inboundMessageTestObserver.assertValue(Pair.of(msg.getSender(), payload));
            eventTestObserver.awaitCount(1);
            eventTestObserver.assertValue(new MessageEvent(Pair.of(msg.getSender(), payload)));
            outboundMessageTestObserver.assertNoValues();
        }

        @Test
        void shouldTriggerOnMatchedEvent() throws InterruptedException {
            SimpleDuplexHandler<ApplicationMessage, NodeUpEvent, Object> handler = new SimpleDuplexHandler<>(ApplicationMessage.class, NodeUpEvent.class, Object.class) {
                @Override
                protected void matchedWrite(HandlerContext ctx,
                                            CompressedPublicKey recipient,
                                            Object msg,
                                            CompletableFuture<Void> future) {
                    ctx.write(recipient, msg, future);
                }

                @Override
                protected void matchedEventTriggered(HandlerContext ctx,
                                                     NodeUpEvent event,
                                                     CompletableFuture<Void> future) {
                    // Do nothing
                }

                @Override
                protected void matchedRead(HandlerContext ctx,
                                           CompressedPublicKey sender,
                                           ApplicationMessage msg,
                                           CompletableFuture<Void> future) {
                    ctx.fireRead(sender, msg, future);
                }
            };

            EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, mock(TypeValidator.class), handler);
            TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

            NodeUpEvent event = mock(NodeUpEvent.class);
            pipeline.processInbound(event);

            eventTestObserver.await(1, TimeUnit.SECONDS);
            eventTestObserver.assertNoValues();
        }

        @Test
        void shouldPassthroughsNotMatchingEvents() {
            SimpleDuplexHandler<ChunkedMessage, NodeUpEvent, Object> handler = new SimpleDuplexHandler<>() {
                @Override
                protected void matchedWrite(HandlerContext ctx,
                                            CompressedPublicKey recipient,
                                            Object msg,
                                            CompletableFuture<Void> future) {
                    ctx.write(recipient, msg, future);
                }

                @Override
                protected void matchedEventTriggered(HandlerContext ctx,
                                                     NodeUpEvent event,
                                                     CompletableFuture<Void> future) {
                    // Do nothing
                }

                @Override
                protected void matchedRead(HandlerContext ctx,
                                           CompressedPublicKey sender,
                                           ChunkedMessage msg,
                                           CompletableFuture<Void> future) {
                    ctx.fireRead(sender, msg, future);
                }
            };

            EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, mock(TypeValidator.class), handler);
            TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

            Event event = mock(Event.class);
            pipeline.processInbound(event);

            eventTestObserver.awaitCount(1);
            eventTestObserver.assertValue(event);
        }
    }
}