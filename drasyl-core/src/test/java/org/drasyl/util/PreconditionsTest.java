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

import java.time.Duration;

import static java.time.Duration.ofMillis;
import static org.drasyl.util.Preconditions.requireInRange;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.Preconditions.requirePositive;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class PreconditionsTest {
    public static final Duration NEGATIVE_DURATION = ofMillis(-1);

    @Nested
    class RequireNonNegative {
        @Test
        void shouldRejectNegativeNumbers() {
            // byte
            assertThrows(IllegalArgumentException.class, () -> requireNonNegative((byte) -1));
            assertThrows(IllegalArgumentException.class, () -> requireNonNegative((byte) -1, "invalid"));

            // int
            assertThrows(IllegalArgumentException.class, () -> requireNonNegative(-1));
            assertThrows(IllegalArgumentException.class, () -> requireNonNegative(-1, "invalid"));

            // long
            assertThrows(IllegalArgumentException.class, () -> requireNonNegative(-1L));
            assertThrows(IllegalArgumentException.class, () -> requireNonNegative(-1L, "invalid"));

            // short
            assertThrows(IllegalArgumentException.class, () -> requireNonNegative((short) -1));
            assertThrows(IllegalArgumentException.class, () -> requireNonNegative((short) -1, "invalid"));

            // float
            assertThrows(IllegalArgumentException.class, () -> requireNonNegative(-1f));
            assertThrows(IllegalArgumentException.class, () -> requireNonNegative(-1f, "invalid"));

            // double
            assertThrows(IllegalArgumentException.class, () -> requireNonNegative(-1d));
            assertThrows(IllegalArgumentException.class, () -> requireNonNegative(-1d, "invalid"));

            // Duration
            assertThrows(IllegalArgumentException.class, () -> requireNonNegative(NEGATIVE_DURATION));
            assertThrows(IllegalArgumentException.class, () -> requireNonNegative(NEGATIVE_DURATION, "invalid"));
        }

        @Test
        void shouldAcceptNonNegativeNumbers() {
            // byte
            assertEquals((byte) 0, requireNonNegative((byte) 0));
            assertEquals((byte) 0, requireNonNegative((byte) 0, "invalid"));
            assertEquals((byte) 1, requireNonNegative((byte) 1));
            assertEquals((byte) 1, requireNonNegative((byte) 1, "invalid"));

            // int
            assertEquals(0, requireNonNegative(0));
            assertEquals(0, requireNonNegative(0, "invalid"));
            assertEquals(1, requireNonNegative(1));
            assertEquals(1, requireNonNegative(1, "invalid"));

            // long
            assertEquals(0L, requireNonNegative(0L));
            assertEquals(0L, requireNonNegative(0L, "invalid"));
            assertEquals(1L, requireNonNegative(1L));
            assertEquals(1L, requireNonNegative(1L, "invalid"));

            // short
            assertEquals((short) 0, requireNonNegative((short) 0));
            assertEquals((short) 0, requireNonNegative((short) 0, "invalid"));
            assertEquals((short) 1, requireNonNegative((short) 1));
            assertEquals((short) 1, requireNonNegative((short) 1, "invalid"));

            // float
            assertEquals((short) 0, requireNonNegative(0f));
            assertEquals((short) 0, requireNonNegative(0f, "invalid"));
            assertEquals((short) 1, requireNonNegative(1f));
            assertEquals((short) 1, requireNonNegative(1f, "invalid"));

            // double
            assertEquals((short) 0, requireNonNegative(0d));
            assertEquals((short) 0, requireNonNegative(0d, "invalid"));
            assertEquals((short) 1, requireNonNegative(1d));
            assertEquals((short) 1, requireNonNegative(1d, "invalid"));
        }
    }

    @Nested
    class RequirePositive {
        @SuppressWarnings("java:S5961")
        @Test
        void shouldRejectNonPositiveNumbers() {
            // byte
            assertThrows(IllegalArgumentException.class, () -> requirePositive((byte) 0));
            assertThrows(IllegalArgumentException.class, () -> requirePositive((byte) 0, "invalid"));
            assertThrows(IllegalArgumentException.class, () -> requirePositive((byte) -1));
            assertThrows(IllegalArgumentException.class, () -> requirePositive((byte) -1, "invalid"));

            // int
            assertThrows(IllegalArgumentException.class, () -> requirePositive(0));
            assertThrows(IllegalArgumentException.class, () -> requirePositive(0, "invalid"));
            assertThrows(IllegalArgumentException.class, () -> requirePositive(-1));
            assertThrows(IllegalArgumentException.class, () -> requirePositive(-1, "invalid"));

            // long
            assertThrows(IllegalArgumentException.class, () -> requirePositive(0L));
            assertThrows(IllegalArgumentException.class, () -> requirePositive(0L, "invalid"));
            assertThrows(IllegalArgumentException.class, () -> requirePositive(-1L));
            assertThrows(IllegalArgumentException.class, () -> requirePositive(-1L, "invalid"));

            // short
            assertThrows(IllegalArgumentException.class, () -> requirePositive((short) 0));
            assertThrows(IllegalArgumentException.class, () -> requirePositive((short) 0, "invalid"));
            assertThrows(IllegalArgumentException.class, () -> requirePositive((short) -1));
            assertThrows(IllegalArgumentException.class, () -> requirePositive((short) -1, "invalid"));

            // float
            assertThrows(IllegalArgumentException.class, () -> requirePositive(0f));
            assertThrows(IllegalArgumentException.class, () -> requirePositive(0f, "invalid"));
            assertThrows(IllegalArgumentException.class, () -> requirePositive(-1f));
            assertThrows(IllegalArgumentException.class, () -> requirePositive(-1f, "invalid"));

            // double
            assertThrows(IllegalArgumentException.class, () -> requirePositive(0d));
            assertThrows(IllegalArgumentException.class, () -> requirePositive(0d, "invalid"));
            assertThrows(IllegalArgumentException.class, () -> requirePositive(-1d));
            assertThrows(IllegalArgumentException.class, () -> requirePositive(-1d, "invalid"));

            // Duration
            assertThrows(IllegalArgumentException.class, () -> requirePositive(Duration.ZERO));
            assertThrows(IllegalArgumentException.class, () -> requirePositive(Duration.ZERO, "invalid"));
            assertThrows(IllegalArgumentException.class, () -> requirePositive(NEGATIVE_DURATION));
            assertThrows(IllegalArgumentException.class, () -> requirePositive(NEGATIVE_DURATION, "invalid"));
        }

        @Test
        void shouldAcceptPositiveNumbers() {
            // byte
            assertEquals(1, requirePositive((byte) 1));
            assertEquals(1, requirePositive((byte) 1, "invalid"));

            // int
            assertEquals(1, requirePositive(1));
            assertEquals(1, requirePositive(1, "invalid"));

            // long
            assertEquals(1L, requirePositive(1L));
            assertEquals(1L, requirePositive(1L, "invalid"));

            // short
            assertEquals((short) 1, requirePositive((short) 1));
            assertEquals((short) 1, requirePositive((short) 1, "invalid"));

            // float
            assertEquals((short) 1, requirePositive(1f));
            assertEquals((short) 1, requirePositive(1f, "invalid"));

            // double
            assertEquals((short) 1, requirePositive(1d));
            assertEquals((short) 1, requirePositive(1d, "invalid"));
        }
    }

    @Nested
    class RequireInRange {
        @Test
        void shouldRejectNonInRangeNumbers() {
            // byte
            assertThrows(IllegalArgumentException.class, () -> requireInRange((byte) 0, (byte) 1, (byte) 2));
            assertThrows(IllegalArgumentException.class, () -> requireInRange((byte) 0, (byte) 1, (byte) 2, "invalid"));
            assertThrows(IllegalArgumentException.class, () -> requireInRange((byte) -1, (byte) 1, (byte) 2));
            assertThrows(IllegalArgumentException.class, () -> requireInRange((byte) -1, (byte) 1, (byte) 2, "invalid"));

            // int
            assertThrows(IllegalArgumentException.class, () -> requireInRange(0, 1, 2));
            assertThrows(IllegalArgumentException.class, () -> requireInRange(0, 1, 2, "invalid"));
            assertThrows(IllegalArgumentException.class, () -> requireInRange(-1, 1, 2));
            assertThrows(IllegalArgumentException.class, () -> requireInRange(-1, 1, 2, "invalid"));

            // long
            assertThrows(IllegalArgumentException.class, () -> requireInRange(0L, 1L, 2L));
            assertThrows(IllegalArgumentException.class, () -> requireInRange(0L, 1L, 2L, "invalid"));
            assertThrows(IllegalArgumentException.class, () -> requireInRange(-1L, 1L, 2L));
            assertThrows(IllegalArgumentException.class, () -> requireInRange(-1L, 1L, 2L, "invalid"));

            // short
            assertThrows(IllegalArgumentException.class, () -> requireInRange((short) 0, (short) 1, (short) 2));
            assertThrows(IllegalArgumentException.class, () -> requireInRange((short) 0, (short) 1, (short) 2, "invalid"));
            assertThrows(IllegalArgumentException.class, () -> requireInRange((short) -1, (short) 1, (short) 2));
            assertThrows(IllegalArgumentException.class, () -> requireInRange((short) -1, (short) 1, (short) 2, "invalid"));

            // float
            assertThrows(IllegalArgumentException.class, () -> requireInRange(0f, 1f, 2f));
            assertThrows(IllegalArgumentException.class, () -> requireInRange(0f, 1f, 2f, "invalid"));
            assertThrows(IllegalArgumentException.class, () -> requireInRange(-1f, 1f, 2f));
            assertThrows(IllegalArgumentException.class, () -> requireInRange(-1f, 1f, 2f, "invalid"));

            // double
            assertThrows(IllegalArgumentException.class, () -> requireInRange(0d, 1d, 2d));
            assertThrows(IllegalArgumentException.class, () -> requireInRange(0d, 1d, 2d, "invalid"));
            assertThrows(IllegalArgumentException.class, () -> requireInRange(-1d, 1d, 2d));
            assertThrows(IllegalArgumentException.class, () -> requireInRange(-1d, 1d, 2d, "invalid"));
        }

        @Test
        void shouldAcceptInRangeNumbers() {
            // byte
            assertEquals(1, requireInRange((byte) 1, (byte) 1, (byte) 2));
            assertEquals(1, requireInRange((byte) 1, (byte) 1, (byte) 2, "invalid"));

            // int
            assertEquals(1, requireInRange(1, 1, 2));
            assertEquals(1, requireInRange(1, 1, 2, "invalid"));

            // long
            assertEquals(1L, requireInRange(1L, 1L, 2L));
            assertEquals(1L, requireInRange(1L, 1L, 2L, "invalid"));

            // short
            assertEquals((short) 1, requireInRange((short) 1, (short) 1, (short) 2));
            assertEquals((short) 1, requireInRange((short) 1, (short) 1, (short) 2, "invalid"));

            // float
            assertEquals(1L, requireInRange(1f, 1f, 2f));
            assertEquals(1L, requireInRange(1f, 1f, 2f, "invalid"));

            // double
            assertEquals(1L, requireInRange(1d, 1d, 2d));
            assertEquals(1L, requireInRange(1d, 1d, 2d, "invalid"));
        }
    }
}
