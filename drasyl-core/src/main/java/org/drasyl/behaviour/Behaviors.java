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
package org.drasyl.behaviour;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.behaviour.Behavior.BehaviorBuilder;
import org.drasyl.event.Event;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.scheduler.DrasylSchedulerUtil.getInstanceLight;

/**
 * Factories for {@link Behavior}.
 * <p>
 * Inspired by: <a href="https://doc.akka.io/docs/akka/current/typed/fsm.html">https://doc.akka.io/docs/akka/current/typed/fsm.html</a>
 */
public final class Behaviors {
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
     * @param factory   function that returns the behavior that should react to scheduled self
     *                  events
     * @param scheduler the {@code Scheduler} to perform scheduled events on
     */
    public static Behavior withScheduler(final Function<EventScheduler, Behavior> factory,
                                         final Scheduler scheduler) {
        return new DeferredBehavior(node -> factory.apply(new EventScheduler(node::onEvent, scheduler)));
    }

    /**
     * A behavior with support for scheduled self events in a node.
     *
     * @param factory function that returns the behavior that should react to scheduled self events
     */
    public static Behavior withScheduler(final Function<EventScheduler, Behavior> factory) {
        return withScheduler(factory, getInstanceLight());
    }

    public static class EventScheduler {
        private final Consumer<Event> consumer;
        private final Scheduler scheduler;

        EventScheduler(final Consumer<Event> consumer, final Scheduler scheduler) {
            this.consumer = requireNonNull(consumer);
            this.scheduler = requireNonNull(scheduler);
        }

        /**
         * Schedules a self event.
         *
         * @param event event to schedule
         * @param delay delay before emitting the event
         * @return {@link Disposable} allowing to cancel the scheduled event
         */
        @SuppressWarnings({ "UnusedReturnValue", "unused" })
        public Disposable scheduleEvent(final Event event, final Duration delay) {
            return scheduler.scheduleDirect(() -> consumer.accept(event), delay.toMillis(), MILLISECONDS);
        }

        /**
         * Schedules a self event.
         *
         * @param event event to schedule
         * @return {@link Disposable} allowing to cancel the scheduled event
         */
        @SuppressWarnings({ "UnusedReturnValue", "unused" })
        public Disposable scheduleEvent(final Event event) {
            return scheduler.scheduleDirect(() -> consumer.accept(event));
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
        @SuppressWarnings({ "UnusedReturnValue", "unused" })
        public Disposable schedulePeriodicallyEvent(final Event event,
                                                    final Duration initialDelay,
                                                    final Duration period) {
            return scheduler.schedulePeriodicallyDirect(() -> consumer.accept(event), initialDelay.toMillis(), period.toMillis(), MILLISECONDS);
        }
    }
}
