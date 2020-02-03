/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.models;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.jupiter.api.Test;

public class PairTest {

    @Test
    public void equalsTest() {
        Pair<String, String> pair1 = Pair.of("test1", "test2");

        assertEquals(Pair.of("test1", "test2"), pair1);
        assertEquals(Pair.of("test1", "test2").hashCode(), pair1.hashCode());

        assertNotEquals(Pair.of("test2", "test1"), pair1);
        assertNotEquals(Pair.of("test2", "test1").hashCode(), pair1.hashCode());
        assertNotEquals(null, pair1);
        assertNotEquals("bla", pair1);

        pair1.toString();
    }

    @Test
    public void nullParamTest() {
        assertEquals(null, Pair.of(null, "bla").getLeft());
        assertEquals(null, Pair.of("bla", null).getRight());
        assertThat(Pair.of(null, null), allOf(hasProperty("left", equalTo(null)), hasProperty("right", equalTo(null))));
    }

    @Test
    public void correctOrderTest() {
        assertThat(Pair.of("test", 2), allOf(hasProperty("left", equalTo("test")), hasProperty("right", equalTo(2))));
    }
}
