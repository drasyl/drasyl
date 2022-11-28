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

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Locale;
import java.util.stream.DoubleStream;

/**
 * Utility class for number-related operations.
 */
public final class NumberUtil {
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
    @SuppressWarnings({ "java:S109", "java:S1541" })
    public static String numberToHumanData(Number number,
                                           short precision,
                                           Locale locale) {
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

    /**
     * Returns the <a href="http://en.wikipedia.org/wiki/Variance#Sample_variance">unbiased sample
     * variance</a> of given {@code values}.
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public static double sampleVariance(final double... values) {
        if (values.length > 1) {
            final double mean = DoubleStream.of(values).average().getAsDouble();
            return DoubleStream.of(values).map(d -> Math.pow(d - mean, 2)).sum() / (values.length - 1);
        }
        else {
            return 0;
        }
    }

    /**
     * Returns the
     * <a href="http://en.wikipedia.org/wiki/Standard_deviation#Corrected_sample_standard_deviation">
     * corrected sample standard deviation</a> of given {@code values}.
     */
    public static double sampleStandardDeviation(final double... values) {
        return Math.sqrt(sampleVariance(values));
    }
}
