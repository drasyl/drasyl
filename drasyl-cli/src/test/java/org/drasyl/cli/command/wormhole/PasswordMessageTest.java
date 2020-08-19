package org.drasyl.cli.command.wormhole;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordMessageTest {
    @Nested
    class Equals {
        @Test
        void notSameBecauseOfText() {
            PasswordMessage message1 = new PasswordMessage("foo");
            PasswordMessage message2 = new PasswordMessage("foo");
            PasswordMessage message3 = new PasswordMessage("bar");

            assertEquals(message1, message2);
            assertNotEquals(message2, message3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfText() {
            PasswordMessage message1 = new PasswordMessage("foo");
            PasswordMessage message2 = new PasswordMessage("foo");
            PasswordMessage message3 = new PasswordMessage("bar");

            assertEquals(message1.hashCode(), message2.hashCode());
            assertNotEquals(message2.hashCode(), message3.hashCode());
        }
    }
}