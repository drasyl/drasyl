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

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class ConcurrentReferenceTest {
    @Nested
    class Get {
        @Test
        void shouldReturnValue() {
            final ConcurrentReference<String> concurrentReference = ConcurrentReference.of("Test");

            assertEquals("Test", concurrentReference.getValue().get());
        }

        @Test
        void shouldReturnEmptyOptionalIfValueIsNotPresent() {
            final ConcurrentReference<String> concurrentReference = ConcurrentReference.of();

            assertEquals(Optional.empty(), concurrentReference.getValue());
        }

        @Test
        void shouldAllowNullValue() {
            final ConcurrentReference<String> concurrentReference = ConcurrentReference.of(null);

            assertEquals(Optional.empty(), concurrentReference.getValue());
        }
    }

    @Nested
    class SetAndGet {
        @Test
        void shouldSetOnEmptyValue() {
            final ConcurrentReference<String> concurrentReference = ConcurrentReference.of();

            concurrentReference.computeIfAbsent(() -> "Hello World!");

            assertEquals("Hello World!", concurrentReference.getValue().get());
        }

        @Test
        void shouldNotSetOnNonEmptyValue() {
            final ConcurrentReference<String> concurrentReference = ConcurrentReference.of("Test");

            assertEquals("Test", concurrentReference.getValue().get());

            concurrentReference.computeIfAbsent(() -> "Hello World!");

            assertEquals("Test", concurrentReference.getValue().get());
        }

        @Test
        void shouldSetOnCondition() {
            final ConcurrentReference<String> concurrentReference = ConcurrentReference.of("Test");

            assertEquals("Test", concurrentReference.getValue().get());

            concurrentReference.computeOnCondition(v -> v.equals("Test"), t -> t + "2");

            assertEquals("Test2", concurrentReference.getValue().get());
        }

        @Test
        void shouldNotSetOnNotFulfilledCondition() {
            final ConcurrentReference<String> concurrentReference = ConcurrentReference.of("Test");

            assertEquals("Test", concurrentReference.getValue().get());

            concurrentReference.computeOnCondition(v -> v.equals("Foo"), t -> "Bar");

            assertEquals("Test", concurrentReference.getValue().get());
        }
    }

    @Nested
    class Equals {
        @Test
        void notSameBecauseOfText() {
            final ConcurrentReference<String> concurrentReference1 = ConcurrentReference.of("foo");
            final ConcurrentReference<String> concurrentReference2 = ConcurrentReference.of("foo");
            final ConcurrentReference<String> concurrentReference3 = ConcurrentReference.of("bar");

            assertEquals(concurrentReference1, concurrentReference2);
            assertNotEquals(concurrentReference2, concurrentReference3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfText() {
            final ConcurrentReference<String> concurrentReference1 = ConcurrentReference.of("foo");
            final ConcurrentReference<String> concurrentReference2 = ConcurrentReference.of("foo");
            final ConcurrentReference<String> concurrentReference3 = ConcurrentReference.of("bar");

            assertEquals(concurrentReference1.hashCode(), concurrentReference2.hashCode());
            assertNotEquals(concurrentReference2.hashCode(), concurrentReference3.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnString() {
            final ConcurrentReference<String> concurrentReference = ConcurrentReference.of("Alice");

            assertThat(concurrentReference.toString(), containsString("Alice"));
        }
    }
}
