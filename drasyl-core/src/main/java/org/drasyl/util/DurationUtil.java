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

import java.time.Duration;

/**
 * Utility class for operations on {@link Duration}s.
 */
public final class DurationUtil {
    private DurationUtil() {
        // util class
    }

    /**
     * Returns the greater of two {@link Duration} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the larger of {@code a} and {@code b}.
     * @throws NullPointerException if one or both values are {@code null}
     */
    public static Duration max(final Duration a, final Duration b) {
        if (a.compareTo(b) > 0) {
            return a;
        }
        else {
            return b;
        }
    }

    /**
     * Returns the smaller of two {@link Duration} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the smaller of {@code a} and {@code b}.
     * @throws NullPointerException if one or both values are {@code null}
     */
    public static Duration min(final Duration a, final Duration b) {
        if (a.compareTo(b) < 0) {
            return a;
        }
        else {
            return b;
        }
    }
}
