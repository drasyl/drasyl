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

import java.lang.reflect.Array;

/**
 * Utility class for operations on arrays.
 */
public final class ArrayUtil {
    private ArrayUtil() {
        // util class
    }

    /**
     * Returns an empty arrray. This noop method is required to prevent ambiguous calls.
     *
     * @param <E> array element type
     * @return empty array
     */
    public static <E> E[] concat() {
        return (E[]) new Object[0];
    }

    /**
     * Returns a new array containing all elements from given {@code arrays}.
     *
     * @param arrays arrays to concatenate
     * @param <E>    array element type
     * @return array containing all elements from given {@code arrays}
     */
    @SuppressWarnings("java:S3047")
    public static <E> E[] concat(final E[]... arrays) {
        if (arrays.length == 0) {
            return (E[]) new Object[0];
        }

        int length = 0;
        for (final E[] array : arrays) {
            length += array.length;
        }

        final E[] result = (E[]) Array.newInstance(arrays[0].getClass().getComponentType(), length);
        int destPos = 0;
        for (final E[] array : arrays) {
            System.arraycopy(array, 0, result, destPos, array.length);
            destPos += array.length;
        }
        return result;
    }

    /**
     * Returns a new array containing all elements from given {@code arrays}.
     *
     * @param arrays arrays to concatenate
     * @param <E>    array element type
     * @return array containing all elements from given {@code arrays}
     */
    @SuppressWarnings("java:S3047")
    public static byte[] concat(final byte[]... arrays) {
        if (arrays.length == 0) {
            return new byte[0];
        }

        int length = 0;
        for (final byte[] array : arrays) {
            length += array.length;
        }

        final byte[] result = new byte[length];
        int destPos = 0;
        for (final byte[] array : arrays) {
            System.arraycopy(array, 0, result, destPos, array.length);
            destPos += array.length;
        }
        return result;
    }
}
