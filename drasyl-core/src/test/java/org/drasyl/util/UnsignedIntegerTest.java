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
