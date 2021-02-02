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

import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * The behavior of an node defines how it reacts to the events that it receives.
 * <p>
 * Behaviors can be formulated in a number of different ways, either by using the DSLs in {@link
 * Behaviors}.
 */
public class Behavior {
    static final Behavior UNHANDLED = new Behavior(List.of()) {
        @Override
        public String toString() {
            return "Behavior.UNHANDLED";
        }
    };
    static final Behavior IGNORE = new Behavior(List.of()) {
        @Override
        public String toString() {
            return "Behavior.IGNORE";
        }

        @Override
        public Behavior receive(final Event event) {
            return this;
        }
    };
    static final Behavior SAME = new Behavior(List.of()) {
        @Override
        public String toString() {
            return "Behavior.SAME";
        }
    };
    static final Behavior SHUTDOWN = new Behavior(List.of()) {
        @Override
        public String toString() {
            return "Behavior.SHUTDOWN";
        }

        @Override
        public Behavior receive(final Event event) {
            return IGNORE;
        }
    };
    private final List<Case<Event>> handlers;

    Behavior(final List<Case<Event>> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    /**
     * Process an incoming event and return the next behavior.
     * <p>
     * The returned behavior can in addition to normal behaviors be one of the canned special
     * objects:
     * <ul>
     * <li>returning {@link #UNHANDLED} keeps the same behavior and signals that the event was not handled (event will be logged with DEBUG level).</li>
     * <li>returning {@link #IGNORE} will ignore all future events.</li>
     * <li>returning {@link #SAME} designates to reuse the current behavior.</li>
     * <li>returning {@link #SHUTDOWN} will shutdown the {@link org.drasyl.DrasylNode}.</li>
     * </ul>
     */
    public Behavior receive(final Event event) {
        for (final Case<Event> handler : handlers) {
            if (handler.getType().isAssignableFrom(event.getClass()) && handler.getTest().test(event)) {
                return requireNonNull(handler.getHandler().apply(event), "new behavior must not be null");
            }
        }

        return UNHANDLED;
    }

    /**
     * Immutable builder for creating {@link Behavior} by chaining event handlers.
     * <p>
     * When handling an event, this {@link Behavior} will consider all handlers in the order they
     * were added, looking for the first handler for with both the type and the (optional) predicate
     * match.
     */
    public static class BehaviorBuilder {
        private static final BehaviorBuilder EMPTY = new BehaviorBuilder(List.of());
        final List<Case<Event>> handlers;

        /**
         * @throws NullPointerException if {@code handlers} is {@code null}, or if it contains any
         *                              {@code null} element
         */
        BehaviorBuilder(final List<Case<Event>> handlers) {
            this.handlers = List.copyOf(handlers);
        }

        /**
         * Adds a new predicated case to the event handling.
         *
         * @param type    type of the event to match
         * @param test    a predicate that will be evaluated on the argument if the type matches
         * @param handler action to apply if the type matches
         * @param <M>     type of event to match
         * @return a new {@link BehaviorBuilder} with the specified handling appended
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public <M extends Event> BehaviorBuilder onEvent(final Class<M> type,
                                                         final Predicate<M> test,
                                                         final Function<M, Behavior> handler) {
            final Case newCase = new Case<>(type, test, handler);
            final List<Case<Event>> newHandlers = new ArrayList<>(handlers);
            newHandlers.add(newCase);
            return new BehaviorBuilder(newHandlers);
        }

        /**
         * Adds a new case to the event handling.
         *
         * @param type    type of the event to match
         * @param handler action to apply if the type matches
         * @param <M>     type of event to match
         * @return a new {@link BehaviorBuilder} with the specified handling appended
         */
        public <M extends Event> BehaviorBuilder onEvent(final Class<M> type,
                                                         final Function<M, Behavior> handler) {
            return onEvent(type, event -> true, handler);
        }

        /**
         * Add a new case to the event handling matching equal events.
         *
         * @param event   the event to compare to
         * @param handler action to apply when the event matches
         * @return a new {@link BehaviorBuilder} with the specified handling appended
         */
        public <M extends Event> BehaviorBuilder onEventEquals(final M event,
                                                               final Supplier<Behavior> handler) {
            return onEvent(event.getClass(), myEvent -> myEvent.equals(event), myEvent -> handler.get());
        }

        /**
         * Adds a new case to the event handling matching any event. Subsequent {@code onEvent(...)}
         * and {@code onMessage(...)} clauses will never see any events.
         *
         * @param handler action to apply for any event
         * @return a new {@link BehaviorBuilder} with the specified handling appended
         */
        public BehaviorBuilder onAnyEvent(final Function<Event, Behavior> handler) {
            return onEvent(Event.class, handler);
        }

        /**
         * Add a new predicated case to the event matching matching events of type {@class
         * MessageEvent} with {@link MessageEvent#getPayload()} matching {@code messageType}.
         *
         * @param messageType type of the event to match
         * @param test        a predicate that will be evaluated on the argument if the type
         *                    matches
         * @param handler     action to apply if the type matches
         * @param <M>         type of event to match
         * @return a new {@link BehaviorBuilder} with the specified handling appended
         */
        @SuppressWarnings("unchecked")
        public <M> BehaviorBuilder onMessage(final Class<M> messageType,
                                             final BiPredicate<CompressedPublicKey, M> test,
                                             final BiFunction<CompressedPublicKey, M, Behavior> handler) {
            return onEvent(
                    MessageEvent.class,
                    event -> messageType.isAssignableFrom(event.getPayload().getClass()) && test.test(event.getSender(), (M) event.getPayload()),
                    event -> handler.apply(event.getSender(), (M) event.getPayload()));
        }

        /**
         * Add a new case to the event matching matching events of type {@class MessageEvent} with
         * {@link MessageEvent#getPayload()} matching {@code messageType}.
         *
         * @param messageType type of the event to match
         * @param handler     action to apply if the type matches
         * @param <M>         type of event to match
         * @return a new {@link BehaviorBuilder} with the specified handling appended
         */
        public <M> BehaviorBuilder onMessage(final Class<M> messageType,
                                             final BiFunction<CompressedPublicKey, M, Behavior> handler) {
            return onMessage(messageType, (sender, message) -> true, handler);
        }

        /**
         * Add a new case to the event handling matching events of type {@class MessageEvent} with
         * equal {@link MessageEvent#getPayload()}.
         *
         * @param sender  the message sender to compare to
         * @param payload the message payload to compare to
         * @param handler action to apply when the event matches
         * @return a new {@link BehaviorBuilder} with the specified handling appended
         */
        public <M> BehaviorBuilder onMessageEquals(final CompressedPublicKey sender,
                                                   final M payload,
                                                   final Supplier<Behavior> handler) {
            return onMessage(
                    payload.getClass(),
                    (mySender, myPayload) -> mySender.equals(sender) && myPayload.equals(payload),
                    (mySender, myMessage) -> handler.get()
            );
        }

        /**
         * Adds a new case to the event handling matching any {@class MessageEvent}. Subsequent
         * {@code onMessage(...)} clauses will never see any messages.
         *
         * @param handler action to apply for any message
         * @return a new {@link BehaviorBuilder} with the specified handling appended
         */
        @SuppressWarnings("unchecked")
        public <M> BehaviorBuilder onAnyMessage(final BiFunction<CompressedPublicKey, M, Behavior> handler) {
            return onEvent(
                    MessageEvent.class,
                    event -> handler.apply(event.getSender(), (M) event.getPayload())
            );
        }

        /**
         * Build a behavior from the current state of the builder.
         */
        public Behavior build() {
            return new Behavior(handlers);
        }

        /**
         * @return new empty immutable {@link BehaviorBuilder}.
         */
        static BehaviorBuilder create() {
            return EMPTY;
        }
    }

    static class Case<T> {
        private final Class<? extends T> type;
        private final Predicate<T> test;
        private final Function<T, Behavior> handler;

        /**
         * @throws NullPointerException if {@code type}, {@code test}, or {@code handler} is {@code
         *                              null}
         */
        public Case(final Class<? extends T> type,
                    final Predicate<T> test,
                    final Function<T, Behavior> handler) {
            this.type = requireNonNull(type);
            this.test = requireNonNull(test);
            this.handler = requireNonNull(handler);
        }

        Class<? extends T> getType() {
            return type;
        }

        Predicate<T> getTest() {
            return test;
        }

        Function<T, Behavior> getHandler() {
            return handler;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Case<?> aCase = (Case<?>) o;
            return Objects.equals(type, aCase.type) && Objects.equals(test, aCase.test);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, test);
        }

        @Override
        public String toString() {
            return "Case{" +
                    "type=" + type +
                    '}';
        }
    }
}
