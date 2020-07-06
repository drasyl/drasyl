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
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TailContextTest {
    @Mock
    private Consumer<Event> eventConsumer;
    @Mock
    private HandlerContext ctx;
    @Mock
    private DrasylConfig config;

    @Test
    void shouldReturnSelfAsHandler() {
        TailContext headContext = new TailContext(eventConsumer, config);

        assertEquals(headContext, headContext.handler());
    }

    @Test
    void shouldDoNothingOnHandlerAdded() throws Exception {
        TailContext headContext = new TailContext(eventConsumer, config);

        headContext.handlerAdded(ctx);

        verifyNoInteractions(ctx);
    }

    @Test
    void shouldDoNothingOnHandlerRemoved() throws Exception {
        TailContext headContext = new TailContext(eventConsumer, config);

        headContext.handlerRemoved(ctx);

        verifyNoInteractions(ctx);
    }

    @Test
    void shouldPassthroughsOnWrite() {
        TailContext headContext = new TailContext(eventConsumer, config);
        ApplicationMessage msg = mock(ApplicationMessage.class);
        CompletableFuture future = mock(CompletableFuture.class);

        headContext.write(ctx, msg, future);

        verify(ctx).write(eq(msg), eq(future));
    }

    @Test
    void shouldThrowException() {
        TailContext headContext = new TailContext(eventConsumer, config);
        Exception exception = mock(Exception.class);

        assertThrows(Exception.class, () -> headContext.exceptionCaught(ctx, exception));
        verifyNoInteractions(ctx);
    }

    @Test
    void shouldPassEventToConsumer() {
        TailContext headContext = new TailContext(eventConsumer, config);
        Event event = mock(Event.class);

        headContext.eventTriggered(ctx, event);

        verify(eventConsumer).accept(eq(event));
        verifyNoInteractions(ctx);
    }

    @Test
    void shouldPassMessageToApplication() {
        TailContext headContext = new TailContext(eventConsumer, config);
        ApplicationMessage msg = mock(ApplicationMessage.class);

        when(msg.getSender()).thenReturn(mock(CompressedPublicKey.class));
        when(msg.getPayload()).thenReturn(new byte[]{});

        headContext.read(ctx, msg);

        verify(eventConsumer).accept(eq(new MessageEvent(Pair.of(msg.getSender(), msg.getPayload()))));
        verifyNoInteractions(ctx);
    }
}