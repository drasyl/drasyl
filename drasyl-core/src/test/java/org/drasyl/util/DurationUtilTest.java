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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static java.time.Duration.ofSeconds;
import static org.drasyl.util.DurationUtil.max;
import static org.drasyl.util.DurationUtil.min;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class DurationUtilTest {
    @Nested
    class Max {
        @SuppressWarnings({ "ResultOfMethodCallIgnored", "ConstantConditions" })
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
        @SuppressWarnings({ "ResultOfMethodCallIgnored", "ConstantConditions" })
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
