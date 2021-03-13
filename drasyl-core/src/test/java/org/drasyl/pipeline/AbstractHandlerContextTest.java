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
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractHandlerContextTest {
    @Mock(answer = CALLS_REAL_METHODS)
    private AbstractHandlerContext prev;
    @Mock(answer = CALLS_REAL_METHODS)
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
    void shouldSetCorrectPrevHandlerContext(@Mock final AbstractHandlerContext newPrev) {
        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        ctx.setPrevHandlerContext(newPrev);

        assertEquals(newPrev, ctx.getPrev());
        assertNotEquals(prev, ctx.getPrev());
    }

    @Test
    void shouldSetCorrectNextHandlerContext(@Mock final AbstractHandlerContext newNext) {
        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

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
    void shouldInvokeExceptionCaught(@Mock final Handler newHandler,
                                     @Mock final Exception exception) {
        when(next.handler()).thenReturn(newHandler);
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        ctx.passException(exception);

        verify(next, times(3)).handler();
        verify(newHandler).onException(next, exception);
    }

    @Test
    void shouldFindCorrectNextHandler(@Mock final Handler handler) {
        when(next.handler()).thenReturn(handler);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        final AbstractHandlerContext actual = ctx.findNextInbound(HandlerMask.ON_INBOUND_MASK);

        assertEquals(next, actual);
    }

    @Test
    void shouldInvokeRead(@Mock final Handler newHandler,
                          @Mock final CompressedPublicKey sender,
                          @Mock final Object msg) {
        when(next.handler()).thenReturn(newHandler);
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        ctx.passInbound(sender, msg, future);

        verify(next, times(3)).handler();
        verify(newHandler).onInbound(next, sender, msg, future);
    }

    @Test
    void shouldRethrowIfExceptionOccursDuringInvokeRead(@Mock final Handler newHandler,
                                                        @Mock final CompressedPublicKey sender,
                                                        @Mock final Object msg) {
        when(next.handler()).thenReturn(newHandler);
        doThrow(RuntimeException.class).when(newHandler).onInbound(any(), any(), any(), any());
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);
        when(next.dependentScheduler()).thenReturn(dependentScheduler);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        ctx.passInbound(sender, msg, future);

        verify(next, times(3)).handler();
        verify(newHandler).onInbound(next, sender, msg, future);
        verify(next).passException(isA(RuntimeException.class));
    }

    @Test
    void shouldFireEventTriggered(@Mock final Handler newHandler, @Mock final Event event) {
        when(next.handler()).thenReturn(newHandler);
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        ctx.passEvent(event, future);

        verify(next, times(3)).handler();
        verify(newHandler).onEvent(next, event, future);
    }

    @Test
    void shouldRethrowIfExceptionOccursDuringFireEventTriggered(@Mock final Handler newHandler,
                                                                @Mock final Event event) {
        when(next.handler()).thenReturn(newHandler);
        doThrow(RuntimeException.class).when(newHandler).onEvent(any(), any(), any());
        when(next.dependentScheduler()).thenReturn(dependentScheduler);
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        ctx.passEvent(event, future);

        verify(next, times(3)).handler();
        verify(newHandler).onEvent(next, event, future);
        verify(next).passException(isA(RuntimeException.class));
    }

    @Test
    void shouldWrite(@Mock final Handler newHandler,
                     @Mock final CompressedPublicKey recipient,
                     @Mock final Object msg) {
        when(prev.handler()).thenReturn(newHandler);
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        ctx.passOutbound(recipient, msg, future);

        verify(prev, times(3)).handler();
        verify(newHandler).onOutbound(prev, recipient, msg, future);
    }

    @Test
    void shouldRethrowIfExceptionOccursDuringWrite(@Mock final Handler newHandler,
                                                   @Mock final CompressedPublicKey recipient,
                                                   @Mock final Object msg) {
        when(prev.handler()).thenReturn(newHandler);
        doThrow(RuntimeException.class).when(newHandler).onOutbound(any(), any(), any(), any());
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);
        when(prev.dependentScheduler()).thenReturn(dependentScheduler);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return handler;
            }
        };

        ctx.passOutbound(recipient, msg, future);

        verify(prev, times(3)).handler();
        verify(newHandler).onOutbound(prev, recipient, msg, future);
        verify(prev).passException(isA(RuntimeException.class));
    }

    @Test
    void shouldNotThrowExceptionOnExceptionOnNextHandler(@Mock final Handler newHandler,
                                                         @Mock final Exception exception,
                                                         @Mock(answer = CALLS_REAL_METHODS) final AbstractHandlerContext context1) {
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);
        final AbstractHandlerContext context = new AbstractHandlerContext("test", config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return null;
            }
        };

        context.setNextHandlerContext(context1);
        when(context1.handler()).thenReturn(newHandler);
        doThrow(RuntimeException.class).when(newHandler).onException(any(), any());

        assertDoesNotThrow(() -> context.passException(exception));
    }

    @Test
    void shouldNotThrowExceptionOnNextHandler(@Mock final Handler newHandler,
                                              @Mock final Exception exception,
                                              @Mock(answer = CALLS_REAL_METHODS) final AbstractHandlerContext context1) {
        when(dependentScheduler.isCalledFromThisScheduler()).thenReturn(true);
        final AbstractHandlerContext context = new AbstractHandlerContext("test", config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return null;
            }
        };

        context.setNextHandlerContext(context1);
        when(context1.handler()).thenReturn(newHandler);
        doThrow(IllegalArgumentException.class).when(newHandler).onException(any(), any());

        assertDoesNotThrow(() -> context.passException(exception));
    }

    @Test
    void shouldSkipNullHandlerOnInbound(@Mock final Handler handler,
                                        @Mock(answer = CALLS_REAL_METHODS) final AbstractHandlerContext context) {
        when(next.handler()).thenReturn(null);
        when(next.getNext()).thenReturn(context);
        when(context.handler()).thenReturn(handler);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return AbstractHandlerContextTest.this.handler;
            }
        };

        final AbstractHandlerContext actual = ctx.findNextInbound(HandlerMask.ON_INBOUND_MASK);

        assertEquals(context, actual);
    }

    @Test
    void shouldSkipNullHandlerOnOutbound(@Mock final Handler handler,
                                         @Mock(answer = CALLS_REAL_METHODS) final AbstractHandlerContext context) {
        when(prev.handler()).thenReturn(null);
        when(prev.getPrev()).thenReturn(context);
        when(context.handler()).thenReturn(handler);

        final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public Handler handler() {
                return AbstractHandlerContextTest.this.handler;
            }
        };

        final AbstractHandlerContext actual = ctx.findPrevOutbound(HandlerMask.ON_OUTBOUND_MASK);

        assertEquals(context, actual);
    }

    @Nested
    class Skippable {
        @ParameterizedTest
        @ValueSource(ints = {
                HandlerMask.ON_EVENT_MASK,
                HandlerMask.ON_EXCEPTION_MASK,
                HandlerMask.ON_INBOUND_MASK,
                HandlerMask.ON_OUTBOUND_MASK,
                HandlerMask.ALL
        })
        void shouldSkipSkippableHandlerOnInbound(final int mask,
                                                 @Mock final AbstractHandlerContext context,
                                                 @Mock final Handler handler,
                                                 @Mock final Handler handler1) {
            when(next.handler()).thenReturn(handler);
            when(next.getMask()).thenReturn(HandlerMask.ALL & ~mask);
            when(next.getNext()).thenReturn(context);
            when(context.handler()).thenReturn(handler1);
            when(context.getMask()).thenReturn(mask);

            final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
                @Override
                public Handler handler() {
                    return AbstractHandlerContextTest.this.handler;
                }
            };

            final AbstractHandlerContext actual = ctx.findNextInbound(mask);

            assertEquals(context, actual);
        }

        @ParameterizedTest
        @ValueSource(ints = {
                HandlerMask.ON_EVENT_MASK,
                HandlerMask.ON_EXCEPTION_MASK,
                HandlerMask.ON_INBOUND_MASK,
                HandlerMask.ON_OUTBOUND_MASK,
                HandlerMask.ALL
        })
        void shouldSkipSkippableHandlerOnOutbound(final int mask,
                                                  @Mock final AbstractHandlerContext context,
                                                  @Mock final Handler handler,
                                                  @Mock final Handler handler1) {
            when(prev.handler()).thenReturn(handler);
            when(prev.getMask()).thenReturn(HandlerMask.ALL & ~mask);
            when(prev.getPrev()).thenReturn(context);
            when(context.handler()).thenReturn(handler1);
            when(context.getMask()).thenReturn(mask);

            final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
                @Override
                public Handler handler() {
                    return AbstractHandlerContextTest.this.handler;
                }
            };

            final AbstractHandlerContext actual = ctx.findPrevOutbound(mask);

            assertEquals(context, actual);
        }
    }

    @Nested
    class DoneFutures {
        @Test
        void shouldNotCallAnyHandlerOnEventIfFutureIsAlreadyDone(@Mock final Handler newHandler,
                                                                 @Mock final Event event) {
            when(future.isDone()).thenReturn(true);

            final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
                @Override
                public Handler handler() {
                    return handler;
                }
            };

            ctx.passEvent(event, future);

            verify(newHandler, never()).onEvent(next, event, future);
        }

        @Test
        void shouldNotCallAnyHandlerOnReadIfFutureIsAlreadyDone(@Mock final Handler newHandler,
                                                                @Mock final Address address,
                                                                @Mock final Object msg) {
            when(future.isDone()).thenReturn(true);

            final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
                @Override
                public Handler handler() {
                    return handler;
                }
            };

            ctx.passInbound(address, msg, future);

            verify(newHandler, never()).onInbound(next, address, msg, future);
        }

        @Test
        void shouldNotCallAnyHandlerOnWriteIfFutureIsAlreadyDone(@Mock final Handler newHandler,
                                                                 @Mock final Address address,
                                                                 @Mock final Object msg) {
            when(future.isDone()).thenReturn(true);

            final AbstractHandlerContext ctx = new AbstractHandlerContext(prev, next, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
                @Override
                public Handler handler() {
                    return handler;
                }
            };

            ctx.passOutbound(address, msg, future);

            verify(newHandler, never()).onOutbound(next, address, msg, future);
        }
    }
}
