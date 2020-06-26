package org.drasyl.event;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MessageEventTest {
    @Mock
    private Pair<CompressedPublicKey, byte[]> message;

    @Nested
    class GetMessage {
        @Test
        void shouldReturnMessage() {
            MessageEvent event = new MessageEvent(message);

            assertEquals(message, event.getMessage());
        }
    }

    @Nested
    class Equals {
        @Mock
        private Pair<CompressedPublicKey, byte[]> message2;

        @Test
        void notSameBecauseOfDifferentMessage() {
            MessageEvent event1 = new MessageEvent(message);
            MessageEvent event2 = new MessageEvent(message);
            MessageEvent event3 = new MessageEvent(message2);

            assertTrue(event1.equals(event1));
            assertEquals(event1, event2);
            assertNotEquals(event1, event3);
            assertFalse(event1.equals(null));
        }
    }

    @Nested
    class HashCode {
        @Mock
        private Pair<CompressedPublicKey, byte[]> message2;

        @Test
        void notSameBecauseOfDifferentMessage() {
            MessageEvent event1 = new MessageEvent(message);
            MessageEvent event2 = new MessageEvent(message);
            MessageEvent event3 = new MessageEvent(message2);

            assertEquals(event1.hashCode(), event2.hashCode());
            assertNotEquals(event1.hashCode(), event3.hashCode());
        }
    }
}