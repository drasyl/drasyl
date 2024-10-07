/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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

import org.drasyl.util.internal.UnstableApi;

import java.util.Arrays;

/**
 * Immutable byte array implementation, that do not allow any modifications to the original input.
 */
@UnstableApi
public final class ImmutableByteArray {
    private final byte[] array;

    private ImmutableByteArray(final byte[] array) {
        this.array = Arrays.copyOf(array, array.length);
    }

    /**
     * Copy the given {@code array} and stores it as immutable object.
     *
     * @param array the array to copy
     * @return an immutable byte array
     */
    public static ImmutableByteArray of(final byte[] array) {
        return new ImmutableByteArray(array);
    }

    public byte[] getArray() {
        return Arrays.copyOf(array, array.length);
    }

    public int size() {
        return array.length;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ImmutableByteArray that = (ImmutableByteArray) o;

        return Arrays.equals(array, that.array);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }

    @Override
    public String toString() {
        return "ImmutableByteArray{" +
                "array=" + Arrays.toString(array) +
                '}';
    }
}
