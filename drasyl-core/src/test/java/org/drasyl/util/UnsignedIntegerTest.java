/*
 * Copyright (c) 2021.
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class UnsignedIntegerTest {
    @Nested
    class ToString {
        @Test
        void shouldReturnUnsignedInteger() {
            assertEquals("1", UnsignedInteger.of(1).toString());
        }
    }

    @Nested
    class Validation {
        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Test
        void shouldThrowExceptionOnInvalidValue() {
            assertThrows(IllegalArgumentException.class, () -> UnsignedInteger.of(-1));
            assertThrows(IllegalArgumentException.class, UnsignedInteger.MAX_VALUE::increment);
        }

        @Test
        void shouldThrowExceptionOnInvalidByteValue() {
            assertThrows(IllegalArgumentException.class, () -> UnsignedInteger.of(new byte[0]));
            assertThrows(IllegalArgumentException.class, () -> UnsignedInteger.of(new byte[1]));
            assertThrows(IllegalArgumentException.class, () -> UnsignedInteger.of(new byte[3]));
            assertThrows(IllegalArgumentException.class, () -> UnsignedInteger.of(new byte[8]));
        }
    }

    @Nested
    class Creation {
        @Test
        void shouldCreateCorrectInteger() {
            assertEquals(0, UnsignedInteger.of(0).getValue());
            assertEquals(0, UnsignedInteger.MIN_VALUE.getValue());
            assertEquals(0, UnsignedInteger.of(new byte[4]).getValue());

            assertEquals(0xFFFFFFFFL, UnsignedInteger.of(0xFFFFFFFFL).getValue());
            assertEquals(0xFFFFFFFFL, UnsignedInteger.of(new byte[]{
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0xFF
            }).getValue());
            assertEquals(UnsignedInteger.MAX_VALUE, UnsignedInteger.of(0xFFFFFFFFL));
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEqual() {
            final byte[] bytes = new byte[]{ 0x0, 0x0, (byte) 0xFF, (byte) 0xFF };
            final UnsignedInteger uint1 = UnsignedInteger.of(bytes);
            final UnsignedInteger uint2 = UnsignedInteger.of(65_535);

            assertEquals(uint1, uint2);
            assertEquals(uint1.getValue(), uint2.getValue());
            assertArrayEquals(uint1.toBytes(), uint2.toBytes());
        }

        @Test
        void shouldNotBeEqual() {
            final byte[] bytes = new byte[]{ 0x0, 0x0, (byte) 0xFF, (byte) 0xFF };
            final UnsignedInteger uint1 = UnsignedInteger.of(bytes);
            final UnsignedInteger uint2 = UnsignedInteger.of(0);

            assertNotEquals(uint1, uint2);
            assertNotEquals(uint1.getValue(), uint2.getValue());
            assertNotEquals(uint1.toBytes(), uint2.toBytes());
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEqual() {
            final byte[] bytes = new byte[]{ 0x0, 0x0, (byte) 0xFF, (byte) 0xFF };
            final UnsignedInteger uint1 = UnsignedInteger.of(bytes);
            final UnsignedInteger uint2 = UnsignedInteger.of(65_535);

            assertEquals(uint1.hashCode(), uint2.hashCode());
        }

        @Test
        void shouldNotBeEqual() {
            final byte[] bytes = new byte[]{ 0x0, 0x0, (byte) 0xFF, (byte) 0xFF };
            final UnsignedInteger uint1 = UnsignedInteger.of(bytes);
            final UnsignedInteger uint2 = UnsignedInteger.of(0);

            assertNotEquals(uint1.hashCode(), uint2.hashCode());
        }
    }

    @Nested
    class Increment {
        @Test
        void shouldIncrementShort() {
            assertEquals(UnsignedInteger.of(3), UnsignedInteger.of(2).increment());
        }
    }

    @Nested
    class Decrement {
        @Test
        void shouldDecrementShort() {
            assertEquals(UnsignedInteger.of(3), UnsignedInteger.of(4).decrement());
        }
    }
}