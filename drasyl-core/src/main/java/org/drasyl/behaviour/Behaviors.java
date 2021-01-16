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

import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.behaviour.Behavior.BehaviorBuilder;
import org.drasyl.event.Event;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.scheduler.DrasylSchedulerUtil.getInstanceLight;

/**
 * Factories for {@link Behavior}.
 * <p>
 * Inspired by: <a href="https://doc.akka.io/docs/akka/current/typed/fsm.html">https://doc.akka.io/docs/akka/current/typed/fsm.html</a>
 */
public class Behaviors {
    private Behaviors() {
        // util class
    }

    public static BehaviorBuilder receive() {
        return BehaviorBuilder.create();
    }

    /**
     * A behavior that keeps the same behavior and signals that the event was not handled (event
     * will be logged with DEBUG level).
     */
    public static Behavior unhandled() {
        return Behavior.UNHANDLED;
    }

    /**
     * A behavior that ignores every incoming message.
     */
    public static Behavior ignore() {
        return Behavior.IGNORE;
    }

    /**
     * A behavior that advises the system to reuse the previous behavior. This is provided in order
     * to avoid the allocation overhead of recreating the current behavior where that is not
     * necessary.
     */
    public static Behavior same() {
        return Behavior.SAME;
    }

    /**
     * A behavior that advises the system to shutdown the {@link org.drasyl.DrasylNode}. Subsequent
     * events will be ignored.
     */
    public static Behavior shutdown() {
        return Behavior.SHUTDOWN;
    }

    /**
     * A behavior with support for scheduled self events in a node.
     *
     * @param factory function that returns the behavior that should react to scheduled self events
     */
    public static Behavior withScheduler(final Function<EventScheduler, Behavior> factory) {
        return new DeferredBehavior(node -> factory.apply(new EventScheduler(node::onEvent)));
    }

    public static class EventScheduler {
        private final Consumer<Event> consumer;

        EventScheduler(final Consumer<Event> consumer) {
            this.consumer = consumer;
        }

        /**
         * Schedules a self event.
         *
         * @param event event to schedule
         * @param delay delay before emitting the event
         * @return {@link Disposable} allowing to cancel the scheduled event
         */
        @SuppressWarnings("UnusedReturnValue")
        public Disposable scheduleEvent(final Event event, final Duration delay) {
            return getInstanceLight().scheduleDirect(() -> consumer.accept(event), delay.toMillis(), MILLISECONDS);
        }

        /**
         * Schedules a self event.
         *
         * @param event        event to schedule
         * @param initialDelay the initial delay amount, non-positive values indicate non-delayed
         *                     scheduling
         * @param period       the period at which the event should be re-emitted
         * @return {@link Disposable} allowing to cancel the scheduled event
         */
        public Disposable schedulePeriodicallyEvent(final Event event,
                                                    final Duration initialDelay,
                                                    final Duration period) {
            return getInstanceLight().schedulePeriodicallyDirect(() -> consumer.accept(event), initialDelay.toMillis(), period.toMillis(), MILLISECONDS);
        }
    }
}
