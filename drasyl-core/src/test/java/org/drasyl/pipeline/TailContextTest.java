/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.pipeline;

import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
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
        void shouldPassthroughsOnWrite(@Mock final IdentityPublicKey recipient,
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
                void executeOnDependentScheduler(final Runnable task,
                                                 final Runnable releaseOnRejectedExecutionException) {
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
        void shouldPassMessageToApplication(@Mock final IdentityPublicKey sender,
                                            @Mock final Object msg) {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

            tailContext.onInbound(ctx, sender, msg, future);

            verify(eventConsumer).accept(MessageEvent.of(sender, msg));
            verifyNoInteractions(ctx);
        }

        @Test
        void shouldCompleteFutureAndNothingElseOnAutoSwallow(@Mock final IdentityPublicKey recipient) {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            final AutoSwallow msg = new AutoSwallow() {
            };

            tailContext.onInbound(ctx, recipient, msg, future);

            verify(future, never()).completeExceptionally(any());
            verify(future).complete(null);
        }
    }
}
