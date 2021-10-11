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
package org.drasyl.node.behaviour;

import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.behaviour.Behavior.BehaviorBuilder;
import org.drasyl.node.behaviour.Behavior.Case;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.MessageEvent;
import org.drasyl.node.event.Node;
import org.drasyl.node.event.NodeDownEvent;
import org.drasyl.node.event.NodeUpEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.drasyl.node.behaviour.Behavior.SAME;
import static org.drasyl.node.behaviour.Behavior.UNHANDLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;

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

                assertEquals(UNHANDLED, behavior.receive(NodeDownEvent.of(nodeA))); // type check
                assertEquals(UNHANDLED, behavior.receive(NodeUpEvent.of(nodeB))); // predicate check
                assertEquals(newBehavior, behavior.receive(NodeUpEvent.of(nodeA)));
            }

            @Test
            void shouldAddNonPredicatedCase(final @Mock Node node,
                                            final @Mock Behavior newBehavior) {
                final Behavior behavior = BehaviorBuilder.create().onEvent(NodeUpEvent.class, event -> newBehavior).build();

                assertEquals(UNHANDLED, behavior.receive(NodeDownEvent.of(node))); // type check
                assertEquals(newBehavior, behavior.receive(NodeUpEvent.of(node)));
            }
        }

        @Nested
        class OnEventEquals {
            @Test
            void shouldAddCase(final @Mock Node node,
                               final @Mock Behavior newBehavior) {
                final Behavior behavior = BehaviorBuilder.create().onEventEquals(NodeUpEvent.of(node), () -> newBehavior).build();

                assertEquals(UNHANDLED, behavior.receive(NodeDownEvent.of(node))); // equal check
                assertEquals(newBehavior, behavior.receive(NodeUpEvent.of(node)));
            }
        }

        @Nested
        class OnAnyEvent {
            @Test
            void shouldAddCase(final @Mock Node node,
                               final @Mock Behavior newBehavior) {
                final Behavior behavior = BehaviorBuilder.create().onAnyEvent(event -> newBehavior).build();

                assertEquals(newBehavior, behavior.receive(NodeUpEvent.of(node)));
                assertEquals(newBehavior, behavior.receive(NodeDownEvent.of(node)));
            }
        }

        @Nested
        class OnMessage {
            @Test
            void shouldAddPredicatedCase(final @Mock Node node,
                                         final @Mock IdentityPublicKey sender,
                                         final @Mock Behavior newBehavior) {
                final Behavior behavior = BehaviorBuilder.create().onMessage(String.class, (mySender, myPayload) -> myPayload.equals("Hello World"), (mySender, myPayload) -> newBehavior).build();

                assertEquals(UNHANDLED, behavior.receive(NodeDownEvent.of(node))); // type check
                assertEquals(UNHANDLED, behavior.receive(MessageEvent.of(sender, 1337))); // payload check
                assertEquals(UNHANDLED, behavior.receive(MessageEvent.of(sender, "Goodbye"))); // predicate check
                assertEquals(newBehavior, behavior.receive(MessageEvent.of(sender, "Hello World")));
            }

            @Test
            void shouldAddNonPredicatedCase(final @Mock Node node,
                                            final @Mock IdentityPublicKey sender,
                                            final @Mock Behavior newBehavior) {
                final Behavior behavior = BehaviorBuilder.create().onMessage(String.class, (mySender, myPayload) -> newBehavior).build();

                assertEquals(UNHANDLED, behavior.receive(NodeDownEvent.of(node))); // type check
                assertEquals(UNHANDLED, behavior.receive(MessageEvent.of(sender, 1337))); // payload check
                assertEquals(newBehavior, behavior.receive(MessageEvent.of(sender, "Hello World")));
            }
        }

        @Nested
        class OnMessageEquals {
            @Test
            void shouldAddCase(final @Mock IdentityPublicKey senderA,
                               final @Mock IdentityPublicKey senderB,
                               final @Mock Behavior newBehavior) {
                final Behavior behavior = BehaviorBuilder.create().onMessageEquals(senderA, "Hallo Welt", () -> newBehavior).build();

                assertEquals(UNHANDLED, behavior.receive(MessageEvent.of(senderB, "Hallo Welt"))); // equal check (sender)
                assertEquals(UNHANDLED, behavior.receive(MessageEvent.of(senderA, "Goodbye"))); // equal check (payload)
                assertEquals(newBehavior, behavior.receive(MessageEvent.of(senderA, "Hallo Welt")));
            }
        }

        @Nested
        class OnAnyMessage {
            @Test
            void shouldAddCase(final @Mock IdentityPublicKey sender,
                               final @Mock Node node,
                               final @Mock Behavior newBehavior) {
                final Behavior behavior = BehaviorBuilder.create().onAnyMessage((mySender, myPayload) -> newBehavior).build();

                assertEquals(newBehavior, behavior.receive(MessageEvent.of(sender, "Hallo Welt")));
                assertEquals(newBehavior, behavior.receive(MessageEvent.of(sender, 42)));
                assertEquals(UNHANDLED, behavior.receive(NodeDownEvent.of(node)));
            }
        }

        @Nested
        class MessageAdapter {
            @Test
            void shouldAddCase(final @Mock IdentityPublicKey sender,
                               final @Mock Node node,
                               final @Mock DrasylNode drasylNode) {
                final Behavior behavior = BehaviorBuilder.create().messageAdapter(String.class, (Function<String, Object>) String::getBytes).build();

                final Behavior newBehavior = ((DeferredBehavior) behavior.receive(MessageEvent.of(sender, "Hallo Welt"))).apply(drasylNode);

                assertEquals(SAME, newBehavior);
                verify(drasylNode).onEvent(MessageEvent.of(sender, "Hallo Welt".getBytes()));
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
