/*
 * Copyright (c) 2020-2021.
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
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractHandlerContextTest {
    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private AbstractHandlerContext prev;
    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private AbstractHandlerContext next;
    @Mock
    private Handler handler;
    @Mock
    private DrasylConfig config;
    @Mock
    private Pipeline pipeline;
    @Mock
    private DrasylScheduler dependentScheduler;
    @Mock
    private DrasylScheduler independentScheduler;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private Serialization inboundSerialization;
    @Mock
    private Serialization outboundSerialization;
    @Mock
    private CompletableFuture<Void> future;
    private String name;

    @BeforeEach
    void setUp() {
        name = "testCtx";
    }

    @Test
    void shouldSetCorrectPrevHandlerContext() {
        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        final AbstractHandlerContext newPrev = mock(AbstractHandlerContext.class);

        ctx.setPrevHandlerContext(newPrev);

        assertEquals(newPrev, ctx.getPrev());
        assertNotEquals(prev, ctx.getPrev());
    }

    @Test
    void shouldSetCorrectNextHandlerContext() {
        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        final AbstractHandlerContext newNext = mock(AbstractHandlerContext.class);

        ctx.setNextHandlerContext(newNext);

        assertEquals(newNext, ctx.getNext());
        assertNotEquals(next, ctx.getNext());
    }

    @Test
    void shouldSetCorrectName() {
        final AbstractHandlerContext ctx = new AbstractHandlerContext(name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        assertEquals(name, ctx.name());
    }

    @Test
    void shouldSetCorrectConfig() {
        final AbstractHandlerContext ctx = new AbstractHandlerContext(name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        assertEquals(config, ctx.config());
    }

    @Test
    void shouldSetPipeline() {
        final AbstractHandlerContext ctx = new AbstractHandlerContext(name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        assertEquals(pipeline, ctx.pipeline());
    }

    @Test
    void shouldSetDependentScheduler() {
        final AbstractHandlerContext ctx = new AbstractHandlerContext(name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        assertEquals(dependentScheduler, ctx.dependentScheduler());
    }

    @Test
    void shouldSetIndependentScheduler() {
        final AbstractHandlerContext ctx = new AbstractHandlerContext(name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        assertEquals(independentScheduler, ctx.independentScheduler());
    }

    @Test
    void shouldSetIdentity() {
        final AbstractHandlerContext ctx = new AbstractHandlerContext(name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        assertEquals(identity, ctx.identity());
    }

    @Test
    void shouldSetInboundValidator() {
        final AbstractHandlerContext ctx = new AbstractHandlerContext(name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        assertEquals(inboundSerialization, ctx.inboundSerialization());
    }

    @Test
    void shouldSetOutboundValidator() {
        final AbstractHandlerContext ctx = new AbstractHandlerContext(name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        assertEquals(outboundSerialization, ctx.outboundSerialization());
    }

    @Test
    void shouldInvokeExceptionCaught() {
        final Handler newHandler = mock(Handler.class);
        when(next.handler()).thenReturn(newHandler);
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        final Exception exception = mock(Exception.class);

        ctx.fireExceptionCaught(exception);

        verify(next, times(3)).handler();
        verify(newHandler).exceptionCaught(next, exception);
    }

    @Test
    void shouldFindCorrectNextHandler() {
        final Handler handler = mock(Handler.class);
        when(next.handler()).thenReturn(handler);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        final AbstractHandlerContext actual = ctx.findNextInbound(HandlerMask.READ_MASK);

        assertEquals(next, actual);
    }

    @Test
    void shouldInvokeRead() {
        final Handler newHandler = mock(Handler.class);
        when(next.handler()).thenReturn(newHandler);
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        final CompressedPublicKey sender = mock(CompressedPublicKey.class);
        final Object msg = mock(Object.class);

        ctx.fireRead(sender, msg, future);

        verify(next, times(3)).handler();
        verify(newHandler).read(next, sender, msg, future);
    }

    @Test
    void shouldRethrowIfExceptionOccursDuringInvokeRead() {
        final Handler newHandler = mock(Handler.class);
        when(next.handler()).thenReturn(newHandler);
        doThrow(RuntimeException.class).when(newHandler).read(any(), any(), any(), any());
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);
        when(next.dependentScheduler()).thenReturn(dependentScheduler);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        final CompressedPublicKey sender = mock(CompressedPublicKey.class);
        final Object msg = mock(Object.class);

        ctx.fireRead(sender, msg, future);

        verify(next, times(3)).handler();
        verify(newHandler).read(next, sender, msg, future);
        verify(next).fireExceptionCaught(isA(RuntimeException.class));
    }

    @Test
    void shouldFireEventTriggered() {
        final Handler newHandler = mock(Handler.class);
        when(next.handler()).thenReturn(newHandler);
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        final Event event = mock(Event.class);

        ctx.fireEventTriggered(event, future);

        verify(next, times(3)).handler();
        verify(newHandler).eventTriggered(next, event, future);
    }

    @Test
    void shouldRethrowIfExceptionOccursDuringFireEventTriggered() {
        final Handler newHandler = mock(Handler.class);
        when(next.handler()).thenReturn(newHandler);
        doThrow(RuntimeException.class).when(newHandler).eventTriggered(any(), any(), any());
        when(next.dependentScheduler()).thenReturn(dependentScheduler);
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        final Event event = mock(Event.class);

        ctx.fireEventTriggered(event, future);

        verify(next, times(3)).handler();
        verify(newHandler).eventTriggered(next, event, future);
        verify(next).fireExceptionCaught(isA(RuntimeException.class));
    }

    @Test
    void shouldWrite() {
        final Handler newHandler = mock(Handler.class);
        when(prev.handler()).thenReturn(newHandler);
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        final CompressedPublicKey recipient = mock(CompressedPublicKey.class);
        final Object msg = mock(Object.class);

        ctx.write(recipient, msg, future);

        verify(prev, times(3)).handler();
        verify(newHandler).write(prev, recipient, msg, future);
    }

    @Test
    void shouldRethrowIfExceptionOccursDuringWrite() {
        final Handler newHandler = mock(Handler.class);
        when(prev.handler()).thenReturn(newHandler);
        doThrow(RuntimeException.class).when(newHandler).write(any(), any(), any(), any());
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);
        when(prev.dependentScheduler()).thenReturn(dependentScheduler);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        final CompressedPublicKey recipient = mock(CompressedPublicKey.class);
        final Object msg = mock(Object.class);

        ctx.write(recipient, msg, future);

        verify(prev, times(3)).handler();
        verify(newHandler).write(prev, recipient, msg, future);
        verify(prev).fireExceptionCaught(isA(RuntimeException.class));
    }

    @Test
    void shouldThrowExceptionOnPipelineException() {
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);
        final AbstractHandlerContext context = new AbstractHandlerContext("test", config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return null;
            }
        };

        final PipelineException exception = mock(PipelineException.class);

        assertThrows(PipelineException.class, () -> context.fireExceptionCaught(exception));
    }

    @Test
    void shouldThrowExceptionOnPipelineExceptionOnNextHandler() {
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);
        final AbstractHandlerContext context = new AbstractHandlerContext("test", config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return null;
            }
        };

        final Handler newHandler = mock(Handler.class);
        final AbstractHandlerContext context1 = mock(AbstractHandlerContext.class, Answers.CALLS_REAL_METHODS);
        context.setNextHandlerContext(context1);
        when(context1.handler()).thenReturn(newHandler);
        doThrow(PipelineException.class).when(newHandler).exceptionCaught(any(), any());

        final Exception exception = mock(Exception.class);

        assertThrows(PipelineException.class, () -> context.fireExceptionCaught(exception));
    }

    @Test
    void shouldNotThrowExceptionOnNextHandler() {
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);
        final AbstractHandlerContext context = new AbstractHandlerContext("test", config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return null;
            }
        };

        final Handler newHandler = mock(Handler.class);
        final AbstractHandlerContext context1 = mock(AbstractHandlerContext.class, Answers.CALLS_REAL_METHODS);
        context.setNextHandlerContext(context1);
        when(context1.handler()).thenReturn(newHandler);
        doThrow(IllegalArgumentException.class).when(newHandler).exceptionCaught(any(), any());

        final Exception exception = mock(Exception.class);

        assertDoesNotThrow(() -> context.fireExceptionCaught(exception));
    }

    @Test
    void shouldSkipNullHandlerOnInbound() {
        final AbstractHandlerContext context = mock(AbstractHandlerContext.class, Answers.CALLS_REAL_METHODS);
        when(next.handler()).thenReturn(null);
        when(next.getNext()).thenReturn(context);
        when(context.handler()).thenReturn(mock(Handler.class));

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        final AbstractHandlerContext actual = ctx.findNextInbound(HandlerMask.READ_MASK);

        assertEquals(context, actual);
    }

    @Test
    void shouldSkipNullHandlerOnOutbound() {
        final AbstractHandlerContext context = mock(AbstractHandlerContext.class, Answers.CALLS_REAL_METHODS);
        when(prev.handler()).thenReturn(null);
        when(prev.getPrev()).thenReturn(context);
        when(context.handler()).thenReturn(mock(Handler.class));

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        final AbstractHandlerContext actual = ctx.findPrevOutbound(HandlerMask.WRITE_MASK);

        assertEquals(context, actual);
    }

    @Nested
    class Skippable {
        @ParameterizedTest
        @ValueSource(ints = {
                HandlerMask.EVENT_TRIGGERED_MASK,
                HandlerMask.EXCEPTION_CAUGHT_MASK,
                HandlerMask.READ_MASK,
                HandlerMask.WRITE_MASK,
                HandlerMask.ALL
        })
        void shouldSkipSkippableHandlerOnInbound(final int mask) {
            final AbstractHandlerContext context = mock(AbstractHandlerContext.class);
            when(next.handler()).thenReturn(mock(Handler.class));
            when(next.getMask()).thenReturn(HandlerMask.ALL & ~mask);
            when(next.getNext()).thenReturn(context);
            when(context.handler()).thenReturn(mock(Handler.class));
            when(context.getMask()).thenReturn(mask);

            final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
                @Override
                public Handler handler() {
                    return handler;
                }
            };

            final AbstractHandlerContext actual = ctx.findNextInbound(mask);

            assertEquals(context, actual);
        }

        @ParameterizedTest
        @ValueSource(ints = {
                HandlerMask.EVENT_TRIGGERED_MASK,
                HandlerMask.EXCEPTION_CAUGHT_MASK,
                HandlerMask.READ_MASK,
                HandlerMask.WRITE_MASK,
                HandlerMask.ALL
        })
        void shouldSkipSkippableHandlerOnOutbound(final int mask) {
            final AbstractHandlerContext context = mock(AbstractHandlerContext.class);
            when(prev.handler()).thenReturn(mock(Handler.class));
            when(prev.getMask()).thenReturn(HandlerMask.ALL & ~mask);
            when(prev.getPrev()).thenReturn(context);
            when(context.handler()).thenReturn(mock(Handler.class));
            when(context.getMask()).thenReturn(mask);

            final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
                @Override
                public Handler handler() {
                    return handler;
                }
            };

            final AbstractHandlerContext actual = ctx.findPrevOutbound(mask);

            assertEquals(context, actual);
        }
    }
}
