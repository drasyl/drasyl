/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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

import static java.nio.ByteOrder.LITTLE_ENDIAN;

/**
 * This class contains methods for MurmurHash3 generation.
 */
public final class Murmur3 {
    private Murmur3() {
        // util class
    }

    /**
     * Generates a MurmurHash3 x86 32-bit hash without seed.
     *
     * @param data the input to hash
     * @return the MurmurHash3 x86 32-bit hash
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public static int murmur3_x86_32(final byte[] data) {
        return murmur3_x86_32(data, 0);
    }

    /**
     * Generates a MurmurHash3 x86 32-bit hash.
     * <p>
     * This method is a Java port of the <a href="https://github.com/aappleby/smhasher/blob/61a0530f28277f2e850bfc39600ce61d02b518de/src/MurmurHash3.cpp#L94-L146">{@code
     * MurmurHash3_x86_32}</a> function from Austin Appleby.
     *
     * @param data the input to hash
     * @param seed the initial seed value
     * @return the MurmurHash3 x86 32-bit hash
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public static int murmur3_x86_32(final byte[] data, final int seed) {
        int h1 = seed;

        final int c1 = 0xcc9e2d51;
        final int c2 = 0x1b873593;

        // body
        final ByteBuffer buffer = ByteBuffer.wrap(data).order(LITTLE_ENDIAN);
        while (buffer.remaining() > 3) {
            int k1 = buffer.getInt();

            k1 *= c1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= c2;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        // tail
        int k1 = 0;

        switch (buffer.remaining()) {
            case 3:
                k1 ^= (buffer.get(buffer.position() + 2) & 0xff) << 16;
            case 2:
                k1 ^= (buffer.get(buffer.position() + 1) & 0xff) << 8;
            case 1:
                k1 ^= buffer.get() & 0xff;

                k1 *= c1;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= c2;
                h1 ^= k1;
        }

        // finalization
        h1 ^= buffer.capacity();

        return fmix32(h1);
    }

    /**
     * Generates a MurmurHash3 x86 32-bit hash with Little Endian Byte Order.
     *
     * @param data the input to hash
     * @param seed the initial seed value
     * @return the MurmurHash3 x86 32-bit hash
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public static int murmur3_x86_32LE(final byte[] data, final int seed) {
        return Integer.reverseBytes(murmur3_x86_32(data, seed));
    }

    /**
     * Generates a MurmurHash3 x86 32-bit hash without seed with Little Endian Byte Order.
     *
     * @param data the input to hash
     * @return the MurmurHash3 x86 32-bit hash
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public static int murmur3_x86_32LE(final byte[] data) {
        return murmur3_x86_32LE(data, 0);
    }

    /**
     * Generates a MurmurHash3 x86 32-bit hash with Little Endian Byte Order.
     *
     * @param data the input to hash
     * @param seed the initial seed value
     * @return the MurmurHash3 x86 32-bit hash
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public static byte[] murmur3_x86_32BytesLE(final byte[] data, final int seed) {
        return ByteBuffer.allocate(4).order(LITTLE_ENDIAN).putInt(Murmur3.murmur3_x86_32(data, seed)).array();
    }

    /**
     * Generates a MurmurHash3 x86 32-bit hash without seed with Little Endian Byte Order.
     *
     * @param data the input to hash
     * @return the MurmurHash3 x86 32-bit hash
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public static byte[] murmur3_x86_32BytesLE(final byte[] data) {
        return murmur3_x86_32BytesLE(data, 0);
    }

    //

    /**
     * Force all bits of the hash block {@code h} to avalanche.
     * <p>
     * This method is a Java port of the <a href="https://github.com/aappleby/smhasher/blob/61a0530f28277f2e850bfc39600ce61d02b518de/src/MurmurHash3.cpp#L68-L77">{@code
     * fmix32}</a> function from Austin Appleby.
     *
     * @param h hash block to avalance
     * @return avalanced hash block
     */
    private static int fmix32(int h) {
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }
}
