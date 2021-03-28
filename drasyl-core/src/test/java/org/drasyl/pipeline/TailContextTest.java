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
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TailContextTest {
    @Mock
    private Consumer<Event> eventConsumer;
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
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

            assertEquals(tailContext, tailContext.handler());
        }

        @Test
        void shouldDoNothingOnHandlerAdded() {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

            tailContext.onAdded(ctx);

            verifyNoInteractions(ctx);
        }

        @Test
        void shouldDoNothingOnHandlerRemoved() {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

            tailContext.onRemoved(ctx);

            verifyNoInteractions(ctx);
        }
    }

    @Nested
    class OnWrite {
        @Test
        void shouldPassthroughsOnWrite(@Mock final CompressedPublicKey recipient,
                                       @Mock final Object msg) {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

            tailContext.onOutbound(ctx, recipient, msg, future);

            verify(ctx).passOutbound(recipient, msg, future);
        }
    }

    @Nested
    class OnException {
        @Test
        void shouldSkipOnException(@Mock final Exception exception) {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            final AbstractHandlerContext actx = new AbstractHandlerContext(tailContext, tailContext, "", config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
                @Override
                public Handler handler() {
                    return null;
                }

                @Override
                void executeOnDependentScheduler(final Runnable task) {
                    task.run();
                }

                @Override
                protected Logger log() {
                    return null;
                }
            };

            assertDoesNotThrow(() -> actx.passException(exception));
            verifyNoInteractions(ctx);
        }
    }

    @Nested
    class OnEvent {
        @Test
        void shouldPassEventToConsumer(@Mock final Event event) {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

            tailContext.onEvent(ctx, event, future);

            verify(eventConsumer).accept(event);
            verifyNoInteractions(ctx);
        }
    }

    @Nested
    class OnRead {
        @Test
        void shouldPassMessageToApplication(@Mock final CompressedPublicKey sender,
                                            @Mock final Object msg) {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

            tailContext.onInbound(ctx, sender, msg, future);

            verify(eventConsumer).accept(MessageEvent.of(sender, msg));
            verifyNoInteractions(ctx);
        }

        @Test
        void shouldCompleteFutureAndNothingElseOnAutoSwallow(@Mock final CompressedPublicKey recipient) {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            final AutoSwallow msg = new AutoSwallow() {
            };

            tailContext.onInbound(ctx, recipient, msg, future);

            verify(future, never()).completeExceptionally(any());
            verify(future).complete(null);
        }
    }
}
