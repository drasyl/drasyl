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

package org.drasyl.core.common.models;

import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PairTest {
    @Test
    public void firstShouldReturnFirstElement() {
        Pair pair = Pair.of(10, "beers");

        assertEquals(10, pair.first());
    }
    @Test
    public void secondShouldReturnSecondElement() {
        Pair pair = Pair.of(10, "beers");

        assertEquals("beers", pair.second());
    }

    @Test
    public void equalsShouldRecognizeEqualPairs() {
        Pair pairA = Pair.of(5, "beers");
        Pair pairB = Pair.of(5, "beers");
        Pair pairC = Pair.of(null, "shots");

        assertTrue(pairA.equals(pairA));
        assertTrue(pairA.equals(pairB));
        assertTrue(pairB.equals(pairA));
        assertFalse(pairA.equals(null));
        assertFalse(pairA.equals(pairC));
        assertFalse(pairC.equals(pairA));
    }

    @Test
    public void hashCodeShouldRecognizeEqualPairs() {
        Pair pairA = Pair.of(5, "beers");
        Pair pairB = Pair.of(5, "beers");
        Pair pairC = Pair.of(10, "shots");

        assertEquals(pairA.hashCode(), pairB.hashCode());
        assertNotEquals(pairA.hashCode(), pairC.hashCode());
        assertNotEquals(pairB.hashCode(), pairC.hashCode());
    }

    @Test
    void toStringShouldReturnCorrectString() {
        String string = Pair.of(5, "beers").toString();

        assertEquals("Pair{first=5, second=beers}", string);
    }
}
