/*
 * Copyright (c) 2020-2021.
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

import java.util.Random;

/**
 * Utility class for receiving pseudorandom values.
 */
public final class RandomUtil {
    private static final Random RANDOM = new Random();

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
            return RANDOM.nextInt(max - min + 1) + min;
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
            return min + (long) (Math.random() * (max - min + 1));
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
        if (count < 0) {
            throw new IllegalArgumentException("count must be greater than or equal to 0");
        }

        final byte[] bytes = new byte[count];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
