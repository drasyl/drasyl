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
package org.drasyl.plugin.groups.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static java.time.Duration.ofSeconds;
import static org.drasyl.plugin.groups.util.DurationUtil.max;
import static org.drasyl.plugin.groups.util.DurationUtil.min;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class DurationUtilTest {
    @Nested
    class Normalize {
        @Test
        void shouldNormalizeDurationToAtLeastOneMinute() {
            final Duration duration = ofSeconds(59);

            assertEquals(Duration.ofMinutes(1), DurationUtil.normalize(duration));
        }

        @Test
        void shouldDoNothingOnDurationGreaterOrEqualsToOneMinute() {
            final Duration duration1 = ofSeconds(60);
            final Duration duration2 = ofSeconds(61);

            assertEquals(duration1, DurationUtil.normalize(duration1));
            assertEquals(duration2, DurationUtil.normalize(duration2));
        }
    }

    @Nested
    class Max {
        @Test
        void shouldReturnLongerDuration() {
            assertEquals(
                    ofSeconds(60),
                    max(ofSeconds(30), ofSeconds(60))
            );
            assertEquals(
                    ofSeconds(60),
                    max(ofSeconds(60), ofSeconds(30))
            );
            assertEquals(
                    ofSeconds(15),
                    max(ofSeconds(15), ofSeconds(15))
            );

            final Duration d = ofSeconds(15);

            assertThrows(NullPointerException.class, () -> max(null, d));
            assertThrows(NullPointerException.class, () -> max(d, null));
            assertThrows(NullPointerException.class, () -> max(null, null));
        }
    }

    @Nested
    class Min {
        @Test
        void shouldReturnLongerDuration() {
            assertEquals(
                    ofSeconds(30),
                    min(ofSeconds(30), ofSeconds(60))
            );
            assertEquals(
                    ofSeconds(30),
                    min(ofSeconds(60), ofSeconds(30))
            );
            assertEquals(
                    ofSeconds(15),
                    min(ofSeconds(15), ofSeconds(15))
            );

            final Duration d = ofSeconds(15);

            assertThrows(NullPointerException.class, () -> min(null, d));
            assertThrows(NullPointerException.class, () -> min(d, null));
            assertThrows(NullPointerException.class, () -> min(null, null));
        }
    }
}
