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
import org.drasyl.pipeline.codec.ObjectHolder;
import org.drasyl.pipeline.codec.TypeValidator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeadContextTest {
    @Mock
    private HandlerContext ctx;
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

    @Nested
    class InGeneral {
        @Test
        void shouldReturnSelfAsHandler() {
            HeadContext headContext = new HeadContext(config, pipeline, scheduler, identity, validator);

            assertEquals(headContext, headContext.handler());
        }

        @Test
        void shouldDoNothingOnHandlerAdded() {
            HeadContext headContext = new HeadContext(config, pipeline, scheduler, identity, validator);

            headContext.handlerAdded(ctx);

            verifyNoInteractions(ctx);
        }

        @Test
        void shouldDoNothingOnHandlerRemoved() {
            HeadContext headContext = new HeadContext(config, pipeline, scheduler, identity, validator);

            headContext.handlerRemoved(ctx);

            verifyNoInteractions(ctx);
        }
    }

    @Nested
    class OnWrite {
        @Test
        void shouldSkipOnFutureCompleted() {
            HeadContext headContext = new HeadContext(config, pipeline, scheduler, identity, validator);
            CompressedPublicKey recipient = mock(CompressedPublicKey.class);
            ObjectHolder msg = ObjectHolder.of(byte[].class, new byte[]{});

            when(future.isDone()).thenReturn(true);

            headContext.write(ctx, recipient, msg, future);

            verify(future, never()).completeExceptionally(any());
        }

        @Test
        void shouldWriteCompleteExceptionallyIfFutureIsNotCompleted() {
            HeadContext headContext = new HeadContext(config, pipeline, scheduler, identity, validator);
            CompressedPublicKey recipient = mock(CompressedPublicKey.class);
            ObjectHolder msg = ObjectHolder.of(byte[].class, new byte[]{});

            when(future.isDone()).thenReturn(false);

            headContext.write(ctx, recipient, msg, future);

            verify(future).completeExceptionally(isA(IllegalStateException.class));
        }

        @Test
        void shouldCompleteFutureAndNothingElseOnAutoSwallow() {
            HeadContext headContext = new HeadContext(config, pipeline, scheduler, identity, validator);
            CompressedPublicKey recipient = mock(CompressedPublicKey.class);
            AutoSwallow msg = new AutoSwallow() {};

            headContext.write(ctx, recipient, msg, future);

            verify(future, never()).completeExceptionally(any());
            verify(future).complete(null);
        }
    }

    @Nested
    class OnException {
        @Test
        void shouldPassthroughsOnException() {
            HeadContext headContext = new HeadContext(config, pipeline, scheduler, identity, validator);
            Exception exception = mock(Exception.class);

            headContext.exceptionCaught(ctx, exception);

            verify(ctx).fireExceptionCaught(eq(exception));
        }
    }

    @Nested
    class OnEvent {
        @Test
        void shouldPassthroughsOnEvent() {
            HeadContext headContext = new HeadContext(config, pipeline, scheduler, identity, validator);
            Event event = mock(Event.class);

            headContext.eventTriggered(ctx, event, future);

            verify(ctx).fireEventTriggered(eq(event), eq(future));
        }
    }

    @Nested
    class OnRead {
        @Test
        void shouldPassthroughsOnRead() {
            HeadContext headContext = new HeadContext(config, pipeline, scheduler, identity, validator);
            CompressedPublicKey sender = mock(CompressedPublicKey.class);
            Object msg = mock(Object.class);

            headContext.read(ctx, sender, msg, future);

            verify(ctx).fireRead(eq(sender), eq(msg), eq(future));
        }
    }
}