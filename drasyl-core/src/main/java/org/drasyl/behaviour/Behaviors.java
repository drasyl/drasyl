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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.behaviour.Behavior.BehaviorBuilder;
import org.drasyl.event.Event;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Factories for {@link Behavior}.
 * <p>
 * Inspired by: <a href="https://doc.akka.io/docs/akka/current/typed/fsm.html">https://doc.akka.io/docs/akka/current/typed/fsm.html</a>
 */
public final class Behaviors {
    static volatile boolean eventLoopGroupCreated;

    private Behaviors() {
        // util class
    }

    /**
     * Creates a new {@link BehaviorBuilder} to build a new {@link Behavior} for inbound
     * message/event handling.
     * <p>
     * Typically used from {@link BehavioralDrasylNode#newBehaviorBuilder()}.
     */
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
     * @param factory        function that returns the behavior that should react to scheduled self
     *                       events
     * @param eventLoopGroup the {@code Scheduler} to perform scheduled events on
     */
    public static Behavior withScheduler(final Function<EventScheduler, Behavior> factory,
                                         final EventLoopGroup eventLoopGroup) {
        return new DeferredBehavior(node -> factory.apply(new EventScheduler(node::onEvent, eventLoopGroup)));
    }

    /**
     * A behavior with support for scheduled self events in a node.
     *
     * @param factory function that returns the behavior that should react to scheduled self events
     */
    public static Behavior withScheduler(final Function<EventScheduler, Behavior> factory) {
        return withScheduler(factory, eventLoopGroup());
    }

    public static class EventScheduler {
        private final Consumer<Event> consumer;
        private final EventLoopGroup eventLoopGroup;

        EventScheduler(final Consumer<Event> consumer, final EventLoopGroup eventLoopGroup) {
            this.consumer = requireNonNull(consumer);
            this.eventLoopGroup = requireNonNull(eventLoopGroup);
        }

        /**
         * Schedules a self event.
         *
         * @param event event to schedule
         * @param delay delay before emitting the event
         * @return {@link Future} allowing to cancel the scheduled event
         */
        @SuppressWarnings({ "UnusedReturnValue", "unused", "java:S1452" })
        public Future<?> scheduleEvent(final Event event, final Duration delay) {
            return eventLoopGroup.schedule(() -> consumer.accept(event), delay.toMillis(), MILLISECONDS);
        }

        /**
         * Schedules a self event.
         *
         * @param event event to schedule
         * @return {@link Future} allowing to cancel the scheduled event
         */
        @SuppressWarnings({ "UnusedReturnValue", "unused", "java:S1452" })
        public Future<?> scheduleEvent(final Event event) {
            return eventLoopGroup.submit(() -> consumer.accept(event));
        }

        /**
         * Schedules a self event.
         *
         * @param event        event to schedule
         * @param initialDelay the initial delay amount, non-positive values indicate non-delayed
         *                     scheduling
         * @param period       the period at which the event should be re-emitted
         * @return {@link Future} allowing to cancel the scheduled event
         */
        @SuppressWarnings({ "UnusedReturnValue", "unused", "java:S1452" })
        public Future<?> schedulePeriodicallyEvent(final Event event,
                                                   final Duration initialDelay,
                                                   final Duration period) {
            return eventLoopGroup.scheduleAtFixedRate(() -> consumer.accept(event), initialDelay.toMillis(), period.toMillis(), MILLISECONDS);
        }
    }

    /**
     * Returns a default, shared {@link EventLoopGroup} instance intended for {@link Behavior}
     * scheduling.
     *
     * @return a {@code EventLoopGroup} meant for {@link Behavior}-bound work
     */
    public static EventLoopGroup eventLoopGroup() {
        return LazyEventLoopGroupHolder.INSTANCE;
    }

    private static final class LazyEventLoopGroupHolder {
        public static final int DEFAULT_THREADS = 2;
        static final int SIZE;

        static {
            SIZE = SystemPropertyUtil.getInt("org.drasyl.behavior.event-loop", DEFAULT_THREADS);
        }

        static final NioEventLoopGroup INSTANCE = new NioEventLoopGroup(SIZE);
        @SuppressWarnings("unused")
        static final boolean LOCK = eventLoopGroupCreated = true;
    }
}
