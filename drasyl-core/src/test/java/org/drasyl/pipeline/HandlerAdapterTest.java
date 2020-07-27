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
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.pipeline.codec.ObjectHolder;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandlerAdapterTest {
    @Mock
    private HandlerContext ctx;
    @Mock
    private Identity identity;
    @Mock
    private TypeValidator validator;
    @Mock
    private CompletableFuture<Void> future;

    @Test
    void shouldDoNothing() {
        HandlerAdapter adapter = new HandlerAdapter() {
            @Override
            public void handlerAdded(HandlerContext ctx) {
                super.handlerAdded(ctx);
            }

            @Override
            public void handlerRemoved(HandlerContext ctx) {
                super.handlerRemoved(ctx);
            }
        };

        adapter.handlerAdded(ctx);
        adapter.handlerRemoved(ctx);

        verifyNoInteractions(ctx);
    }

    @Nested
    class Outbound {
        @Test
        void shouldPassthroughsOnWrite() {
            HandlerAdapter duplexHandler = new HandlerAdapter();

            CompressedPublicKey recipient = mock(CompressedPublicKey.class);
            Object msg = mock(Object.class);

            duplexHandler.write(ctx, recipient, msg, future);

            verify(ctx).write(eq(recipient), eq(msg), eq(future));
        }
    }

    @Nested
    class Inbound {
        @Test
        void shouldPassthroughsOnRead() {
            HandlerAdapter duplexHandler = new HandlerAdapter();

            CompressedPublicKey sender = mock(CompressedPublicKey.class);
            Object msg = mock(Object.class);

            duplexHandler.read(ctx, sender, msg, future);

            verify(ctx).fireRead(eq(sender), eq(msg), eq(future));
        }

        @Test
        void shouldPassthroughsOnEventTriggered() {
            HandlerAdapter duplexHandler = new HandlerAdapter();

            Event event = mock(Event.class);

            duplexHandler.eventTriggered(ctx, event, future);

            verify(ctx).fireEventTriggered(eq(event), eq(future));
        }

        @Test
        void shouldPassthroughsOnExceptionCaught() {
            HandlerAdapter duplexHandler = new HandlerAdapter();

            Exception exception = mock(Exception.class);

            duplexHandler.exceptionCaught(ctx, exception);

            verify(ctx).fireExceptionCaught(eq(exception));
        }

        @Test
        void shouldPassthroughsOnEventTriggeredWithMultipleHandler() {
            EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, validator, IntStream.rangeClosed(1, 10).mapToObj(i -> new HandlerAdapter()).toArray(HandlerAdapter[]::new));
            TestObserver<Event> events = pipeline.inboundEvents().test();

            Event event = mock(Event.class);
            pipeline.processInbound(event);

            events.awaitCount(1);
            events.assertValue(event);
        }

        @Test
        void shouldPassthroughsOnReadWithMultipleHandler() {
            EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, validator, IntStream.rangeClosed(1, 10).mapToObj(i -> new HandlerAdapter()).toArray(HandlerAdapter[]::new));
            TestObserver<Pair<CompressedPublicKey, Object>> events = pipeline.inboundMessages().test();

            CompressedPublicKey sender = mock(CompressedPublicKey.class);
            ApplicationMessage msg = mock(ApplicationMessage.class);
            when(msg.getSender()).thenReturn(sender);
            when(msg.getPayload()).thenReturn(new byte[]{});

            pipeline.processInbound(msg);

            events.awaitCount(1);
            events.assertValue(Pair.of(sender, ObjectHolder.of(null, new byte[]{})));
        }
    }
}