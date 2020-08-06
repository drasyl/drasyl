package org.drasyl.event;

import org.drasyl.identity.CompressedPublicKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class MessageEventTest {
    @Mock
    private CompressedPublicKey sender;
    @Mock
    private Object message;

    @Nested
    class GetMessage {
        @Test
        void shouldReturnMessage() {
            MessageEvent event = new MessageEvent(sender, message);

            assertEquals(message, event.getPayload());
        }
    }

    @Nested
    class Equals {
        @Mock
        private Object message2;

        @Test
        void notSameBecauseOfDifferentMessage() {
            MessageEvent event1 = new MessageEvent(sender, message);
            MessageEvent event2 = new MessageEvent(sender, message);
            MessageEvent event3 = new MessageEvent(sender, message2);

            assertEquals(event1, event2);
            assertNotEquals(event1, event3);
        }
    }

    @Nested
    class HashCode {
        @Mock
        private Object message2;

        @Test
        void notSameBecauseOfDifferentMessage() {
            MessageEvent event1 = new MessageEvent(sender, message);
            MessageEvent event2 = new MessageEvent(sender, message);
            MessageEvent event3 = new MessageEvent(sender, message2);

            assertEquals(event1.hashCode(), event2.hashCode());
            assertNotEquals(event1.hashCode(), event3.hashCode());
        }
    }
}