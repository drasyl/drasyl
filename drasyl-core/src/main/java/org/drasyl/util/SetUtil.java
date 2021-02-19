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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for operations on {@link Set}s.
 */
public final class SetUtil {
    private SetUtil() {
        // util class
    }

    /**
     * Returns a set containing all elements from set <code>a</code> and set <code>b</code>. If
     * there are duplicates in both sets, the elements from the set <code>a</code> are favored.
     *
     * @param a   set a
     * @param b   set b
     * @param <E> the {@code Set}'s element type
     * @return a {@code Set} containing the specified elements
     */
    public static <E> Set<E> merge(final Set<E> a, final Set<E> b) {
        final HashSet<E> result = new HashSet<>(a);
        result.addAll(b);
        return result;
    }

    /**
     * Returns a set containing all elements from <code>a</code> and the element <code>b</code>.
     * <code>b</code> is ignored if an equal element is already contained in the set
     * <code>a</code>.
     *
     * @param a   set a
     * @param b   set b
     * @param <E> the {@code Set}'s element type
     * @return a {@code Set} containing the specified elements
     */
    @SafeVarargs
    public static <E> Set<E> merge(final Set<E> a, final E... b) {
        return merge(a, Set.of(b));
    }

    /**
     * Returns a set containing all elements from set <code>a</code> that are not in set
     * <code>b</code>.
     *
     * @param a   set a
     * @param b   set b
     * @param <E> the {@code Set}'s element type
     * @return a {@code Set} containing the specified elements
     */
    public static <E> Set<E> difference(final Set<E> a, final Collection<E> b) {
        final HashSet<E> result = new HashSet<>(a);
        result.removeAll(b);
        return result;
    }

    /**
     * Returns a set containing all elements from set <code>a</code> that are not <code>b</code>.
     *
     * @param a   set a
     * @param b   set b
     * @param <E> the {@code Set}'s element type
     * @return a {@code Set} containing the specified elements
     */
    @SafeVarargs
    public static <E> Set<E> difference(final Set<E> a, final E... b) {
        return difference(a, Set.of(b));
    }

    /**
     * Returns the {@code n}-th element from set {@code set}. Throws a {@link
     * IndexOutOfBoundsException} if {@code n} is negative or greater than the set's cardinality.
     *
     * @param set a set
     * @param n   specifies the element to be taken
     * @param <E> the {@link Set}'s element type
     * @return {@code n}-th element from set {@code set}
     * @throws IndexOutOfBoundsException if {@code n} is negative or greater than the set's
     *                                   cardinality
     */
    @SuppressWarnings({ "java:S881", "java:S3242" })
    public static <E> E nthElement(final Set<E> set, final int n) {
        if (n < 0 || n > set.size() - 1) {
            throw new IndexOutOfBoundsException();
        }

        int count = 0;
        for (final E element : set) {
            if (n == count++) {
                return element;
            }
        }

        // unreachable
        return null;
    }

    /**
     * Returns the first element from set {@code set}. Returns {@code null} if set is empty.
     *
     * @param set a set
     * @param <E> the {@link Set}'s element type
     * @return first element from set {@code set}
     */
    @SuppressWarnings("java:S1166")
    public static <E> E firstElement(final Set<E> set) {
        try {
            return nthElement(set, 0);
        }
        catch (final IndexOutOfBoundsException e) {
            return null;
        }
    }
}
