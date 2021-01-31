/*
 * Copyright (c) 2021.
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

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Locale;

/**
 * Utility class for number-related operations.
 */
public class NumberUtil {
    private NumberUtil() {
        // util class
    }

    /**
     * Takes a {@link Number} with bytes and returns it as a formatted SI (metric) multiple-byte
     * number.
     * <p>
     * Inspired by <a href="https://stackoverflow.com/a/3758880/1074188">https://stackoverflow.com/a/3758880/1074188</a>.
     *
     * @param number    number containing size in bytes
     * @param precision Sets the precision of the number. Value -1 has a special meaning here: an
     *                  absolute formatted number less then 10 results in precision 2; an absolute
     *                  formatted number less then 100 results in precision 1; all other absolute
     *                  formatted numbers result in precision 0
     * @param locale    The {@link Locale locale} to apply during formatting. If {@code locale} is
     *                  {@code null} then no localization is applied.
     * @return the formatted SI (metric) multiple-byte number
     * @throws IllegalArgumentException if precision is less than {@code -1}
     */
    public static String numberToHumanData(final Number number,
                                           short precision,
                                           final Locale locale) {
        if (precision < -1) {
            throw new IllegalArgumentException("precision must not be less than -1");
        }

        long bytes = number.longValue();
        if (-1000 < bytes && bytes < 1000) {
            return bytes + " B";
        }
        final CharacterIterator ci = new StringCharacterIterator("kMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            ci.next();
        }

        // build format
        if (precision < 0) {
            if (bytes > -10_000 && bytes < 10_000) {
                precision = 2;
            }
            else if (bytes > -100_000 && bytes < 100_000) {
                precision = 1;
            }
            else {
                precision = 0;
            }
        }
        final String format = "%." + precision + "f %cB";
        return String.format(locale, format, bytes / 1000.0, ci.current());
    }

    /**
     * Takes a {@link Number} with bytes and returns it as a formatted SI (metric) multiple-byte
     * number.
     *
     * @param number    number containing size in bytes
     * @param precision Sets the precision of the number. Value -1 has a special meaning here: an
     *                  absolute formatted number less then 10 results in precision 2; an absolute
     *                  formatted number less then 100 results in precision 1; all other absolute
     *                  formatted numbers result in precision 0
     * @return the formatted SI (metric) multiple-byte number
     * @throws IllegalArgumentException if precision is less than {@code -1}
     */
    public static String numberToHumanData(final Number number, final short precision) {
        return numberToHumanData(number, precision, null);
    }

    /**
     * Takes a {@link Number} with bytes and returns it as a formatted SI (metric) multiple-byte
     * number.
     *
     * @param number number containing size in bytes
     * @param locale The {@link Locale locale} to apply during formatting. If {@code locale} is
     *               {@code null} then no localization is applied.
     * @return the formatted SI (metric) multiple-byte number
     * @throws IllegalArgumentException if precision is less than {@code -1}
     */
    public static String numberToHumanData(final Number number, final Locale locale) {
        return numberToHumanData(number, (short) -1, locale);
    }

    /**
     * Takes a {@link Number} with bytes and returns it as a formatted SI (metric) multiple-byte
     * number.
     *
     * @param number number containing size in bytes
     * @return the formatted SI (metric) multiple-byte number
     * @throws IllegalArgumentException if precision is less than {@code -1}
     */
    public static String numberToHumanData(final Number number) {
        return numberToHumanData(number, (short) -1);
    }

    /**
     * Takes a {@link Number} with bytes and returns it as a formatted SI (metric) data-rate.
     * <p>
     * Inspired by <a href="https://stackoverflow.com/a/3758880/1074188">https://stackoverflow.com/a/3758880/1074188</a>.
     *
     * @param number    number containing size in bytes
     * @param precision Sets the precision of the number. Value -1 has a special meaning here: an
     *                  absolute formatted number less then 10 results in precision 2; an absolute
     *                  formatted number less then 100 results in precision 1; all other absolute
     *                  formatted numbers result in precision 0
     * @param locale    The {@link Locale locale} to apply during formatting. If {@code locale} is
     *                  {@code null} then no localization is applied.
     * @return the formatted SI (metric) data-rate
     * @throws IllegalArgumentException if precision is less than {@code -1}
     */
    public static String numberToHumanDataRate(final Number number,
                                               final short precision,
                                               final Locale locale) {
        return numberToHumanData(number.longValue(), precision, locale).replace("B", "bit/s");
    }

    /**
     * Takes a {@link Number} with bytes and returns it as a formatted SI (metric) data-rate.
     *
     * @param number    number containing size in bytes
     * @param precision Sets the precision of the number. Value -1 has a special meaning here: an
     *                  absolute formatted number less then 10 results in precision 2; an absolute
     *                  formatted number less then 100 results in precision 1; all other absolute
     *                  formatted numbers result in precision 0
     * @return the formatted SI (metric) data-rate
     * @throws IllegalArgumentException if precision is less than {@code -1}
     */
    public static String numberToHumanDataRate(final Number number, final short precision) {
        return numberToHumanDataRate(number, precision, null);
    }

    /**
     * Takes a {@link Number} with bytes and returns it as a formatted SI (metric) data-rate.
     *
     * @param number number containing size in bytes
     * @param locale The {@link Locale locale} to apply during formatting. If {@code locale} is
     *               {@code null} then no localization is applied.
     * @return the formatted SI (metric) data-rate
     * @throws IllegalArgumentException if precision is less than {@code -1}
     */
    public static String numberToHumanDataRate(final Number number, final Locale locale) {
        return numberToHumanDataRate(number, (short) -1, locale);
    }

    /**
     * Takes a {@link Number} with bytes and returns it as a formatted SI (metric) data-rate.
     *
     * @param number number containing size in bytes
     * @return the formatted SI (metric) data-rate
     * @throws IllegalArgumentException if precision is less than {@code -1}
     */
    public static String numberToHumanDataRate(final Number number) {
        return numberToHumanDataRate(number, (short) -1);
    }
}
