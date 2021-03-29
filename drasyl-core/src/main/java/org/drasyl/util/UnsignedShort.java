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

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * This class represents an unsigned short in a rang of [0, 2^16 - 1]
 */
public final class UnsignedShort {
    public static final UnsignedShort MIN_VALUE = UnsignedShort.of(new byte[2]);
    public static final UnsignedShort MAX_VALUE = UnsignedShort.of(new byte[]{
            (byte) 0xFF,
            (byte) 0xFF
    });
    private static final int SHORT_LENGTH = 2;
    private final int value;

    private UnsignedShort(final int value) {
        if (value < MIN_VALUE.value || value > MAX_VALUE.value) {
            throw new IllegalArgumentException("Value must be in range of [0, 2^16 - 1], but was " + value);
        }

        this.value = value;
    }

    private UnsignedShort(final byte[] value) {
        if (value.length != SHORT_LENGTH) {
            throw new IllegalArgumentException("Value must be a byte array of length 2, but was of length " + value.length);
        }

        this.value = ByteBuffer.wrap(new byte[]{ 0x0, 0x0, value[0], value[1] }).getInt();
    }

    /**
     * Creates a new {@link UnsignedShort}.
     *
     * @param value the value as integer
     * @return an unaligned short
     * @throws IllegalArgumentException if the value is not in range of [0, 2^16 - 1].
     */
    public static UnsignedShort of(final int value) {
        return new UnsignedShort(value);
    }

    /**
     * Creates a new {@link UnsignedShort}.
     *
     * @param value the value as byte array in big-endian (BE) format
     * @return an unaligned short
     * @throws IllegalArgumentException if the value is not in range of [0, 2^16 - 1].
     */
    public static UnsignedShort of(final byte[] value) {
        return new UnsignedShort(value);
    }

    /**
     * @return a byte array of length 2.
     */
    public byte[] toBytes() {
        return toByteArray(value);
    }

    /**
     * @return the value as integer.
     */
    public int getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "" + value;
    }

    public UnsignedShort increment() {
        return UnsignedShort.of(value + 1);
    }

    public UnsignedShort decrement() {
        return UnsignedShort.of(value - 1);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UnsignedShort that = (UnsignedShort) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @SuppressWarnings("java:S109")
    private static byte[] toByteArray(final int value) {
        return new byte[]{ (byte) (value >> 8), (byte) value };
    }
}
