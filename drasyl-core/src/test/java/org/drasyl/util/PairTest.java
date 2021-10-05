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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PairTest {
    @Nested
    class First {
        @Test
        void shouldReturnFirstElement() {
            final Pair<Integer, String> pair = Pair.of(10, "beers");

            assertEquals(10, pair.first());
        }
    }

    @Nested
    class Second {
        @Test
        void shouldReturnSecondElement() {
            final Pair<Integer, String> pair = Pair.of(10, "beers");

            assertEquals("beers", pair.second());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldRecognizeEqualPairs() {
            final Pair<Integer, String> pairA = Pair.of(5, "beers");
            final Pair<Integer, String> pairB = Pair.of(5, "beers");
            final Pair<Integer, String> pairC = Pair.of(null, "shots");

            assertEquals(pairA, pairA);
            assertEquals(pairA, pairB);
            assertEquals(pairB, pairA);
            assertNotEquals(pairA, pairC);
            assertNotEquals(pairC, pairA);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldRecognizeEqualPairs() {
            final Pair<Integer, String> pairA = Pair.of(5, "beers");
            final Pair<Integer, String> pairB = Pair.of(5, "beers");
            final Pair<Integer, String> pairC = Pair.of(10, "shots");

            assertEquals(pairA.hashCode(), pairB.hashCode());
            assertNotEquals(pairA.hashCode(), pairC.hashCode());
            assertNotEquals(pairB.hashCode(), pairC.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectString() {
            final String string = Pair.of(5, "beers").toString();

            assertEquals("Pair{first=5, second=beers}", string);
        }
    }
}
