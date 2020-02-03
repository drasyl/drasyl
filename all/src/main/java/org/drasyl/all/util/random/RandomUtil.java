/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.util.random;

import java.security.SecureRandom;

public class RandomUtil {
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static final SecureRandom SRND = new SecureRandom();

    private RandomUtil() {
    }

    /**
     * Generates a secure random HEX String with the given {@code entropy} of bytes.
     *
     * <p>
     * Recommendation:
     *     <ul>
     *         <li>4 byte for small sets</li>
     *         <li>8 bytes for unique internal strings, e.g. hash tables</li>
     *         <li>16 bytes for global uniqueness, e.g. auth token</li>
     *     </ul>
     * <p>
     * You can also use the following probability table for the "Birthday problem", as a starting point for a suitable
     * entropy size:
     * <a href="https://en.wikipedia.org/wiki/Birthday_problem#Probability_table">Birthday problem probability table</a>
     * </p>
     *
     * @param entropy entropy in bytes
     * @return a secure random HEX String
     */
    public static String randomString(int entropy) {
        byte[] token = new byte[entropy];
        SRND.nextBytes(token);

        return bytesToHex(token);
    }

    /**
     * Generates a random number with the static {@link SecureRandom} of this class. Avoids overhead of generating a
     * new instance of {@link SecureRandom}.
     *
     * @param bound the upper bound (exclusive).  Must be positive.
     * @return the next pseudorandom, uniformly distributed {@code int}
     * value between zero (inclusive) and {@code bound} (exclusive)
     * from this random number generator's sequence
     */
    public static int randomNumber(int bound) {
        return SRND.nextInt(bound);
    }

    /**
     * Fast bytes to hex implementation.
     *
     * @param bytes byte array
     * @return hex representation of the byte array
     */
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
