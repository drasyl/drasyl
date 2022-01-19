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

/**
 * Utility class for operations on bytes.
 */
public final class ByteUtil {
    private ByteUtil() {
        // util class
    }

    /**
     * Returns the number of leading zero bits (highest-order / "leftmost") of the specified {@code
     * byte} value.
     *
     * @param b the value whose number of leading zeros is to be computed
     * @return the number of leading zero bits of the specified {@code byte} value
     */
    @SuppressWarnings("java:S109")
    public static int numberOfLeadingZeros(byte b) {
        if (b <= 0) {
            return b == 0 ? 8 : 0;
        }
        int n = 7;
        if (b >= 1 << 4) {
            n -= 4;
            b >>>= 4;
        }
        if (b >= 1 << 2) {
            n -= 2;
            b >>>= 2;
        }
        return n - (b >>> 1);
    }

    /**
     * Returns the number of trailing zero bits (lowed-order / "rightmost") of the specified {@code
     * byte} value.
     *
     * @param b the value whose number of trailing zeros is to be computed
     * @return the number of trailing zero bits of the specified {@code byte} value
     */
    @SuppressWarnings("java:S109")
    public static int numberOfTrailingZeros(byte b) {
        b = (byte) (~b & (b - 1));
        if (b <= 0) {
            return b & 8;
        }
        int n = 1;
        if (b > 1 << 4) {
            n += 4;
            b >>>= 4;
        }
        if (b > 1 << 2) {
            n += 2;
            b >>>= 2;
        }
        return n + (b >>> 1);
    }
}
