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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SetUtilTest {
    @Nested
    class Merge {
        @Test
        void shouldReturnSetContainingAllElementsOfBothGivenSets() {
            Set<String> a = Set.of("apple", "banana");
            Set<String> b = Set.of("pear", "cherry", "banana");

            assertEquals(Set.of("apple", "banana", "pear", "cherry"), SetUtil.merge(a, b));
        }

        @Test
        void shouldReturnSetContainingAllElementsOfGivenSetAndElement() {
            Set<String> a = Set.of("apple", "banana");
            String b1 = "pear";
            String b2 = "apple";

            assertEquals(Set.of("apple", "banana", "pear"), SetUtil.merge(a, b1));
            assertEquals(Set.of("apple", "banana"), SetUtil.merge(a, b2));
        }
    }

    @Nested
    class Difference {
        @Test
        void shouldReturnSetContainingAllElementsOfSetAThatAreNotInSetB() {
            Set<String> a = Set.of("apple", "banana");
            Set<String> b = Set.of("pear", "cherry", "banana");

            assertEquals(Set.of("apple"), SetUtil.difference(a, b));
        }

        @Test
        void shouldReturnSetContainingAllElementsOfSetAThatAreNotB() {
            Set<String> a = Set.of("apple", "banana");
            String b1 = "pear";
            String b2 = "apple";

            assertEquals(Set.of("apple", "banana"), SetUtil.difference(a, b1));
            assertEquals(Set.of("banana"), SetUtil.difference(a, b2));
        }
    }
}