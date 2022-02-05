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

import static org.drasyl.util.UnsignedMediumInteger.MAX_VALUE;
import static org.drasyl.util.UnsignedMediumInteger.MIN_VALUE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class UnsignedMediumIntegerTest {
    @Nested
    class ToString {
        @Test
        void shouldReturnUnsignedInteger() {
            assertEquals("1", UnsignedMediumInteger.of(1).toString());
        }
    }

    @Nested
    class Validation {
        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Test
        void shouldThrowExceptionOnInvalidValue() {
            assertThrows(IllegalArgumentException.class, () -> UnsignedMediumInteger.of(-1));
            assertThrows(IllegalArgumentException.class, MAX_VALUE::increment);
        }

        @Test
        void shouldThrowExceptionOnInvalidByteValue() {
            assertThrows(IllegalArgumentException.class, () -> UnsignedMediumInteger.of(new byte[0]));
            assertThrows(IllegalArgumentException.class, () -> UnsignedMediumInteger.of(new byte[1]));
            assertThrows(IllegalArgumentException.class, () -> UnsignedMediumInteger.of(new byte[4]));
            assertThrows(IllegalArgumentException.class, () -> UnsignedMediumInteger.of(new byte[8]));
        }
    }

    @Nested
    class Creation {
        @Test
        void shouldCreateCorrectInteger() {
            assertEquals(0, UnsignedMediumInteger.of(0).getValue());
            assertEquals(0, UnsignedMediumInteger.MIN_VALUE.getValue());
            assertEquals(0, UnsignedMediumInteger.of(new byte[3]).getValue());

            assertEquals(0x00FFFFFF, UnsignedMediumInteger.of(0x00FFFFFF).getValue());
            assertEquals(0x00FFFFFF, UnsignedMediumInteger.of(new byte[]{
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0xFF
            }).getValue());
            assertEquals(MAX_VALUE, UnsignedMediumInteger.of(0x00FFFFFF));
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEqual() {
            final byte[] bytes = new byte[]{ 0x0, (byte) 0xFF, (byte) 0xFF };
            final UnsignedMediumInteger uint1 = UnsignedMediumInteger.of(bytes);
            final UnsignedMediumInteger uint2 = UnsignedMediumInteger.of(65_535);

            assertEquals(uint1, uint2);
            assertEquals(uint1.getValue(), uint2.getValue());
            assertArrayEquals(uint1.toBytes(), uint2.toBytes());
        }

        @Test
        void shouldNotBeEqual() {
            final byte[] bytes = new byte[]{ 0x0, (byte) 0xFF, (byte) 0xFF };
            final UnsignedMediumInteger uint1 = UnsignedMediumInteger.of(bytes);
            final UnsignedMediumInteger uint2 = UnsignedMediumInteger.of(0);

            assertNotEquals(uint1, uint2);
            assertNotEquals(uint1.getValue(), uint2.getValue());
            assertNotEquals(uint1.toBytes(), uint2.toBytes());
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEqual() {
            final byte[] bytes = new byte[]{ 0x0, (byte) 0xFF, (byte) 0xFF };
            final UnsignedMediumInteger uint1 = UnsignedMediumInteger.of(bytes);
            final UnsignedMediumInteger uint2 = UnsignedMediumInteger.of(65_535);

            assertEquals(uint1.hashCode(), uint2.hashCode());
        }

        @Test
        void shouldNotBeEqual() {
            final byte[] bytes = new byte[]{ 0x0, (byte) 0xFF, (byte) 0xFF };
            final UnsignedMediumInteger uint1 = UnsignedMediumInteger.of(bytes);
            final UnsignedMediumInteger uint2 = UnsignedMediumInteger.of(0);

            assertNotEquals(uint1.hashCode(), uint2.hashCode());
        }
    }

    @Nested
    class Increment {
        @Test
        void shouldIncrementShort() {
            assertEquals(UnsignedMediumInteger.of(3), UnsignedMediumInteger.of(2).increment());
        }

        @Test
        void shouldIncrementSafe() {
            assertEquals(MIN_VALUE, MAX_VALUE.safeIncrement());
            assertEquals(UnsignedMediumInteger.of(3), UnsignedMediumInteger.of(2).increment());
        }
    }

    @Nested
    class Decrement {
        @Test
        void shouldDecrementShort() {
            assertEquals(UnsignedMediumInteger.of(3), UnsignedMediumInteger.of(4).decrement());
        }

        @Test
        void shouldDecrementSafe() {
            assertEquals(MIN_VALUE, MIN_VALUE.safeDecrement());
            assertEquals(UnsignedMediumInteger.of(3), UnsignedMediumInteger.of(4).decrement());
        }
    }
}
