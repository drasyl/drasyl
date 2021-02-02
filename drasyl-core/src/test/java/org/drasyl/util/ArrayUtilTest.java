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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ArrayUtilTest {
    @Nested
    class Concat {
        @Test
        void shouldConcatTwoStringArrays() {
            final String[] a = { "Dog", "Cat" };
            final String[] b = { "Bird", "Cow" };

            final String[] result = ArrayUtil.concat(a, b);

            assertArrayEquals(new String[]{ "Dog", "Cat", "Bird", "Cow" }, result);
        }

        @Test
        void shouldConcatTwoByteArrays() {
            final byte[] a = { 0, 1 };
            final byte[] b = { 2, 3 };

            final byte[] result = ArrayUtil.concat(a, b);

            assertArrayEquals(new byte[]{ 0, 1, 2, 3 }, result);
        }
    }
}
