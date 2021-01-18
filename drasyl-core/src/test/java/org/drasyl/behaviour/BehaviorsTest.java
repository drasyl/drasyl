/*
 * Copyright (c) 2021.
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