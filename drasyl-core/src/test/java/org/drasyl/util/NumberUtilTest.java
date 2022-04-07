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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static java.util.Locale.GERMAN;
import static org.drasyl.util.NumberUtil.numberToHumanData;
import static org.drasyl.util.NumberUtil.numberToHumanDataRate;
import static org.drasyl.util.NumberUtil.sampleStandardDeviation;
import static org.drasyl.util.NumberUtil.sampleVariance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NumberUtilTest {
    final double ALLOWED_ERROR = 1e-10;
    final double[] TWO_VALUES = new double[]{ 12.34, -56.78 };
    final double TWO_VALUES_MEAN = (12.34 - 56.78) / 2;
    final double TWO_VALUES_SUM_OF_SQUARES_OF_DELTAS =
            (12.34 - TWO_VALUES_MEAN) * (12.34 - TWO_VALUES_MEAN)
                    + (-56.78 - TWO_VALUES_MEAN) * (-56.78 - TWO_VALUES_MEAN);
    final double[] MANY_VALUES = new double[]{ 1.1, -44.44, 33.33, 555.555, -2.2 };
    final int MANY_VALUES_COUNT = 5;
    final double MANY_VALUES_MEAN = (1.1 - 44.44 + 33.33 + 555.555 - 2.2) / 5;
    final double MANY_VALUES_SUM_OF_SQUARES_OF_DELTAS =
            (1.1 - MANY_VALUES_MEAN) * (1.1 - MANY_VALUES_MEAN)
                    + (-44.44 - MANY_VALUES_MEAN) * (-44.44 - MANY_VALUES_MEAN)
                    + (33.33 - MANY_VALUES_MEAN) * (33.33 - MANY_VALUES_MEAN)
                    + (555.555 - MANY_VALUES_MEAN) * (555.555 - MANY_VALUES_MEAN)
                    + (-2.2 - MANY_VALUES_MEAN) * (-2.2 - MANY_VALUES_MEAN);

    @Nested
    class NumberToHumanData {
        @Test
        void shouldReturnCorrectHumanReadableRepresentation() {
            // positive values
            assertEquals("0 B", numberToHumanData(0));
            assertEquals("1.00 kB", numberToHumanData(1000));
            assertEquals("1.02 kB", numberToHumanData(1024));
            assertEquals("10.0 kB", numberToHumanData(10 * 1_000));
            assertEquals("1.00 MB", numberToHumanData(1000 * 1_000));
            assertEquals("1 GB", numberToHumanData(1000 * 1000 * 1000, (short) 0));
            assertEquals("1.0 TB", numberToHumanData(1000L * 1000 * 1000 * 1000, (short) 1));
            assertEquals("1.00 PB", numberToHumanData(1000L * 1000 * 1000 * 1000 * 1000, (short) 2));
            assertEquals("1.000 EB", numberToHumanData(1000L * 1000 * 1000 * 1000 * 1000 * 1000, (short) 3));
            assertEquals("9,22 EB", numberToHumanData(Long.MAX_VALUE, GERMAN));

            // negative values
            assertEquals("-1 B", numberToHumanData(-1));
            assertEquals("-1.00 kB", numberToHumanData(-1000));
            assertEquals("-1.02 kB", numberToHumanData(-1024));
            assertEquals("-10.0 kB", numberToHumanData(-10 * 1000));
            assertEquals("-1.00 MB", numberToHumanData(-1000 * 1000));
            assertEquals("-1 GB", numberToHumanData(-1000 * 1000 * 1000, (short) 0));
            assertEquals("-1.0 TB", numberToHumanData(-1000L * 1000 * 1000 * 1000, (short) 1));
            assertEquals("-1.00 PB", numberToHumanData(-1000L * 1000 * 1000 * 1000 * 1000, (short) 2));
            assertEquals("-1.000 EB", numberToHumanData(-1000L * 1000 * 1000 * 1000 * 1000 * 1000, (short) 3));
            assertEquals("-9,22 EB", numberToHumanData(Long.MIN_VALUE, GERMAN));
        }

        @Test
        void shouldThrowExceptionForInvalidArguments() {
            assertThrows(IllegalArgumentException.class, () -> numberToHumanData(0, (short) -2));
        }
    }

    @Nested
    class NumberToHumanDataRate {
        @Test
        void shouldReturnCorrectHumanReadableRepresentation() {
            // positive values
            assertEquals("0 bit/s", numberToHumanDataRate(0));
            assertEquals("1.00 kbit/s", numberToHumanDataRate(1000));
            assertEquals("1.02 kbit/s", numberToHumanDataRate(1024));
            assertEquals("10.0 kbit/s", numberToHumanDataRate(10 * 1_000));
            assertEquals("1.00 Mbit/s", numberToHumanDataRate(1000 * 1_000));
            assertEquals("1 Gbit/s", numberToHumanDataRate(1000 * 1000 * 1000, (short) 0));
            assertEquals("1.0 Tbit/s", numberToHumanDataRate(1000L * 1000 * 1000 * 1000, (short) 1));
            assertEquals("1.00 Pbit/s", numberToHumanDataRate(1000L * 1000 * 1000 * 1000 * 1000, (short) 2));
            assertEquals("1.000 Ebit/s", numberToHumanDataRate(1000L * 1000 * 1000 * 1000 * 1000 * 1000, (short) 3));
            assertEquals("9,22 Ebit/s", numberToHumanDataRate(Long.MAX_VALUE, GERMAN));

            // negative values
            assertEquals("-1 bit/s", numberToHumanDataRate(-1));
            assertEquals("-1.00 kbit/s", numberToHumanDataRate(-1000));
            assertEquals("-1.02 kbit/s", numberToHumanDataRate(-1024));
            assertEquals("-10.0 kbit/s", numberToHumanDataRate(-10 * 1000));
            assertEquals("-1.00 Mbit/s", numberToHumanDataRate(-1000 * 1000));
            assertEquals("-1 Gbit/s", numberToHumanDataRate(-1000 * 1000 * 1000, (short) 0));
            assertEquals("-1.0 Tbit/s", numberToHumanDataRate(-1000L * 1000 * 1000 * 1000, (short) 1));
            assertEquals("-1.00 Pbit/s", numberToHumanDataRate(-1000L * 1000 * 1000 * 1000 * 1000, (short) 2));
            assertEquals("-1.000 Ebit/s", numberToHumanDataRate(-1000L * 1000 * 1000 * 1000 * 1000 * 1000, (short) 3));
            assertEquals("-9,22 Ebit/s", numberToHumanDataRate(Long.MIN_VALUE, GERMAN));
        }

        @Test
        void shouldThrowExceptionForInvalidArguments() {
            assertThrows(IllegalArgumentException.class, () -> numberToHumanData(0, (short) -2));
        }
    }

    @Test
    void testSampleVariance() {
        assertThat(sampleVariance(TWO_VALUES), is(closeTo(TWO_VALUES_SUM_OF_SQUARES_OF_DELTAS, ALLOWED_ERROR)));
        assertThat(sampleVariance(MANY_VALUES), is(closeTo(MANY_VALUES_SUM_OF_SQUARES_OF_DELTAS / (MANY_VALUES_COUNT - 1), ALLOWED_ERROR)));
    }

    @Test
    void testSampleStandardDeviation() {
        assertThat(sampleStandardDeviation(TWO_VALUES), is(closeTo(Math.sqrt(TWO_VALUES_SUM_OF_SQUARES_OF_DELTAS), ALLOWED_ERROR)));
        assertThat(sampleStandardDeviation(MANY_VALUES), is(closeTo(Math.sqrt(MANY_VALUES_SUM_OF_SQUARES_OF_DELTAS / (MANY_VALUES_COUNT - 1)), ALLOWED_ERROR)));
    }
}
