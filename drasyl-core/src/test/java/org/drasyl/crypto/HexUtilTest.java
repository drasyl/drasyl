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
package org.drasyl.crypto;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings({ "java:S1607" })
class HexUtilTest {
    private final byte[] byteArray = new byte[]{ 0x4f, 0x00, 0x10, 0x0d };
    private final String byteString = "4f00100d";

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectString() {
            assertEquals(HexUtil.toString(byteArray), byteString);
        }
    }

    @Nested
    class FromString {
        @Test
        void shouldReturnCorrectByteArray() {
            assertArrayEquals(byteArray, HexUtil.fromString(byteString));
            assertArrayEquals(byteArray, HexUtil.fromString(byteString.toLowerCase()));
        }

        @Test
        void shouldThrowExceptionOnNotConformStringRepresentations() {
            assertThrows(IllegalArgumentException.class, () -> HexUtil.fromString("A"));
            assertThrows(IllegalArgumentException.class, () -> HexUtil.fromString("0Z"));
        }
    }
}
