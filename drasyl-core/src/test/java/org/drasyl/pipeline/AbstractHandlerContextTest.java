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
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractHandlerContextTest {
    @Mock
    private AbstractHandlerContext prev;
    @Mock
    private AbstractHandlerContext next;
    @Mock
    private Handler handler;
    @Mock
    private DrasylConfig config;
    private String name;

    @BeforeEach
    void setUp() {
        name = "testCtx";
    }

    @Test
    void shouldSetCorrectPrevHandlerContext() {
        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        AbstractHandlerContext newPrev = mock(AbstractHandlerContext.class);

        ctx.setPrevHandlerContext(newPrev);

        assertEquals(newPrev, ctx.getPrev());
        assertNotEquals(prev, ctx.getPrev());
    }

    @Test
    void shouldSetCorrectNextHandlerContext() {
        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        AbstractHandlerContext newNext = mock(AbstractHandlerContext.class);

        ctx.setNextHandlerContext(newNext);

        assertEquals(newNext, ctx.getNext());
        assertNotEquals(next, ctx.getNext());
    }

    @Test
    void shouldSetCorrectName() {
        AbstractHandlerContext ctx = new AbstractHandlerContext(name, config) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        assertEquals(name, ctx.name());
    }

    @Test
    void shouldInvokeExceptionCaught() throws Throwable {
        InboundHandler inboundHandler = mock(InboundHandler.class);
        when(next.handler()).thenReturn(inboundHandler);

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        Exception exception = mock(Exception.class);

        ctx.fireExceptionCaught(exception);

        verify(next, times(2)).handler();
        verify(inboundHandler).exceptionCaught(eq(next), eq(exception));
    }

    @Test
    void shouldFindCorrectNextInbound() {
        AbstractHandlerContext nextCtx = mock(AbstractHandlerContext.class);
        InboundHandler inboundHandler = mock(InboundHandler.class);
        OutboundHandler outboundHandler = mock(OutboundHandler.class);
        when(next.handler()).thenReturn(outboundHandler);
        when(next.getNext()).thenReturn(nextCtx);
        when(nextCtx.handler()).thenReturn(inboundHandler);

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        AbstractHandlerContext actual = ctx.findNextInbound();

        assertEquals(nextCtx, actual);
        assertNotEquals(next, actual);
    }

    @Test
    void shouldFindCorrectNextOutbound() {
        AbstractHandlerContext prevCtx = mock(AbstractHandlerContext.class);
        InboundHandler inboundHandler = mock(InboundHandler.class);
        OutboundHandler outboundHandler = mock(OutboundHandler.class);
        when(prev.handler()).thenReturn(inboundHandler);
        when(prev.getPrev()).thenReturn(prevCtx);
        when(prevCtx.handler()).thenReturn(outboundHandler);

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        AbstractHandlerContext actual = ctx.findPrevOutbound();

        assertEquals(prevCtx, actual);
        assertNotEquals(prev, actual);
    }

    @Test
    void shouldInvokeRead() {
        InboundHandler inboundHandler = mock(InboundHandler.class);
        when(next.handler()).thenReturn(inboundHandler);

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        ApplicationMessage msg = mock(ApplicationMessage.class);

        ctx.fireRead(msg);

        verify(next, times(2)).handler();
        verify(inboundHandler).read(eq(next), eq(msg));
    }

    @Test
    void shouldRethrowIfExceptionOccursDuringInvokeRead() {
        InboundHandler inboundHandler = mock(InboundHandler.class);
        when(next.handler()).thenReturn(inboundHandler);
        doThrow(RuntimeException.class).when(inboundHandler).read(any(), any());

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        ApplicationMessage msg = mock(ApplicationMessage.class);

        ctx.fireRead(msg);

        verify(next, times(2)).handler();
        verify(inboundHandler).read(eq(next), eq(msg));
        verify(next).fireExceptionCaught(isA(RuntimeException.class));
    }

    @Test
    void shouldFireEventTriggered() {
        InboundHandler inboundHandler = mock(InboundHandler.class);
        when(next.handler()).thenReturn(inboundHandler);

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        Event event = mock(Event.class);

        ctx.fireEventTriggered(event);

        verify(next, times(2)).handler();
        verify(inboundHandler).eventTriggered(eq(next), eq(event));
    }

    @Test
    void shouldRethrowIfExceptionOccursDuringFireEventTriggered() {
        InboundHandler inboundHandler = mock(InboundHandler.class);
        when(next.handler()).thenReturn(inboundHandler);
        doThrow(RuntimeException.class).when(inboundHandler).eventTriggered(any(), any());

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        Event event = mock(Event.class);

        ctx.fireEventTriggered(event);

        verify(next, times(2)).handler();
        verify(inboundHandler).eventTriggered(eq(next), eq(event));
        verify(next).fireExceptionCaught(isA(RuntimeException.class));
    }

    @Test
    void shouldWrite() {
        OutboundHandler outboundHandler = mock(OutboundHandler.class);
        when(prev.handler()).thenReturn(outboundHandler);

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        ApplicationMessage msg = mock(ApplicationMessage.class);

        ctx.write(msg);

        verify(prev, times(2)).handler();
        verify(outboundHandler).write(eq(prev), eq(msg), isA(CompletableFuture.class));
    }

    @Test
    void shouldRethrowIfExceptionOccursDuringWrite() {
        OutboundHandler outboundHandler = mock(OutboundHandler.class);
        when(prev.handler()).thenReturn(outboundHandler);
        doThrow(RuntimeException.class).when(outboundHandler).write(any(), any(), any());

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        ApplicationMessage msg = mock(ApplicationMessage.class);

        ctx.write(msg);

        verify(prev, times(2)).handler();
        verify(outboundHandler).write(eq(prev), eq(msg), isA(CompletableFuture.class));
        verify(prev).fireExceptionCaught(isA(RuntimeException.class));
    }
}