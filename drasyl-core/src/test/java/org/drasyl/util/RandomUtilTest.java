/*
 * Copyright (c) 2020-2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class RandomUtilTest {
    private static final int ITERATIONS = 1_000;

    @Nested
    class RandomInt {
        @Test
        void shouldReturnRandomNumberBetweenMinAndMax() {
            int minResult = 10;
            int maxResult = 5;
            for (int i = 0; i < ITERATIONS; i++) {
                final int result = RandomUtil.randomInt(5, 10);
                minResult = Math.min(minResult, result);
                maxResult = Math.max(maxResult, result);

                assertThat(result, is(both(greaterThanOrEqualTo(5)).and(lessThanOrEqualTo(10))));
            }

            assertEquals(5, minResult);
            assertEquals(10, maxResult);
        }

        @Test
        void shouldReturnRandomNumberBetweenZeroAndMax() {
            int minResult = 5;
            int maxResult = 0;
            for (int i = 0; i < ITERATIONS; i++) {
                final int result = RandomUtil.randomInt(5);
                minResult = Math.min(minResult, result);
                maxResult = Math.max(maxResult, result);

                assertThat(result, is(both(greaterThanOrEqualTo(0)).and(lessThanOrEqualTo(5))));
            }

            assertEquals(0, minResult);
            assertEquals(5, maxResult);
        }

        @Test
        void shouldReturnMinNumberIfMinAndMaxAreEqual() {
            assertEquals(10, RandomUtil.randomInt(10, 10));
        }

        @Test
        void shouldThrowExceptionIfMinIsGreaterThanMax() {
            assertThrows(IllegalArgumentException.class, () -> RandomUtil.randomInt(10, 1));
        }

        @Test
        void shouldThrowExceptionIfMaxIsNegative() {
            assertThrows(IllegalArgumentException.class, () -> RandomUtil.randomInt(-1));
        }
    }

    @Nested
    class RandomLong {
        @Test
        void shouldReturnRandomNumberBetweenMinAndMax() {
            long minResult = 10;
            long maxResult = 5;
            for (int i = 0; i < ITERATIONS; i++) {
                final long result = RandomUtil.randomLong(5, 10);
                minResult = Math.min(minResult, result);
                maxResult = Math.max(maxResult, result);

                assertThat(result, is(both(greaterThanOrEqualTo(5L)).and(lessThanOrEqualTo(10L))));
            }

            assertEquals(5, minResult);
            assertEquals(10, maxResult);
        }

        @Test
        void shouldReturnRandomNumberBetweenZeroAndMax() {
            long minResult = 5;
            long maxResult = 0;
            for (int i = 0; i < ITERATIONS; i++) {
                final long result = RandomUtil.randomLong(5);
                minResult = Math.min(minResult, result);
                maxResult = Math.max(maxResult, result);

                assertThat(result, is(both(greaterThanOrEqualTo(0L)).and(lessThanOrEqualTo(5L))));
            }

            assertEquals(0, minResult);
            assertEquals(5, maxResult);
        }

        @Test
        void shouldReturnMinNumberIfMinAndMaxAreEqual() {
            assertEquals(10, RandomUtil.randomLong(10, 10));
        }

        @Test
        void shouldThrowExceptionIfMinIsGreaterThanMax() {
            assertThrows(IllegalArgumentException.class, () -> RandomUtil.randomLong(10, 1));
        }

        @Test
        void shouldThrowExceptionIfMaxIsNegative() {
            assertThrows(IllegalArgumentException.class, () -> RandomUtil.randomLong(-1));
        }
    }

    @Nested
    class RandomBytes {
        @Test
        void shouldReturnArrayOfCorrectLength() {
            final byte[] bytes = RandomUtil.randomBytes(10);

            assertEquals(10, bytes.length);
        }

        @Test
        void shouldThrowExceptionIfCountIsNegative() {
            assertThrows(IllegalArgumentException.class, () -> RandomUtil.randomBytes(-1));
        }
    }

    @Nested
    class RandomString {
        @Test
        void shouldReturnStringOfCorrectLength() {
            final String string = RandomUtil.randomString(10);

            assertEquals(10, string.length());
        }

        @Test
        void shouldThrowExceptionIfLengthIsNegative() {
            assertThrows(IllegalArgumentException.class, () -> RandomUtil.randomString(-1));
        }
    }
}
