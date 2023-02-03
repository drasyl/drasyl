/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.InconsistentSortedSetTest.EqualPersonWithSameHeight.equalPersonWithEqualge;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class InconsistentSortedSetTest {
    public static final Person ALICE_15 = new Person("Alice", 15);
    public static final Person ALICE_18 = new Person("Alice", 18);
    public static final Person BOB_16 = new Person("Bob", 16);
    public static final Person CHARLIE_21 = new Person("Charlie", 21);

    @Nested
    class Size {
        @Test
        void shouldReturnNumberOfElementsInSet() {
            final Set<Person> set = new InconsistentSortedSet<>();

            set.add(CHARLIE_21);
            set.add(BOB_16);
            set.add(ALICE_15);
            set.add(ALICE_18);

            assertEquals(3, set.size());
        }
    }

    @Nested
    class IsEmpty {
        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        @Test
        void shouldReturnTrueIfSetIsEmpty() {
            final Set<Person> set = new InconsistentSortedSet<>();

            assertTrue(set.isEmpty());
        }

        @Test
        void shouldReturnFalseIfSetIsNotEmpty() {
            final Set<Person> set = new InconsistentSortedSet<>(List.of(ALICE_15));

            assertFalse(set.isEmpty());
        }
    }

    @Nested
    class Contains {
        @Test
        void shouldReturnTrueIfEqualElementIsContainedInSet() {
            final Set<Person> set = new InconsistentSortedSet<>();

            assertFalse(set.contains(ALICE_15));

            set.add(ALICE_15);
            assertTrue(set.contains(ALICE_18));

            set.remove(ALICE_18);
            assertFalse(set.contains(ALICE_18));
        }
    }

    @Nested
    class IteratorTest {
        @Test
        void shouldReturnIteratorWithSortedElements() {
            final Set<Person> set = new InconsistentSortedSet<>(List.of(CHARLIE_21, BOB_16, ALICE_15, ALICE_18));
            final Iterator<Person> iterator = set.iterator();

            assertTrue(iterator.hasNext());
            assertThat(iterator.next(), equalPersonWithEqualge(ALICE_15));

            assertTrue(iterator.hasNext());
            assertThat(iterator.next(), equalPersonWithEqualge(BOB_16));

            assertTrue(iterator.hasNext());
            assertThat(iterator.next(), equalPersonWithEqualge(CHARLIE_21));

            assertFalse(iterator.hasNext());
        }
    }

    @Nested
    class ToArray {
        @Test
        void shouldReturnArrayWithSortedElements() {
            final Set<Person> set = new InconsistentSortedSet<>(List.of(CHARLIE_21, BOB_16, ALICE_15, ALICE_18));
            final Object[] array = set.toArray();

            assertThat(array, arrayWithSize(3));
            assertThat((Person) array[0], equalPersonWithEqualge(ALICE_15));
            assertThat((Person) array[1], equalPersonWithEqualge(BOB_16));
            assertThat((Person) array[2], equalPersonWithEqualge(CHARLIE_21));
        }

        @Test
        void shouldFillAndReturnArrayWithSortedElements() {
            final Set<Person> set = new InconsistentSortedSet<>(List.of(CHARLIE_21, BOB_16, ALICE_15, ALICE_18));
            final Person[] array = set.toArray(new Person[3]);

            assertThat(array[0], equalPersonWithEqualge(ALICE_15));
            assertThat(array[1], equalPersonWithEqualge(BOB_16));
            assertThat(array[2], equalPersonWithEqualge(CHARLIE_21));
        }
    }

    @Nested
    class Add {
        @Test
        void shouldAddElementIfNoEqualElementAlreadyExistInSet() {
            final Set<Person> set = new InconsistentSortedSet<>();

            assertTrue(set.add(CHARLIE_21));
            assertTrue(set.add(BOB_16));
            assertTrue(set.add(ALICE_15));
            assertFalse(set.add(ALICE_18));

            assertThat(set, hasSize(3));
            assertThat(set, hasItem(equalPersonWithEqualge(ALICE_15)));
            assertThat(set, hasItem(equalPersonWithEqualge(BOB_16)));
            assertThat(set, hasItem(equalPersonWithEqualge(CHARLIE_21)));
        }
    }

    @Nested
    class Remove {
        @Test
        void shouldRemoveEqualElementFromSet() {
            final Set<Person> set = new InconsistentSortedSet<>(List.of(CHARLIE_21, BOB_16, ALICE_18));

            assertTrue(set.remove(CHARLIE_21));
            assertTrue(set.remove(ALICE_15));
            assertFalse(set.remove(ALICE_18));

            assertThat(set, hasSize(1));
            assertThat(set, hasItem(equalPersonWithEqualge(BOB_16)));
        }
    }

    @Nested
    class ContainsAll {
        @Test
        void shouldReturnTrueIfEqualElementsArePresentInSet() {
            final Set<Person> set = new InconsistentSortedSet<>(List.of(BOB_16, ALICE_18));

            assertTrue(set.containsAll(List.of(ALICE_15, BOB_16)));
        }

        @Test
        void shouldReturnFalseIfNoneEqualElementsArePresentInSet() {
            final Set<Person> set = new InconsistentSortedSet<>(List.of(BOB_16, ALICE_18));

            assertFalse(set.containsAll(List.of(ALICE_15, BOB_16, CHARLIE_21)));
        }
    }

    @Nested
    class AddAll {
        @Test
        void shouldAddNewElementsToSet() {
            final Set<Person> set = new InconsistentSortedSet<>();

            assertTrue(set.addAll(List.of(CHARLIE_21, BOB_16, ALICE_15, ALICE_18)));
            assertFalse(set.addAll(List.of(CHARLIE_21, BOB_16, ALICE_15, ALICE_18)));

            assertThat(set, hasSize(3));
            assertThat(set, hasItem(equalPersonWithEqualge(ALICE_15)));
            assertThat(set, hasItem(equalPersonWithEqualge(BOB_16)));
            assertThat(set, hasItem(equalPersonWithEqualge(CHARLIE_21)));
        }
    }

    @Nested
    class RetainAll {
        @Test
        void shouldRemoveAllElementsNotInGivenSet() {
            final Set<Person> set = new InconsistentSortedSet<>(List.of(CHARLIE_21, BOB_16, ALICE_18));

            assertTrue(set.retainAll(List.of(ALICE_15)));
            assertFalse(set.retainAll(List.of(ALICE_15)));

            assertThat(set, hasSize(1));
            assertThat(set, hasItem(equalPersonWithEqualge(ALICE_18)));
        }
    }

    @Nested
    class RemoveAll {
        @Test
        void shouldRemoveAllElementsInGivenSet() {
            final Set<Person> set = new InconsistentSortedSet<>(List.of(CHARLIE_21, BOB_16, ALICE_18));

            assertTrue(set.removeAll(List.of(ALICE_15, CHARLIE_21)));
            assertFalse(set.removeAll(List.of(ALICE_15, CHARLIE_21)));

            assertThat(set, hasSize(1));
            assertThat(set, hasItem(equalPersonWithEqualge(BOB_16)));
        }
    }

    @Nested
    class Clear {
        @Test
        void shouldClearSet() {
            final Set<Person> set = new InconsistentSortedSet<>(List.of(CHARLIE_21, BOB_16, ALICE_18));

            set.clear();

            assertThat(set, hasSize(0));
        }
    }

    @Nested
    class Comparator {
        @Test
        void shouldReturnNull() {
            final SortedSet<Person> set = new InconsistentSortedSet<>();

            assertNull(set.comparator());
        }
    }

    @Nested
    class SubSet {
        @Test
        void shouldReturnRequestedSubSet() {
            final SortedSet<Person> subSet = new InconsistentSortedSet<>(List.of(BOB_16, ALICE_18, CHARLIE_21)).subSet(BOB_16, ALICE_18);

            assertThat(subSet, hasSize(1));
            assertThat(subSet, hasItem(equalPersonWithEqualge(BOB_16)));
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Test
        void shouldThrowExceptionIfFromElementIsGreaterThanToElement() {
            final InconsistentSortedSet<Person> set = new InconsistentSortedSet<>(List.of(BOB_16, ALICE_18));

            assertThrows(IllegalArgumentException.class, () -> set.subSet(ALICE_18, BOB_16));
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Test
        void shouldThrowExceptionIfFromElementLiesOutsideTheBoundsOfTheRange() {
            final InconsistentSortedSet<Person> set = new InconsistentSortedSet<>(List.of(BOB_16, CHARLIE_21));

            assertThrows(IllegalArgumentException.class, () -> set.subSet(ALICE_15, ALICE_18));
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Test
        void shouldThrowExceptionIfToElementLiesOutsideTheBoundsOfTheRange() {
            final InconsistentSortedSet<Person> set = new InconsistentSortedSet<>(List.of(BOB_16, ALICE_18));

            assertThrows(IllegalArgumentException.class, () -> set.subSet(ALICE_15, CHARLIE_21));
        }
    }

    @Nested
    class HeadSet {
        @Test
        void shouldReturnRequestedHeadSet() {
            final SortedSet<Person> headSet = new InconsistentSortedSet<>(List.of(BOB_16, ALICE_18, CHARLIE_21)).headSet(ALICE_18);

            assertThat(headSet, hasSize(1));
            assertThat(headSet, hasItem(equalPersonWithEqualge(BOB_16)));
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Test
        void shouldThrowExceptionIfToElementLiesOutsideTheBoundsOfTheRange() {
            final InconsistentSortedSet<Person> set = new InconsistentSortedSet<>(List.of(BOB_16, ALICE_18));

            assertThrows(IllegalArgumentException.class, () -> set.headSet(CHARLIE_21));
        }
    }

    @Nested
    class TailSet {
        @Test
        void shouldReturnRequestedTailSet() {
            final SortedSet<Person> tailSet = new InconsistentSortedSet<>(List.of(BOB_16, ALICE_18, CHARLIE_21)).tailSet(ALICE_18);

            assertThat(tailSet, hasSize(2));
            assertThat(tailSet, hasItem(equalPersonWithEqualge(ALICE_18)));
            assertThat(tailSet, hasItem(equalPersonWithEqualge(CHARLIE_21)));
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Test
        void shouldThrowExceptionIfToElementLiesOutsideTheBoundsOfTheRange() {
            final InconsistentSortedSet<Person> set = new InconsistentSortedSet<>(List.of(BOB_16, ALICE_18));

            assertThrows(IllegalArgumentException.class, () -> set.tailSet(CHARLIE_21));
        }
    }

    @Nested
    class First {
        @Test
        void shouldWork() {
            final SortedSet<Person> set1 = new InconsistentSortedSet<>(List.of(CHARLIE_21, BOB_16, ALICE_18));
            assertEquals(BOB_16, set1.first());

            final SortedSet<Person> set2 = new InconsistentSortedSet<>();
            assertThrows(NoSuchElementException.class, set2::first);
        }
    }

    @Nested
    class Last {
        @Test
        void shouldWork() {
            final SortedSet<Person> set1 = new InconsistentSortedSet<>(List.of(CHARLIE_21, BOB_16, ALICE_18));
            assertEquals(CHARLIE_21, set1.last());

            final SortedSet<Person> set2 = new InconsistentSortedSet<>();
            assertThrows(NoSuchElementException.class, set2::last);
        }
    }

    /**
     * A data object class that is equal by {@link #name} and sorted by {@link #height}.
     */
    static class Person implements Comparable<Person> {
        final String name;
        final int height;

        Person(final String name, final int height) {
            this.name = name;
            this.height = height;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }

        @Override
        public boolean equals(final Object o) {
            return o instanceof Person && Objects.equals(name, ((Person) o).name);
        }

        @Override
        public String toString() {
            return "Person{" +
                    "name='" + name + '\'' +
                    ", height=" + height +
                    '}';
        }

        @Override
        public int compareTo(final Person o) {
            return Integer.compare(height, o.height);
        }
    }

    /**
     * As {@link Person#equals(Object)} only compares {@link Person#name}, we need this special
     * matcher that also compares {@link Person#height} in addition.
     */
    public static class EqualPersonWithSameHeight extends TypeSafeMatcher<Person> {
        private final Person left;

        private EqualPersonWithSameHeight(final Person left) {
            this.left = requireNonNull(left);
        }

        @Override
        protected boolean matchesSafely(final Person right) {
            return left.equals(right) &&
                    left.height == right.height;
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText("equal neighbor with same height as " + left);
        }

        public static Matcher<Person> equalPersonWithEqualge(final Person left) {
            return new EqualPersonWithSameHeight(left);
        }
    }
}
