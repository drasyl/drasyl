package org.drasyl.cli.command.wormhole;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TextMessageTest {
    @Nested
    class Equals {
        @Test
        void notSameBecauseOfText() {
            TextMessage message1 = new TextMessage("foo");
            TextMessage message2 = new TextMessage("foo");
            TextMessage message3 = new TextMessage("bar");

            assertEquals(message1, message2);
            assertNotEquals(message2, message3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfText() {
            TextMessage message1 = new TextMessage("foo");
            TextMessage message2 = new TextMessage("foo");
            TextMessage message3 = new TextMessage("bar");

            assertEquals(message1.hashCode(), message2.hashCode());
            assertNotEquals(message2.hashCode(), message3.hashCode());
        }
    }
}