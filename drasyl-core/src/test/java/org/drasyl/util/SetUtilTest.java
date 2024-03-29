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

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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

    @Nested
    class FirstElements {
        @Test
        void shouldReturnFirstNElementsOfASet() {
            final SortedSet<String> set = new TreeSet<>();
            set.add("banana");
            set.add("cherry");
            set.add("pear");

            final Set<String> subSet = SetUtil.firstElements(set, 2);

            assertThat(subSet, hasSize(2));
            assertThat(subSet, hasItem(equalTo("banana")));
            assertThat(subSet, hasItem(equalTo("cherry")));
        }

        @Test
        void shouldReturnEmptySetIfZeroElementsAreRequested() {
            final SortedSet<String> set = new TreeSet<>();
            set.add("banana");
            set.add("cherry");
            set.add("pear");

            final Set<String> subSet = SetUtil.firstElements(set, 0);

            assertThat(subSet, hasSize(0));
        }

        @Test
        void shouldThrowExceptionForNegativeN() {
            final SortedSet<String> set = new TreeSet<>();
            set.add("banana");
            set.add("cherry");
            set.add("pear");

            assertThrows(IllegalArgumentException.class, () -> SetUtil.firstElements(set, -1));
        }
    }

    @Nested
    class CartesianProduct {
        @Test
        void shouldReturnSetOfAllOrderesPairs() {
            final Set<String> a = Set.of("Foo", "Bar", "Baz");
            final Set<Integer> b = Set.of(1, 2, 3);

            final Set<Pair<String, Integer>> product = SetUtil.cartesianProduct(a, b);
            assertEquals(product.size(), a.size() * b.size());
            assertThat(product, containsInAnyOrder(
                    Pair.of("Foo", 1),
                    Pair.of("Foo", 2),
                    Pair.of("Foo", 3),
                    Pair.of("Bar", 1),
                    Pair.of("Bar", 2),
                    Pair.of("Bar", 3),
                    Pair.of("Baz", 1),
                    Pair.of("Baz", 2),
                    Pair.of("Baz", 3)
            ));
        }
    }
}
