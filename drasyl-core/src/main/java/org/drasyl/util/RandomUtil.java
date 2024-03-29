/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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

import java.util.concurrent.ThreadLocalRandom;

import static org.drasyl.util.Preconditions.requireNonNegative;

/**
 * Utility class for receiving pseudorandom values.
 */
public final class RandomUtil {
    private static final char[] ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();

    private RandomUtil() {
        // util class
    }

    /**
     * Returns a pseudorandom, uniformly distributed {@code int} value between {@code min} and
     * {@code max}.
     *
     * @param min The lowest value to return
     * @param max The highest value to return
     * @return Pseudorandom {@code int}
     * @throws IllegalArgumentException if {@code min} is greater then {@code max}
     */
    public static int randomInt(final int min, final int max) {
        if (min > max) {
            throw new IllegalArgumentException("max must be greater then min");
        }
        else if (min == max) {
            return min;
        }
        else {
            return ThreadLocalRandom.current().nextInt(min, max + 1);
        }
    }

    /**
     * Returns a pseudorandom, uniformly distributed {@code int} value between 0 and {@code max}.
     *
     * @param max The highest value to return
     * @return Pseudorandom {@code int}
     */
    public static int randomInt(final int max) {
        return randomInt(0, max);
    }

    /**
     * Returns a pseudorandom, uniformly distributed {@code long} value between {@code min} and
     * {@code max}.
     *
     * @param min The lowest value to return
     * @param max The highest value to return
     * @return Pseudorandom {@code long}
     * @throws IllegalArgumentException if {@code min} is greater then {@code max}
     */
    public static long randomLong(final long min, final long max) {
        if (min > max) {
            throw new IllegalArgumentException("max must be greater then min");
        }
        else if (min == max) {
            return min;
        }
        else {
            return ThreadLocalRandom.current().nextLong(min, max + 1);
        }
    }

    /**
     * Returns a pseudorandom, uniformly distributed {@code long} value between 0 and {@code max}.
     *
     * @param max The highest value to return
     * @return Pseudorandom {@code long}
     */
    public static long randomLong(final long max) {
        return randomLong(0, max);
    }

    /**
     * Returns an array of length {@code count} containing pseudorandom bytes.
     *
     * @param count The length of the array
     * @return Array containing pseudorandom bytes
     */
    public static byte[] randomBytes(final int count) {
        final byte[] bytes = new byte[requireNonNegative(count, "count must be greater than or equal to 0")];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }

    /**
     * Returns a single pseudorandom byte.
     *
     * @return a pseudorandom byte
     */
    public static byte randomByte() {
        return randomBytes(1)[0];
    }

    /**
     * Returns a string of the given {@code length} containing pseudorandom alphanumeric
     * characters.
     *
     * @param length The length of the string
     * @return string containing pseudorandom alphanumeric characters
     */
    public static String randomString(final int length) {
        final char[] buffer = new char[requireNonNegative(length, "length must be greater than or equal to 0")];

        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = ALPHABET[ThreadLocalRandom.current().nextInt(ALPHABET.length)];
        }

        return new String(buffer);
    }
}
