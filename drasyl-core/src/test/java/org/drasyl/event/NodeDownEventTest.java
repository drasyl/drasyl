package org.drasyl.event;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class NodeDownEventTest {
    @Mock
    private Node node;

    @Nested
    class GetMessage {
        @Test
        void shouldReturnMessage() {
            NodeDownEvent event = new NodeDownEvent(node);

            assertEquals(node, event.getNode());
        }
    }

    @Nested
    class Equals {
        @Mock
        private Node node2;

        @Test
        void notSameBecauseOfDifferentMessage() {
            NodeDownEvent event1 = new NodeDownEvent(node);
            NodeDownEvent event2 = new NodeDownEvent(node);
            NodeDownEvent event3 = new NodeDownEvent(node2);

            assertEquals(event1, event2);
            assertNotEquals(event1, event3);
        }
    }

    @Nested
    class HashCode {
        @Mock
        private Node node2;

        @Test
        void notSameBecauseOfDifferentMessage() {
            NodeDownEvent event1 = new NodeDownEvent(node);
            NodeDownEvent event2 = new NodeDownEvent(node);
            NodeDownEvent event3 = new NodeDownEvent(node2);

            assertEquals(event1.hashCode(), event2.hashCode());
            assertNotEquals(event1.hashCode(), event3.hashCode());
        }
    }
}