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
package org.drasyl.handler.remote.protocol;

import java.util.Objects;

/**
 * This is an immutable object.
 */
@SuppressWarnings("java:S2974")
public class HopCount implements Comparable<HopCount> {
    public static final byte MIN_HOP_COUNT = 0;
    public static final byte MAX_HOP_COUNT = 7;
    private final byte value;

    private HopCount(final byte value) {
        if (value < MIN_HOP_COUNT) {
            throw new IllegalArgumentException("hop count must be greater or equal to " + MIN_HOP_COUNT);
        }
        this.value = value;
    }

    public byte getByte() {
        return value;
    }

    /**
     * @throws IllegalStateException if incremented hop count is greater then {@link
     *                               #MAX_HOP_COUNT}
     */
    public HopCount increment() {
        if (value == MAX_HOP_COUNT) {
            throw new IllegalStateException("hop count must not be greater then " + MAX_HOP_COUNT);
        }

        return of(getByte() + 1);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HopCount hopCount = (HopCount) o;
        return value == hopCount.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @Override
    public int compareTo(final HopCount o) {
        return Byte.compare(getByte(), o.getByte());
    }

    /**
     * Creates a new hop count value with given value.
     *
     * @throws IllegalArgumentException if {@code value} is smaller then {@link #MIN_HOP_COUNT}
     */
    public static HopCount of(final byte value) {
        return new HopCount(value);
    }

    /**
     * Creates a new hop count value with given value.
     *
     * @throws IllegalArgumentException if {@code value} is not between {@link #MIN_HOP_COUNT} and
     *                                  {@link #MAX_HOP_COUNT}
     */
    public static HopCount of(final int value) {
        if (value < MIN_HOP_COUNT || value > MAX_HOP_COUNT) {
            throw new IllegalArgumentException("hop count must be between " + MIN_HOP_COUNT + " and " + MAX_HOP_COUNT);
        }
        return of((byte) value);
    }

    /**
     * Creates a new hop count value with given value.
     *
     * @throws IllegalArgumentException if {@code value} is not between {@link #MIN_HOP_COUNT} and
     *                                  {@link #MAX_HOP_COUNT}
     */
    public static HopCount of(final short value) {
        if (value < MIN_HOP_COUNT || value > MAX_HOP_COUNT) {
            throw new IllegalArgumentException("hop count must be between " + MIN_HOP_COUNT + " and " + MAX_HOP_COUNT);
        }
        return of((byte) value);
    }

    /**
     * Creates a new minimal hop count value.
     */
    public static HopCount of() {
        return of(MIN_HOP_COUNT);
    }
}
