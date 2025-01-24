/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.channel;

import static org.drasyl.util.Preconditions.requireNonNegative;

/**
 * ReadBufferWaterMark is used to set low water mark and high water mark for the read buffer.
 * <p>
 * If the number of bytes queued in the reads buffer exceeds the {@linkplain #high high water mark},
 * {@link DrasylChannel#isReadBufferFull()} will start to return {@code true}.
 * <p>
 * If the number of bytes queued in the read buffer exceeds the {@linkplain #high high water mark}
 * and then dropped down below the {@linkplain #low low water mark},
 * {@link DrasylChannel#isReadBufferFull()} will start to return {@code true} again.
 * <p>
 */
public final class ReadBufferWaterMark {
    public static final ReadBufferWaterMark DEFAULT = new ReadBufferWaterMark(32 * 1024, 64 * 1024);
    private final int low;
    private final int high;

    /**
     * Create a new instance.
     *
     * @param low  low water mark for read buffer.
     * @param high high water mark for read buffer
     */
    public ReadBufferWaterMark(int low, int high) {
        if (high < low) {
            throw new IllegalArgumentException("read buffer's high water mark cannot be less than " + " low water mark (" + low + "): " + high);
        }
        this.low = requireNonNegative(low);
        this.high = requireNonNegative(high);
    }

    /**
     * Returns the low water mark for the read buffer.
     */
    public int low() {
        return low;
    }

    /**
     * Returns the high water mark for the read buffer.
     */
    public int high() {
        return high;
    }

    @Override
    public String toString() {
        return "ReadBufferWaterMark(low: " + low + ", high: " + high + ")";
    }
}
