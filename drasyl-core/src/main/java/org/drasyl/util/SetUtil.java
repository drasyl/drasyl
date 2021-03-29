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
