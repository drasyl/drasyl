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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static java.util.Locale.GERMAN;
import static org.drasyl.util.NumberUtil.numberToHumanData;
import static org.drasyl.util.NumberUtil.numberToHumanDataRate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NumberUtilTest {
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
}
