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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class HeadContextTest {
    @Mock
    private HandlerContext ctx;
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

    @Nested
    class InGeneral {
        @Test
        void shouldReturnSelfAsHandler() {
            final HeadContext headContext = new HeadContext(config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

            assertEquals(headContext, headContext.handler());
        }

        @Test
        void shouldDoNothingOnHandlerAdded() {
            final HeadContext headContext = new HeadContext(config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

            headContext.onAdded(ctx);

            verifyNoInteractions(ctx);
        }

        @Test
        void shouldDoNothingOnHandlerRemoved() {
            final HeadContext headContext = new HeadContext(config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

            headContext.onRemoved(ctx);

            verifyNoInteractions(ctx);
        }
    }

    @Nested
    class OnWrite {
        @Test
        void shouldWriteCompleteExceptionallyIfFutureIsNotCompleted(@Mock final Object msg,
                                                                    @Mock final CompressedPublicKey recipient) {
            final HeadContext headContext = new HeadContext(config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

            headContext.onOutbound(ctx, recipient, msg, future);

            verify(future).completeExceptionally(isA(IllegalStateException.class));
        }

        @Test
        void shouldCompleteFutureAndNothingElseOnAutoSwallow(@Mock final CompressedPublicKey recipient) {
            final HeadContext headContext = new HeadContext(config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            final AutoSwallow msg = new AutoSwallow() {
            };

            headContext.onOutbound(ctx, recipient, msg, future);

            verify(future, never()).completeExceptionally(any());
            verify(future).complete(null);
        }

        @Test
        void shouldAutoReleaseByteBuf(@Mock final Address address) {
            final HeadContext headContext = new HeadContext(config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            final ByteBuf byteBuf = Unpooled.buffer();

            headContext.onOutbound(ctx, address, byteBuf, CompletableFuture.completedFuture(null));

            assertEquals(0, byteBuf.refCnt());
        }
    }

    @Nested
    class OnException {
        @Test
        void shouldPassthroughsOnException(@Mock final Exception exception) {
            final HeadContext headContext = new HeadContext(config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

            headContext.onException(ctx, exception);

            verify(ctx).passException(exception);
        }
    }

    @Nested
    class OnEvent {
        @Test
        void shouldPassthroughsOnEvent(@Mock final Event event) {
            final HeadContext headContext = new HeadContext(config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

            headContext.onEvent(ctx, event, future);

            verify(ctx).passEvent(event, future);
        }
    }

    @Nested
    class OnRead {
        @Test
        void shouldPassthroughsOnRead(@Mock final CompressedPublicKey sender,
                                      @Mock final Object msg) {
            final HeadContext headContext = new HeadContext(config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

            headContext.onInbound(ctx, sender, msg, future);

            verify(ctx).passInbound(sender, msg, future);
        }
    }
}
