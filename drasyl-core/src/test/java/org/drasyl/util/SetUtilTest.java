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

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SetUtilTest {
    @Nested
    class Merge {
        @Test
        void shouldReturnSetContainingAllElementsOfBothGivenSets() {
            final Set<String> a = Set.of("apple", "banana");
            final Set<String> b = Set.of("pear", "cherry", "banana");

            assertEquals(Set.of("apple", "banana", "pear", "cherry"), SetUtil.merge(a, b));
        }

        @Test
        void shouldReturnSetContainingAllElementsOfGivenSetAndElement() {
            final Set<String> a = Set.of("apple", "banana");
            final String b1 = "pear";
            final String b2 = "apple";

            assertEquals(Set.of("apple", "banana", "pear"), SetUtil.merge(a, b1));
            assertEquals(Set.of("apple", "banana"), SetUtil.merge(a, b2));
        }
    }

    @Nested
    class Difference {
        @Test
        void shouldReturnSetContainingAllElementsOfSetAThatAreNotInSetB() {
            final Set<String> a = Set.of("apple", "banana");
            final Set<String> b = Set.of("pear", "cherry", "banana");

            assertEquals(Set.of("apple"), SetUtil.difference(a, b));
        }

        @Test
        void shouldReturnSetContainingAllElementsOfSetAThatAreNotB() {
            final Set<String> a = Set.of("apple", "banana");
            final String b1 = "pear";
            final String b2 = "apple";

            assertEquals(Set.of("apple", "banana"), SetUtil.difference(a, b1));
            assertEquals(Set.of("banana"), SetUtil.difference(a, b2));
        }
    }

    @Nested
    class NthElement {
        @Test
        void shouldReturnTheNthElementOfASet() {
            final SortedSet<String> set = new TreeSet<>();
            set.add("banana");
            set.add("cherry");
            set.add("pear");

            assertEquals("banana", SetUtil.nthElement(set, 0));
            assertEquals("cherry", SetUtil.nthElement(set, 1));
            assertEquals("pear", SetUtil.nthElement(set, 2));
        }

        @Test
        void shouldThrowExceptionForNegativeN() {
            final Set<String> set = Set.of("pear", "cherry", "banana");

            assertThrows(IndexOutOfBoundsException.class, () -> SetUtil.nthElement(set, -1));
        }

        @Test
        void shouldThrowExceptionForTooLargeN() {
            final Set<String> set = Set.of("pear", "cherry", "banana");

            assertThrows(IndexOutOfBoundsException.class, () -> SetUtil.nthElement(set, 3));
        }
    }

    @Nested
    class FirstElement {
        @Test
        void shouldReturnFirstElementOfASet() {
            final SortedSet<String> set = new TreeSet<>();
            set.add("banana");
            set.add("cherry");
            set.add("pear");

            assertEquals("banana", SetUtil.firstElement(set));
        }

        @Test
        void shouldReturnNullForEmptySet() {
            final SortedSet<String> set = new TreeSet<>();

            assertNull(SetUtil.firstElement(set));
        }
    }
}
