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

import java.io.IOException;

import static org.drasyl.util.ByteUtil.numberOfLeadingZeros;
import static org.drasyl.util.ByteUtil.numberOfTrailingZeros;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ByteUtilTest {
    @Nested
    class NumberOfLeadingZeros {
        @Test
        void shouldReturnNumberOfLeadingZeros() {
            assertEquals(8, numberOfLeadingZeros((byte) 0));
            assertEquals(7, numberOfLeadingZeros((byte) 1));
            assertEquals(6, numberOfLeadingZeros((byte) 2));
            assertEquals(5, numberOfLeadingZeros((byte) 4));
            assertEquals(4, numberOfLeadingZeros((byte) 8));
            assertEquals(3, numberOfLeadingZeros((byte) 16));
            assertEquals(2, numberOfLeadingZeros((byte) 32));
            assertEquals(1, numberOfLeadingZeros((byte) 64));
            assertEquals(1, numberOfLeadingZeros((byte) 127));
            assertEquals(0, numberOfLeadingZeros((byte) 128));
            assertEquals(0, numberOfLeadingZeros((byte) 255));
            assertEquals(0, numberOfLeadingZeros((byte) -1));
        }
    }

    @Nested
    class NumberOfTrailingZeros {
        @Test
        void shouldReturnNumberOfTrailingZeros() throws IOException {
            assertEquals(8, numberOfTrailingZeros((byte) 0));
            assertEquals(0, numberOfTrailingZeros((byte) 1));
            assertEquals(1, numberOfTrailingZeros((byte) 2));
            assertEquals(2, numberOfTrailingZeros((byte) 4));
            assertEquals(3, numberOfTrailingZeros((byte) 8));
            assertEquals(4, numberOfTrailingZeros((byte) 16));
            assertEquals(5, numberOfTrailingZeros((byte) 32));
            assertEquals(6, numberOfTrailingZeros((byte) 64));
            assertEquals(0, numberOfTrailingZeros((byte) 127));
            assertEquals(7, numberOfTrailingZeros((byte) 128));
            assertEquals(0, numberOfTrailingZeros((byte) 255));
            assertEquals(0, numberOfTrailingZeros((byte) -1));
        }
    }
}
