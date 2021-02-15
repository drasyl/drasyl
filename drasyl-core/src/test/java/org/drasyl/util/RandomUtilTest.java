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
    @Nested
    class RandomInt {
        @Test
        void shouldReturnRandomNumberBetweenMinAndMax() {
            for (int i = 0; i < 100; i++) {
                final int result = RandomUtil.randomInt(5, 10);

                assertThat(result, is(both(greaterThanOrEqualTo(5)).and(lessThanOrEqualTo(10))));
            }
        }

        @Test
        void shouldReturnRandomNumberBetweenZeroAndMax() {
            for (int i = 0; i < 100; i++) {
                final int result = RandomUtil.randomInt(5);

                assertThat(result, is(both(greaterThanOrEqualTo(0)).and(lessThanOrEqualTo(5))));
            }
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
}
