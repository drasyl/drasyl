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
 * This class represents an unsigned short in a rang of [0, 256).
 */
public final class UnsignedByte {
    public static final UnsignedByte MIN_VALUE = UnsignedByte.of((byte) 0x00);
    public static final UnsignedByte MAX_VALUE = UnsignedByte.of((byte) 0xFF);
    private final short value;

    private UnsignedByte(final short value) {
        if (value < MIN_VALUE.value || value > MAX_VALUE.value) {
            throw new IllegalArgumentException("Value must be in range of [0, 255), but was " + value);
        }

        this.value = value;
    }

    @SuppressWarnings("java:S109")
    private UnsignedByte(final byte value) {
        this.value = ByteBuffer.wrap(new byte[]{
                0x0,
                value
        }).getShort();
    }

    /**
     * @return the value as short.
     */
    public short getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UnsignedByte that = (UnsignedByte) o;
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

    /**
     * Creates a new {@link UnsignedByte}.
     *
     * @param value the value as integer
     * @return an unsigned byte
     * @throws IllegalArgumentException if the value is not in range of [0, 256).
     */
    public static UnsignedByte of(final short value) {
        return new UnsignedByte(value);
    }

    /**
     * Creates a new {@link UnsignedShort}.
     *
     * @param value the value as byte array in big-endian (BE) format
     * @return an unsigned byte
     * @throws IllegalArgumentException if the value is not in range of [0, 256).
     */
    public static UnsignedByte of(final byte value) {
        return new UnsignedByte(value);
    }
}
