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

import org.drasyl.behaviour.Behavior.BehaviorBuilder;
import org.drasyl.behaviour.Behavior.Case;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.Node;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.drasyl.behaviour.Behavior.UNHANDLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(MockitoExtension.class)
class BehaviorTest {
    @Nested
    class Receive {
        @Test
        void shouldFireFirstHandlerMatchingTypeAndPredicate(@Mock final Behavior newBehaviorA,
                                                            @Mock final Behavior newBehaviorB,
                                                            @Mock final NodeUpEvent event) {
            final Case<Event> handlerA = new Case<>(NodeDownEvent.class, m -> false, m -> newBehaviorA);
            final Case<Event> handlerB = new Case<>(NodeUpEvent.class, m -> true, m -> newBehaviorB);

            final Behavior behavior = new Behavior(List.of(handlerA, handlerB));
            assertSame(newBehaviorB, behavior.receive(event));
        }

        @Test
        void shouldReturnUnhandledBehaviorIfNoHandlerMatches(@Mock final NodeUpEvent event) {
            final Behavior behavior = new Behavior(List.of());
            assertSame(UNHANDLED, behavior.receive(event));
        }
    }

    @Nested
    class TestBehaviorBuilder {
        @Nested
        class OnEvent {
            @Test
            void shouldAddPredicatedCase(final @Mock Node nodeA,
                                         final @Mock Node nodeB,
                                         final @Mock Behavior newBehavior) {
                final Behavior behavior = BehaviorBuilder.create().onEvent(NodeUpEvent.class, event -> event.getNode().equals(nodeA), event -> newBehavior).build();

                assertEquals(UNHANDLED, behavior.receive(new NodeDownEvent(nodeA))); // type check
                assertEquals(UNHANDLED, behavior.receive(new NodeUpEvent(nodeB))); // predicate check
                assertEquals(newBehavior, behavior.receive(new NodeUpEvent(nodeA)));
            }

            @Test
            void shouldAddNonPredicatedCase(final @Mock Node node,
                                            final @Mock Behavior newBehavior) {
                final Behavior behavior = BehaviorBuilder.create().onEvent(NodeUpEvent.class, event -> newBehavior).build();

                assertEquals(UNHANDLED, behavior.receive(new NodeDownEvent(node))); // type check
                assertEquals(newBehavior, behavior.receive(new NodeUpEvent(node)));
            }
        }

        @Nested
        class OnEventEquals {
            @Test
            void shouldAddCase(final @Mock Node node,
                               final @Mock Behavior newBehavior) {
                final Behavior behavior = BehaviorBuilder.create().onEventEquals(new NodeUpEvent(node), () -> newBehavior).build();

                assertEquals(UNHANDLED, behavior.receive(new NodeDownEvent(node))); // equal check
                assertEquals(newBehavior, behavior.receive(new NodeUpEvent(node)));
            }
        }

        @Nested
        class OnAnyEvent {
            @Test
            void shouldAddCase(final @Mock Node node,
                               final @Mock Behavior newBehavior) {
                final Behavior behavior = BehaviorBuilder.create().onAnyEvent(event -> newBehavior).build();

                assertEquals(newBehavior, behavior.receive(new NodeUpEvent(node)));
                assertEquals(newBehavior, behavior.receive(new NodeDownEvent(node)));
            }
        }

        @Nested
        class OnMessage {
            @Test
            void shouldAddPredicatedCase(final @Mock Node node,
                                         final @Mock CompressedPublicKey sender,
                                         final @Mock Behavior newBehavior) {
                final Behavior behavior = BehaviorBuilder.create().onMessage(String.class, (mySender, myPayload) -> myPayload.equals("Hello World"), (mySender, myPayload) -> newBehavior).build();

                assertEquals(UNHANDLED, behavior.receive(new NodeDownEvent(node))); // type check
                assertEquals(UNHANDLED, behavior.receive(new MessageEvent(sender, 1337))); // payload check
                assertEquals(UNHANDLED, behavior.receive(new MessageEvent(sender, "Goodbye"))); // predicate check
                assertEquals(newBehavior, behavior.receive(new MessageEvent(sender, "Hello World")));
            }

            @Test
            void shouldAddNonPredicatedCase(final @Mock Node node,
                                            final @Mock CompressedPublicKey sender,
                                            final @Mock Behavior newBehavior) {
                final Behavior behavior = BehaviorBuilder.create().onMessage(String.class, (mySender, myPayload) -> newBehavior).build();

                assertEquals(UNHANDLED, behavior.receive(new NodeDownEvent(node))); // type check
                assertEquals(UNHANDLED, behavior.receive(new MessageEvent(sender, 1337))); // payload check
                assertEquals(newBehavior, behavior.receive(new MessageEvent(sender, "Hello World")));
            }
        }

        @Nested
        class OnMessageEquals {
            @Test
            void shouldAddCase(final @Mock CompressedPublicKey senderA,
                               final @Mock CompressedPublicKey senderB,
                               final @Mock Behavior newBehavior) {
                final Behavior behavior = BehaviorBuilder.create().onMessageEquals(senderA, "Hallo Welt", () -> newBehavior).build();

                assertEquals(UNHANDLED, behavior.receive(new MessageEvent(senderB, "Hallo Welt"))); // equal check (sender)
                assertEquals(UNHANDLED, behavior.receive(new MessageEvent(senderA, "Goodbye"))); // equal check (payload)
                assertEquals(newBehavior, behavior.receive(new MessageEvent(senderA, "Hallo Welt")));
            }
        }

        @Nested
        class OnAnyMessage {
            @Test
            void shouldAddCase(final @Mock CompressedPublicKey sender,
                               final @Mock Node node,
                               final @Mock Behavior newBehavior) {
                final Behavior behavior = BehaviorBuilder.create().onAnyMessage((mySender, myPayload) -> newBehavior).build();

                assertEquals(newBehavior, behavior.receive(new MessageEvent(sender, "Hallo Welt")));
                assertEquals(newBehavior, behavior.receive(new MessageEvent(sender, 42)));
                assertEquals(UNHANDLED, behavior.receive(new NodeDownEvent(node)));
            }
        }
    }

    @Nested
    class TestCase {
        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Nested
        class Equals {
            @Test
            void shouldRecognizeEqualCases(final @Mock Predicate test,
                                           final @Mock Function handler) {
                final Case caseA = new Case(Event.class, test, handler);
                final Case caseB = new Case(Event.class, test, handler);
                final Case caseC = new Case(NodeDownEvent.class, test, handler);

                assertEquals(caseA, caseA);
                assertEquals(caseA, caseB);
                assertEquals(caseB, caseA);
                assertNotEquals(null, caseA);
                assertNotEquals(caseA, caseC);
                assertNotEquals(caseC, caseA);
            }
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Nested
        class HashCode {
            @Test
            void shouldRecognizeEqualCases(final @Mock Predicate test,
                                           final @Mock Function handler) {
                final Case caseA = new Case(Event.class, test, handler);
                final Case caseB = new Case(Event.class, test, handler);
                final Case caseC = new Case(NodeDownEvent.class, test, handler);

                assertEquals(caseA.hashCode(), caseB.hashCode());
                assertNotEquals(caseA.hashCode(), caseC.hashCode());
                assertNotEquals(caseB.hashCode(), caseC.hashCode());
            }
        }
    }
}
