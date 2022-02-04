/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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

import static org.drasyl.util.Murmur3.murmur3_x86_32;
import static org.drasyl.util.Murmur3.murmur3_x86_32BytesLE;
import static org.drasyl.util.Murmur3.murmur3_x86_32LE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Murmur3Test {
    @Nested
    class Murmur3_x86_32 {
        @Test
        void shouldReturnCorrectHashes() {
            assertThrows(NullPointerException.class, () -> murmur3_x86_32(null));
            assertEquals(0, murmur3_x86_32(new byte[0]));
            assertEquals(-2127245832, murmur3_x86_32(new byte[16]));
            assertEquals(1043635621, murmur3_x86_32(new byte[]{ 1, 2, 3, 4 }));
            assertEquals(-61302859, murmur3_x86_32(new byte[]{ 1, 2, 3, 4, 5, 6, 7 }));
        }
    }

    @Nested
    class Murmur3_x86_32LE {
        @Test
        void shouldReturnCorrectHashes() {
            assertThrows(NullPointerException.class, () -> murmur3_x86_32LE(null));
            assertEquals(0, murmur3_x86_32LE(new byte[0]));
            assertEquals(-120769407, murmur3_x86_32LE(new byte[16]));
            assertEquals(-1516424130, murmur3_x86_32LE(new byte[]{ 1, 2, 3, 4 }));
            assertEquals(-1248372484, murmur3_x86_32LE(new byte[]{ 1, 2, 3, 4, 5, 6, 7 }));
        }
    }

    @Nested
    class Murmur3_x86_32BytesLE {
        @Test
        void shouldReturnCorrectHashes() {
            assertThrows(NullPointerException.class, () -> murmur3_x86_32BytesLE(null));
            assertArrayEquals(new byte[]{ 0, 0, 0, 0 }, murmur3_x86_32BytesLE(new byte[0]));
            assertArrayEquals(new byte[]{ -8, -51, 52, -127 }, murmur3_x86_32BytesLE(new byte[16]));
            assertArrayEquals(new byte[]{ -91, -99, 52, 62 }, murmur3_x86_32BytesLE(new byte[]{
                    1,
                    2,
                    3,
                    4
            }));
            assertArrayEquals(new byte[]{ -75, -105, 88, -4 }, murmur3_x86_32BytesLE(new byte[]{
                    1,
                    2,
                    3,
                    4,
                    5,
                    6,
                    7
            }));
        }
    }
}
