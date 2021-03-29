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
            assertThrows(IllegalArgumentException.class, UnsignedShort.MAX_VALUE::increment);
        }

        @Test
        void shouldThrowExceptionOnInvalidByteValue() {
            assertThrows(IllegalArgumentException.class, () -> UnsignedShort.of(new byte[0]));
            assertThrows(IllegalArgumentException.class, () -> UnsignedShort.of(new byte[1]));
            assertThrows(IllegalArgumentException.class, () -> UnsignedShort.of(new byte[3]));
        }
    }

    @Nested
    class Creation {
        @Test
        void shouldCreateCorrectShort() {
            assertEquals(0, UnsignedShort.of(0).getValue());
            assertEquals(0, UnsignedShort.MIN_VALUE.getValue());
            assertEquals(0, UnsignedShort.of(new byte[2]).getValue());

            assertEquals(65_535, UnsignedShort.of(65_535).getValue());
            assertEquals(65_535, UnsignedShort.of(new byte[]{
                    (byte) 0xFF,
                    (byte) 0xFF
            }).getValue());
            assertEquals(UnsignedShort.MAX_VALUE, UnsignedShort.of(65_535));
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
