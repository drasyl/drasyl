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
 * This class represents an unsigned integer in a rang of [0, 2^32 - 1]
 */
public final class UnsignedInteger {
    public static final UnsignedInteger MIN_VALUE = UnsignedInteger.of(new byte[4]);
    public static final UnsignedInteger MAX_VALUE = UnsignedInteger.of(new byte[]{
            (byte) 0xFF,
            (byte) 0xFF,
            (byte) 0xFF,
            (byte) 0xFF
    });
    private static final int INTEGER_LENGTH = 4;
    private final long value;

    private UnsignedInteger(final long value) {
        if (value < MIN_VALUE.value || value > MAX_VALUE.value) {
            throw new IllegalArgumentException("Value must be in range of [0, 2^32 - 1], but was " + value);
        }

        this.value = value;
    }

    @SuppressWarnings("java:S109")
    private UnsignedInteger(final byte[] value) {
        if (value.length != INTEGER_LENGTH) {
            throw new IllegalArgumentException("Value must be a byte array of length 4, but was of length " + value.length);
        }

        this.value = ByteBuffer.wrap(new byte[]{
                0x0,
                0x0,
                0x0,
                0x0,
                value[0],
                value[1],
                value[2],
                value[3]
        }).getLong();
    }

    /**
     * Creates a new {@link UnsignedInteger}.
     *
     * @param value the value as long
     * @return an unaligned int
     * @throws IllegalArgumentException if the value is not in range of [0, 2^32 - 1].
     */
    public static UnsignedInteger of(final long value) {
        return new UnsignedInteger(value);
    }

    /**
     * Creates a new {@link UnsignedInteger}.
     *
     * @param value the value as byte array in big-endian (BE) format
     * @return an unaligned int
     * @throws IllegalArgumentException if the value is not in range of [0, 2^32 - 1].
     */
    public static UnsignedInteger of(final byte[] value) {
        return new UnsignedInteger(value);
    }

    public UnsignedInteger increment() {
        return UnsignedInteger.of(value + 1L);
    }

    public UnsignedInteger decrement() {
        return UnsignedInteger.of(value - 1L);
    }

    /**
     * @return a byte array of length 4.
     */
    public byte[] toBytes() {
        return toByteArray(value);
    }

    /**
     * @return the value as long
     */
    public long getValue() {
        return value;
    }

    @SuppressWarnings("java:S109")
    private static byte[] toByteArray(final long value) {
        return new byte[]{
                (byte) (value >> 32),
                (byte) (value >> 16),
                (byte) (value >> 8),
                (byte) value
        };
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UnsignedInteger that = (UnsignedInteger) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "" + value;
    }
}
