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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TripleTest {
    @Nested
    class First {
        @Test
        void shouldReturnFirstElement() {
            final Triple<Integer, Boolean, String> triple = Triple.of(10, false, "beers");

            assertEquals(10, triple.first());
        }
    }

    @Nested
    class Second {
        @Test
        void shouldReturnSecondElement() {
            final Triple<Integer, Boolean, String> triple = Triple.of(10, false, "beers");

            assertFalse(triple.second());
        }
    }

    @Nested
    class Third {
        @Test
        void shouldReturnThirdElement() {
            final Triple<Integer, Boolean, String> triple = Triple.of(10, false, "beers");

            assertEquals("beers", triple.third());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldRecognizeEqualTriples() {
            final Triple<Integer, Boolean, String> tripleA = Triple.of(5, false, "beers");
            final Triple<Integer, Boolean, String> tripleB = Triple.of(5, false, "beers");
            final Triple<Integer, Boolean, String> tripleC = Triple.of(null, false, "shots");

            assertEquals(tripleA, tripleA);
            assertEquals(tripleA, tripleB);
            assertEquals(tripleB, tripleA);
            assertNotEquals(tripleA, tripleC);
            assertNotEquals(tripleC, tripleA);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldRecognizeEqualTriples() {
            final Triple<Integer, Boolean, String> tripleA = Triple.of(5, false, "beers");
            final Triple<Integer, Boolean, String> tripleB = Triple.of(5, false, "beers");
            final Triple<Integer, Boolean, String> tripleC = Triple.of(null, false, "shots");

            assertEquals(tripleA.hashCode(), tripleB.hashCode());
            assertNotEquals(tripleA.hashCode(), tripleC.hashCode());
            assertNotEquals(tripleB.hashCode(), tripleC.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectString() {
            final String string = Triple.of(5, false, "beers").toString();

            assertEquals("Triple{first=5, second=false, third=beers}", string);
        }
    }
}
