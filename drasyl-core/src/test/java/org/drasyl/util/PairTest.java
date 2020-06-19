/*
 * Copyright (c) 2020.
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PairTest {
    @Nested
    class First {
        @Test
        void shouldReturnFirstElement() {
            Pair<Integer, String> pair = Pair.of(10, "beers");

            assertEquals(10, pair.first());
        }
    }

    @Nested
    class Second {
        @Test
        void shouldReturnSecondElement() {
            Pair<Integer, String> pair = Pair.of(10, "beers");

            assertEquals("beers", pair.second());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldRecognizeEqualPairs() {
            Pair<Integer, String> pairA = Pair.of(5, "beers");
            Pair<Integer, String> pairB = Pair.of(5, "beers");
            Pair<Integer, String> pairC = Pair.of(null, "shots");

            assertEquals(pairA, pairA);
            assertEquals(pairA, pairB);
            assertEquals(pairB, pairA);
            Assertions.assertNotEquals(null, pairA);
            Assertions.assertNotEquals(pairA, pairC);
            Assertions.assertNotEquals(pairC, pairA);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldRecognizeEqualPairs() {
            Pair pairA = Pair.of(5, "beers");
            Pair pairB = Pair.of(5, "beers");
            Pair pairC = Pair.of(10, "shots");

            assertEquals(pairA.hashCode(), pairB.hashCode());
            assertNotEquals(pairA.hashCode(), pairC.hashCode());
            assertNotEquals(pairB.hashCode(), pairC.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectString() {
            String string = Pair.of(5, "beers").toString();

            assertEquals("Pair{first=5, second=beers}", string);
        }
    }
}
