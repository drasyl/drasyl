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
package org.drasyl.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class WormTest {
    @Nested
    class Get {
        @Test
        void shouldReturnValueIfPresent() {
            final Worm<String> worm = new Worm<>("Merry Christmas!");

            assertEquals("Merry Christmas!", worm.get());
        }

        @Test
        void shouldReturnValueIfNullValuePresent() {
            final Worm<String> worm = new Worm<>(null);

            assertNull(worm.get());
        }

        @Test
        void shouldThrowExceptionIfEmpty() {
            final Worm<String> worm = new Worm<>();

            assertThrows(NoSuchElementException.class, worm::get);
        }
    }

    @Nested
    class Set {
        @Test
        void shouldSetValueIfWormIsEmpty() {
            final Worm<String> worm = new Worm<>();

            assertDoesNotThrow(() -> worm.set("Hello World"));
            assertEquals("Hello World", worm.get());
        }

        @Test
        void shouldAbleToSetValueToNull() {
            final Worm<String> worm = new Worm<>();

            assertDoesNotThrow(() -> worm.set(null));
            assertNull(worm.get());
        }

        @Test
        void shouldThrowExceptionIfWormIsNotEmpty() {
            final Worm<String> worm = new Worm<>("Alice");

            assertThrows(IllegalStateException.class, () -> worm.set("Bob"));
        }
    }

    @Nested
    class GetOrSet {
        @Test
        void shouldSetAndReturnValueIfWormWasEmpty() {
            final Worm<String> worm = new Worm<>();

            assertEquals("Bob", worm.getOrSet("Bob"));
        }

        @Test
        void shouldAbleToSetValueToNull() {
            final Worm<String> worm = new Worm<>();

            assertNull(worm.getOrSet(null));
        }

        @Test
        void shouldReturnExistingValueIfWormWasNotEmpty() {
            final Worm<String> worm = new Worm<>("Alice");

            assertEquals("Alice", worm.getOrSet("Bob"));
        }
    }

    @Nested
    class GetOrCompute {
        @Test
        void shouldComputeAndReturnValueIfWormWasEmpty() {
            final Worm<String> worm = new Worm<>();

            assertEquals("Bob", worm.getOrCompute(() -> "Bob"));
        }

        @Test
        void shouldAbleToComputeValueToNull() {
            final Worm<String> worm = new Worm<>();

            assertNull(worm.getOrCompute(() -> null));
        }

        @Test
        void shouldReturnExistingValueIfWormWasNotEmpty() {
            final Worm<String> worm = new Worm<>("Alice");

            assertEquals("Alice", worm.getOrCompute(() -> "Bob"));
        }
    }

    @Nested
    class IsPresent {
        @Test
        void shouldReturnTrueForNonEmptyWorm() {
            final Worm<String> worm = new Worm<>("Alice");

            assertTrue(worm.isPresent());
        }

        @Test
        void shouldReturnFalseForEmptyWorm() {
            final Worm<String> worm = new Worm<>();

            assertFalse(worm.isPresent());
        }
    }

    @Nested
    class IsEmpty {
        @Test
        void shouldReturnFalseForNonEmptyWorm() {
            final Worm<String> worm = new Worm<>("Alice");

            assertFalse(worm.isEmpty());
        }

        @Test
        void shouldReturnTrueForEmptyWorm() {
            final Worm<String> worm = new Worm<>();

            assertTrue(worm.isEmpty());
        }
    }

    @Nested
    class Equals {
        @Test
        void notSameBecauseOfText() {
            final Worm<String> worm1 = new Worm<>("foo");
            final Worm<String> worm2 = new Worm<>("foo");
            final Worm<String> worm3 = new Worm<>("bar");

            assertEquals(worm1, worm2);
            assertNotEquals(worm2, worm3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfText() {
            final Worm<String> worm1 = new Worm<>("foo");
            final Worm<String> worm2 = new Worm<>("foo");
            final Worm<String> worm3 = new Worm<>("bar");

            assertEquals(worm1.hashCode(), worm2.hashCode());
            assertNotEquals(worm2.hashCode(), worm3.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnString() {
            final Worm<String> worm = new Worm<>("Alice");

            assertThat(worm.toString(), containsString("Alice"));
        }
    }

    @Nested
    class Of {
        @Test
        void shouldCreateWormWithValue() {
            final Worm<String> worm = Worm.of("Frohe Weihnachten!");

            assertEquals(new Worm<>("Frohe Weihnachten!"), worm);
        }

        @Test
        void shouldCreateWormWithNoValue() {
            final Worm<String> worm = Worm.of();

            assertEquals(new Worm<>(), worm);
        }

        @Test
        void shouldDistinguishBetweenNullAndEmpty() {
            final Worm<String> worm = Worm.of(null);

            assertEquals(new Worm<>(null), worm);
        }
    }
}
