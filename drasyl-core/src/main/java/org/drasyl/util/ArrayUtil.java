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

import com.google.common.primitives.Bytes;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Utility class for operations on arrays.
 */
public final class ArrayUtil {
    private ArrayUtil() {
        // util class
    }

    /**
     * Creates a new array containing all elements from {@code a} first and then from {@code b}.
     *
     * @param a   array a
     * @param b   array b
     * @param <E> the arrays element type
     * @return array containing all elements from {@code a} first and then from {@code b}
     * @throws NullPointerException if {@code a} or {@code b} is {@code null}
     */
    @SuppressWarnings("unchecked")
    public static <E> E[] concat(final E[] a, final E[] b) {
        return Stream.concat(Arrays.stream(a), Arrays.stream(b)).toArray(
                size -> (E[]) Array.newInstance(a.getClass().getComponentType(), size));
    }

    /**
     * Creates a new byte array containing all bytes from {@code a} first and then from {@code b}.
     *
     * @param a array a
     * @param b array b
     * @return byte array containing all bytes from {@code a} first and then from {@code b}
     * @throws NullPointerException if {@code a} or {@code b} is {@code null}
     */
    public static byte[] concat(final byte[] a, final byte[] b) {
        return Bytes.concat(a, b);
    }
}
