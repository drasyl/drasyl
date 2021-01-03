/*
 * Copyright (c) 2020.
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
class UnsignedShortTest {
    @Nested
    class ToString {
        @Test
        void shouldReturnUnsignedShort() {
            assertEquals("1", UnsignedShort.of(1).toString());
        }
    }

    @Nested
    class Validation {
        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Test
        void shouldThrowExceptionOnInvalidValue() {
            assertThrows(IllegalArgumentException.class, () -> UnsignedShort.of(-1));
            assertThrows(IllegalArgumentException.class, () -> UnsignedShort.of(65_536));
        }

        @Test
        void shouldThrowExceptionOnInvalidByteValue() {
            assertThrows(IllegalArgumentException.class, () -> UnsignedShort.of(new byte[]{}));
            assertThrows(IllegalArgumentException.class, () -> UnsignedShort.of(new byte[]{ 0x0 }));
            assertThrows(IllegalArgumentException.class, () -> UnsignedShort.of(new byte[]{
                    0x0,
                    0x0,
                    0x0
            }));
        }
    }

    @Nested
    class Creation {
        @Test
        void shouldCreateCorrectShort() {
            assertEquals(0, UnsignedShort.of(0).getValue());
            assertEquals(0, UnsignedShort.of(new byte[]{ 0x0, 0x0 }).getValue());

            assertEquals(65_535, UnsignedShort.of(65_535).getValue());
            assertEquals(65_535, UnsignedShort.of(new byte[]{
                    (byte) 0xFF,
                    (byte) 0xFF
            }).getValue());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEqual() {
            final byte[] bytes = new byte[]{ (byte) 0xFF, (byte) 0xFF };
            final UnsignedShort ushort1 = UnsignedShort.of(bytes);
            final UnsignedShort ushort2 = UnsignedShort.of(65_535);

            assertEquals(ushort1, ushort2);
            assertEquals(ushort1.getValue(), ushort2.getValue());
            assertArrayEquals(ushort1.toBytes(), ushort2.toBytes());
        }

        @Test
        void shouldNotBeEqual() {
            final byte[] bytes = new byte[]{ (byte) 0xFF, (byte) 0xFF };
            final UnsignedShort ushort1 = UnsignedShort.of(bytes);
            final UnsignedShort ushort2 = UnsignedShort.of(0);

            assertNotEquals(ushort1, ushort2);
            assertNotEquals(ushort1.getValue(), ushort2.getValue());
            assertNotEquals(ushort1.toBytes(), ushort2.toBytes());
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEqual() {
            final byte[] bytes = new byte[]{ (byte) 0xFF, (byte) 0xFF };
            final UnsignedShort ushort1 = UnsignedShort.of(bytes);
            final UnsignedShort ushort2 = UnsignedShort.of(65_535);

            assertEquals(ushort1.hashCode(), ushort2.hashCode());
        }

        @Test
        void shouldNotBeEqual() {
            final byte[] bytes = new byte[]{ (byte) 0xFF, (byte) 0xFF };
            final UnsignedShort ushort1 = UnsignedShort.of(bytes);
            final UnsignedShort ushort2 = UnsignedShort.of(0);

            assertNotEquals(ushort1.hashCode(), ushort2.hashCode());
        }
    }

    @Nested
    class Increment {
        @Test
        void shouldIncrementShort() {
            assertEquals(UnsignedShort.of(3), UnsignedShort.of(2).increment());
        }
    }

    @Nested
    class Decrement {
        @Test
        void shouldDecrementShort() {
            assertEquals(UnsignedShort.of(3), UnsignedShort.of(4).decrement());
        }
    }
}