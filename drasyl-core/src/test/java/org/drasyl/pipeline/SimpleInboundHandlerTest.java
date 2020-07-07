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

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ChunkedMessage;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class SimpleInboundHandlerTest {
    @Test
    void shouldTriggerOnMatchedMessage() {
        SimpleInboundHandler<ChunkedMessage, Event> handler = new SimpleInboundHandler<>() {
            @Override
            protected void matchedEventTriggered(HandlerContext ctx, Event event) {
                super.eventTriggered(ctx, event);
            }

            @Override
            protected void matchedRead(HandlerContext ctx, ChunkedMessage msg) {
                // Emit this message as outbound message to test
                ctx.pipeline().executeOutbound(msg);
            }
        };

        EmbeddedPipeline pipeline = new EmbeddedPipeline(handler);
        TestObserver<ApplicationMessage> inboundMessageTestObserver = pipeline.inboundMessages().test();
        TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages().test();
        TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

        ChunkedMessage msg = mock(ChunkedMessage.class);
        pipeline.executeInbound(msg);

        outboundMessageTestObserver.awaitCount(1);
        outboundMessageTestObserver.assertValue(msg);
        inboundMessageTestObserver.assertNoValues();
        eventTestObserver.assertNoValues();
    }

    @Test
    void shouldPassthroughsNotMatchingMessage() {
        SimpleInboundHandler<ChunkedMessage, Event> handler = new SimpleInboundHandler<>() {
            @Override
            protected void matchedEventTriggered(HandlerContext ctx, Event event) {
                ctx.fireEventTriggered(event);
            }

            @Override
            protected void matchedRead(HandlerContext ctx, ChunkedMessage msg) {
                // Emit this message as outbound message to test
                ctx.pipeline().executeOutbound(msg);
            }
        };

        EmbeddedPipeline pipeline = new EmbeddedPipeline(handler);
        TestObserver<ApplicationMessage> inboundMessageTestObserver = pipeline.inboundMessages().test();
        TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages().test();
        TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

        ApplicationMessage msg = mock(ApplicationMessage.class);
        pipeline.executeInbound(msg);

        inboundMessageTestObserver.awaitCount(1);
        inboundMessageTestObserver.assertValue(msg);
        eventTestObserver.awaitCount(1);
        eventTestObserver.assertValue(new MessageEvent(Pair.of(msg.getSender(), msg.getPayload())));
        outboundMessageTestObserver.assertNoValues();
    }

    @Test
    void shouldTriggerOnMatchedEvent() throws InterruptedException {
        SimpleInboundHandler<ApplicationMessage, NodeUpEvent> handler = new SimpleInboundHandler<>(ApplicationMessage.class, NodeUpEvent.class) {
            @Override
            protected void matchedEventTriggered(HandlerContext ctx, NodeUpEvent event) {
                // Do nothing
            }

            @Override
            protected void matchedRead(HandlerContext ctx, ApplicationMessage msg) {
                ctx.fireRead(msg);
            }
        };

        EmbeddedPipeline pipeline = new EmbeddedPipeline(handler);
        TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

        NodeUpEvent event = mock(NodeUpEvent.class);
        pipeline.executeInbound(event);

        eventTestObserver.await(1, TimeUnit.SECONDS);
        eventTestObserver.assertNoValues();
    }

    @Test
    void shouldPassthroughsNotMatchingEvents() {
        SimpleInboundHandler<ChunkedMessage, NodeUpEvent> handler = new SimpleInboundHandler<>() {
            @Override
            protected void matchedEventTriggered(HandlerContext ctx, NodeUpEvent event) {
                // Do nothing
            }

            @Override
            protected void matchedRead(HandlerContext ctx, ChunkedMessage msg) {
                ctx.fireRead(msg);
            }
        };

        EmbeddedPipeline pipeline = new EmbeddedPipeline(handler);
        TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

        Event event = mock(Event.class);
        pipeline.executeInbound(event);

        eventTestObserver.awaitCount(1);
        eventTestObserver.assertValue(event);
    }
}