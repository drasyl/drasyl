package org.drasyl.util;

import static org.drasyl.util.Preconditions.requireInRange;
import static org.drasyl.util.Preconditions.requireNonNegative;

/**
 * Utility class for <a href="https://www.rfc-editor.org/rfc/rfc1982">serial number arithmetic</a>.
 */
public class SerialNumberArithmetic {
    private static final int MAX_SERIAL_BITS = 63;

    private SerialNumberArithmetic() {
        // util class
    }

    /**
     * @param s          sequence number we want increment. Must be non-negative.
     * @param n          number to add. Must be within range {@code [0, (2^(serialBits - 1) - 1)]}
     * @param serialBits size of the serial number space
     * @return resulting sequence number of the addition
     */
    public static long add(final long s, final long n, final int serialBits) {
        return (requireNonNegative(s) + requireInRange(n, 0, (long) Math.pow(2, serialBits - 1d) - 1)) % (long) Math.pow(2, requireInRange(serialBits, 0, MAX_SERIAL_BITS));
    }

    /**
     * @param i1         first non-negative number
     * @param i2         second non-negative number
     * @param serialBits size of the serial number space
     * @return {@code true} if {@code i1} is less than {@code i2}. Otherwise {@code false}
     */
    public static boolean lessThan(final long i1, final long i2, final int serialBits) {
        requireNonNegative(i1);
        requireNonNegative(i2);
        final long pow = (long) Math.pow(2, requireInRange(serialBits, 0, MAX_SERIAL_BITS) - 1d);
        return (i1 < i2 && i2 - i1 < pow) || (i1 > i2 && i1 - i2 > pow);
    }

    /**
     * @param i1         first non-negative number
     * @param i2         second non-negative number
     * @param serialBits size of the serial number space
     * @return {@code true} if {@code i1} is less than or equal to {@code i2}. Otherwise {@code
     * false}
     */
    public static boolean lessThanOrEqualTo(final long i1, final long i2, final int serialBits) {
        return i1 == i2 || lessThan(i1, i2, serialBits);
    }

    /**
     * @param i1         first non-negative number
     * @param i2         second non-negative number
     * @param serialBits size of the serial number space
     * @return {@code true} if {@code i1} is greater than {@code i2}. Otherwise {@code false}
     */
    public static boolean greaterThan(final long i1, final long i2, final int serialBits) {
        requireNonNegative(i1);
        requireNonNegative(i2);
        final long pow = (long) Math.pow(2, requireInRange(serialBits, 0, MAX_SERIAL_BITS) - 1d);
        return (i1 < i2 && i2 - i1 > pow) || (i1 > i2 && i1 - i2 < pow);
    }

    /**
     * @param i1         first non-negative number
     * @param i2         second non-negative number
     * @param serialBits size of the serial number space
     * @return {@code true} if {@code i1} is greater than or equal to {@code i2}. Otherwise {@code
     * false}
     */
    public static boolean greaterThanOrEqualTo(final long i1, final long i2, final int serialBits) {
        return i1 == i2 || greaterThan(i1, i2, serialBits);
    }
}
