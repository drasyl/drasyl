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

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.event.Event;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.util.DrasylConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeadContextTest {
    @Mock
    private DrasylConsumer<ApplicationMessage> outboundConsumer;
    @Mock
    private HandlerContext ctx;
    @Mock
    private DrasylConfig config;

    @Test
    void shouldReturnSelfAsHandler() {
        HeadContext headContext = new HeadContext(outboundConsumer, config);

        assertEquals(headContext, headContext.handler());
    }

    @Test
    void shouldDoNothingOnHandlerAdded() throws Exception {
        HeadContext headContext = new HeadContext(outboundConsumer, config);

        headContext.handlerAdded(ctx);

        verifyNoInteractions(ctx);
    }

    @Test
    void shouldDoNothingOnHandlerRemoved() throws Exception {
        HeadContext headContext = new HeadContext(outboundConsumer, config);

        headContext.handlerRemoved(ctx);

        verifyNoInteractions(ctx);
    }

    @Test
    void shouldPassthroughsOnRead() {
        HeadContext headContext = new HeadContext(outboundConsumer, config);
        ApplicationMessage msg = mock(ApplicationMessage.class);

        headContext.read(ctx, msg);

        verify(ctx).fireRead(eq(msg));
    }

    @Test
    void shouldPassthroughsOnEvent() {
        HeadContext headContext = new HeadContext(outboundConsumer, config);
        Event event = mock(Event.class);

        headContext.eventTriggered(ctx, event);

        verify(ctx).fireEventTriggered(eq(event));
    }

    @Test
    void shouldPassthroughsOnException() throws Exception {
        HeadContext headContext = new HeadContext(outboundConsumer, config);
        Exception exception = mock(Exception.class);

        headContext.exceptionCaught(ctx, exception);

        verify(ctx).fireExceptionCaught(eq(exception));
    }

    @Test
    void shouldWriteToConsumer() throws Exception {
        HeadContext headContext = new HeadContext(outboundConsumer, config);
        ApplicationMessage msg = mock(ApplicationMessage.class);
        CompletableFuture<Void> future = mock(CompletableFuture.class);

        when(future.isDone()).thenReturn(false);

        headContext.write(ctx, msg, future);

        verify(outboundConsumer).accept(eq(msg));
        verify(future).complete(null);
    }

    @Test
    void shouldNotWriteToConsumerWhenFutureIsDone() throws Exception {
        HeadContext headContext = new HeadContext(outboundConsumer, config);
        ApplicationMessage msg = mock(ApplicationMessage.class);
        CompletableFuture<Void> future = mock(CompletableFuture.class);

        when(future.isDone()).thenReturn(true);

        headContext.write(ctx, msg, future);

        verifyNoInteractions(outboundConsumer);
        verify(future, never()).complete(null);
    }

    @Test
    void shouldCompleteFutureExceptionallyIfExceptionOccursOnWrite() throws Exception {
        HeadContext headContext = new HeadContext(outboundConsumer, config);
        ApplicationMessage msg = mock(ApplicationMessage.class);
        CompletableFuture<Void> future = mock(CompletableFuture.class);

        doThrow(DrasylException.class).when(outboundConsumer).accept(any());
        when(future.isDone()).thenReturn(false);

        headContext.write(ctx, msg, future);

        verify(outboundConsumer).accept(eq(msg));
        verify(future, never()).complete(null);
        verify(future).completeExceptionally(isA(Exception.class));
    }
}