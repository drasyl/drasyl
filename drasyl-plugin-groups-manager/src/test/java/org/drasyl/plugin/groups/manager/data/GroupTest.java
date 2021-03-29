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
package org.drasyl.plugin.groups.manager.data;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class GroupTest {
    @Nested
    class Getters {
        @Test
        void shouldReturnCorrectName() {
            final Group group = Group.of("test", "secret", (byte) 0, ofSeconds(60));

            assertEquals("test", group.getName());
        }

        @Test
        void shouldReturnCorrectSecret() {
            final Group group = Group.of("test", "secret", (byte) 0, ofSeconds(60));

            assertEquals("secret", group.getCredentials());
        }

        @Test
        void shouldReturnCorrectMinDifficulty() {
            final Group group = Group.of("test", "secret", (byte) 0, ofSeconds(60));

            assertEquals(0, group.getMinDifficulty());
        }

        @Test
        void shouldReturnCorrectTimeout() {
            final Group group = Group.of("test", "secret", (byte) 0, ofSeconds(60));

            assertEquals(ofSeconds(60), group.getTimeout());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final Group group1 = Group.of("test", "secret", (byte) 0, ofSeconds(60));
            final Group group2 = Group.of("test", "secret", (byte) 0, ofSeconds(60));

            assertEquals(group1, group2);
        }

        @Test
        void shouldNotBeEquals() {
            final Group group1 = Group.of("test1", "secret", (byte) 0, ofSeconds(60));
            final Group group2 = Group.of("test2", "secret", (byte) 0, ofSeconds(60));

            assertNotEquals(group1, group2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final Group group1 = Group.of("test", "secret", (byte) 0, ofSeconds(60));
            final Group group2 = Group.of("test", "secret", (byte) 0, ofSeconds(60));

            assertEquals(group1.hashCode(), group2.hashCode());
        }

        @Test
        void shouldNotBeEquals() {
            final Group group1 = Group.of("test1", "secret", (byte) 0, ofSeconds(60));
            final Group group2 = Group.of("test2", "secret", (byte) 0, ofSeconds(60));

            assertNotEquals(group1.hashCode(), group2.hashCode());
        }
    }

    @Nested
    class Of {
        @Test
        void shouldThrowExceptionOnInvalidMinDifficulty() {
            final Duration timeout = ofSeconds(60);
            assertThrows(IllegalArgumentException.class, () -> Group.of("vip-gang", "secret", (byte) -1, timeout));
        }

        @Test
        void shouldThrowExceptionOnInvalidTimeout() {
            final Duration timeout = ofSeconds(1);
            assertThrows(IllegalArgumentException.class, () -> Group.of("vip-gang", "secret", (byte) 0, timeout));
        }
    }
}
