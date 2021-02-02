/*
 * Copyright (c) 2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.util;

import com.google.common.primitives.Bytes;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Utility class for operations on arrays.
 */
public class ArrayUtil {
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
