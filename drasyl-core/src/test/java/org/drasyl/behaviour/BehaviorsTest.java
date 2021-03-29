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
package org.drasyl.behaviour;

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.event.Event;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Function;

import static org.drasyl.behaviour.Behavior.IGNORE;
import static org.drasyl.behaviour.Behavior.SAME;
import static org.drasyl.behaviour.Behavior.SHUTDOWN;
import static org.drasyl.behaviour.Behavior.UNHANDLED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(MockitoExtension.class)
class BehaviorsTest {
    @Nested
    class Receive {
        @Test
        void shouldReturnEmptyBehaviorBuilder() {
            assertThat(Behaviors.receive().handlers, empty());
        }
    }

    @Nested
    class Unhandled {
        @Test
        void shouldReturnUnhandledBehavior(@Mock final Event event) {
            assertSame(UNHANDLED, Behaviors.unhandled());
        }
    }

    @Nested
    class Ignore {
        @Test
        void shouldReturnIgnoreBehavior(@Mock final Event event) {
            assertSame(IGNORE, Behaviors.ignore());
            assertSame(IGNORE, Behaviors.ignore().receive(event));
        }
    }

    @Nested
    class Same {
        @Test
        void shouldReturnSameBehavior() {
            assertSame(SAME, Behaviors.same());
        }
    }

    @Nested
    class Shutdown {
        @Test
        void shouldReturnShutdownBehavior(@Mock final Event event) {
            assertSame(SHUTDOWN, Behaviors.shutdown());
            assertSame(IGNORE, Behaviors.shutdown().receive(event));
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Nested
    class WithScheduler {
        @Test
        void shouldReturnDeferredBehavior(@Mock final Function factory,
                                          @Mock final Scheduler scheduler) {
            assertThat(Behaviors.withScheduler(factory), instanceOf(DeferredBehavior.class));
            assertThat(Behaviors.withScheduler(factory, scheduler), instanceOf(DeferredBehavior.class));
        }
    }
}
