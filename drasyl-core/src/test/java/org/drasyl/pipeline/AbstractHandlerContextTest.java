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

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.pipeline.codec.TypeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @Mock
    private Pipeline pipeline;
    @Mock
    private Scheduler scheduler;
    @Mock
    private Identity identity;
    @Mock
    private TypeValidator validator;
    @Mock
    private CompletableFuture<Void> future;
    private String name;

    @BeforeEach
    void setUp() {
        name = "testCtx";
    }

    @Test
    void shouldSetCorrectPrevHandlerContext() {
        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, scheduler, identity, validator) {
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
        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, scheduler, identity, validator) {
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
        AbstractHandlerContext ctx = new AbstractHandlerContext(name, config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        assertEquals(name, ctx.name());
    }

    @Test
    void shouldSetCorrectConfig() {
        AbstractHandlerContext ctx = new AbstractHandlerContext(name, config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        assertEquals(config, ctx.config());
    }

    @Test
    void shouldSetPipeline() {
        AbstractHandlerContext ctx = new AbstractHandlerContext(name, config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        assertEquals(pipeline, ctx.pipeline());
    }

    @Test
    void shouldSetScheduler() {
        AbstractHandlerContext ctx = new AbstractHandlerContext(name, config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        assertEquals(scheduler, ctx.scheduler());
    }

    @Test
    void shouldSetIdentity() {
        AbstractHandlerContext ctx = new AbstractHandlerContext(name, config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        assertEquals(identity, ctx.identity());
    }

    @Test
    void shouldSetValidator() {
        AbstractHandlerContext ctx = new AbstractHandlerContext(name, config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        assertEquals(validator, ctx.validator());
    }

    @Test
    void shouldInvokeExceptionCaught() {
        Handler newHandler = mock(Handler.class);
        when(next.handler()).thenReturn(newHandler);

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        Exception exception = mock(Exception.class);

        ctx.fireExceptionCaught(exception);

        verify(next, times(2)).handler();
        verify(newHandler).exceptionCaught(eq(next), eq(exception));
    }

    @Test
    void shouldFindCorrectNextHandler() {
        Handler handler = mock(Handler.class);
        when(next.handler()).thenReturn(handler);

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        AbstractHandlerContext actual = ctx.findNextInbound();

        assertEquals(next, actual);
    }

    @Test
    void shouldInvokeRead() {
        Handler newHandler = mock(Handler.class);
        when(next.handler()).thenReturn(newHandler);

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        CompressedPublicKey sender = mock(CompressedPublicKey.class);
        ApplicationMessage msg = mock(ApplicationMessage.class);

        ctx.fireRead(sender, msg, future);

        verify(next, times(2)).handler();
        verify(newHandler).read(eq(next), eq(sender), eq(msg), eq(future));
    }

    @Test
    void shouldRethrowIfExceptionOccursDuringInvokeRead() {
        Handler newHandler = mock(Handler.class);
        when(next.handler()).thenReturn(newHandler);
        doThrow(RuntimeException.class).when(newHandler).read(any(), any(), any(), any());

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        CompressedPublicKey sender = mock(CompressedPublicKey.class);
        ApplicationMessage msg = mock(ApplicationMessage.class);

        ctx.fireRead(sender, msg, future);

        verify(next, times(2)).handler();
        verify(newHandler).read(eq(next), eq(sender), eq(msg), eq(future));
        verify(next).fireExceptionCaught(isA(RuntimeException.class));
    }

    @Test
    void shouldFireEventTriggered() {
        Handler newHandler = mock(Handler.class);
        when(next.handler()).thenReturn(newHandler);

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        Event event = mock(Event.class);

        ctx.fireEventTriggered(event, future);

        verify(next, times(2)).handler();
        verify(newHandler).eventTriggered(eq(next), eq(event), eq(future));
    }

    @Test
    void shouldRethrowIfExceptionOccursDuringFireEventTriggered() {
        Handler newHandler = mock(Handler.class);
        when(next.handler()).thenReturn(newHandler);
        doThrow(RuntimeException.class).when(newHandler).eventTriggered(any(), any(), any());

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        Event event = mock(Event.class);

        ctx.fireEventTriggered(event, future);

        verify(next, times(2)).handler();
        verify(newHandler).eventTriggered(eq(next), eq(event), eq(future));
        verify(next).fireExceptionCaught(isA(RuntimeException.class));
    }

    @Test
    void shouldWrite() {
        Handler newHandler = mock(Handler.class);
        when(prev.handler()).thenReturn(newHandler);

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        CompressedPublicKey recipient = mock(CompressedPublicKey.class);
        ApplicationMessage msg = mock(ApplicationMessage.class);

        ctx.write(recipient, msg);

        verify(prev, times(2)).handler();
        verify(newHandler).write(eq(prev), eq(recipient), eq(msg), isA(CompletableFuture.class));
    }

    @Test
    void shouldRethrowIfExceptionOccursDuringWrite() {
        Handler newHandler = mock(Handler.class);
        when(prev.handler()).thenReturn(newHandler);
        doThrow(RuntimeException.class).when(newHandler).write(any(), any(), any(), any());

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        CompressedPublicKey recipient = mock(CompressedPublicKey.class);
        ApplicationMessage msg = mock(ApplicationMessage.class);

        ctx.write(recipient, msg);

        verify(prev, times(2)).handler();
        verify(newHandler).write(eq(prev), eq(recipient), eq(msg), isA(CompletableFuture.class));
        verify(prev).fireExceptionCaught(isA(RuntimeException.class));
    }

    @Test
    void shouldThrowExceptionOnPipelineException() {
        AbstractHandlerContext context = new AbstractHandlerContext("test", config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return null;
            }
        };

        PipelineException exception = mock(PipelineException.class);

        assertThrows(PipelineException.class, () -> context.fireExceptionCaught(exception));
    }

    @Test
    void shouldThrowExceptionOnPipelineExceptionOnNextHandler() {
        AbstractHandlerContext context = new AbstractHandlerContext("test", config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return null;
            }
        };

        Handler newHandler = mock(Handler.class);
        AbstractHandlerContext context1 = mock(AbstractHandlerContext.class);
        context.setNextHandlerContext(context1);
        when(context1.handler()).thenReturn(newHandler);
        doThrow(PipelineException.class).when(newHandler).exceptionCaught(any(), any());

        Exception exception = mock(Exception.class);

        assertThrows(PipelineException.class, () -> context.fireExceptionCaught(exception));
    }

    @Test
    void shouldNotThrowExceptionOnNextHandler() {
        AbstractHandlerContext context = new AbstractHandlerContext("test", config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return null;
            }
        };

        Handler newHandler = mock(Handler.class);
        AbstractHandlerContext context1 = mock(AbstractHandlerContext.class);
        context.setNextHandlerContext(context1);
        when(context1.handler()).thenReturn(newHandler);
        doThrow(IllegalArgumentException.class).when(newHandler).exceptionCaught(any(), any());

        Exception exception = mock(Exception.class);

        assertDoesNotThrow(() -> context.fireExceptionCaught(exception));
    }

    @Test
    void shouldSkipNullHandlerOnInbound() {
        AbstractHandlerContext context = mock(AbstractHandlerContext.class);
        when(next.handler()).thenReturn(null);
        when(next.getNext()).thenReturn(context);
        when(context.handler()).thenReturn(mock(Handler.class));

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        AbstractHandlerContext actual = ctx.findNextInbound();

        assertEquals(context, actual);
    }

    @Test
    void shouldSkipNullHandlerOnOutbound() {
        AbstractHandlerContext context = mock(AbstractHandlerContext.class);
        when(prev.handler()).thenReturn(null);
        when(prev.getPrev()).thenReturn(context);
        when(context.handler()).thenReturn(mock(Handler.class));

        AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, scheduler, identity, validator) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        AbstractHandlerContext actual = ctx.findPrevOutbound();

        assertEquals(context, actual);
    }
}