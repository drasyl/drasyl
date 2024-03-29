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

import static org.drasyl.util.Preconditions.requireNonNegative;

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
     * Returns the {@code n}-th element from set {@code set}. Throws a
     * {@link IndexOutOfBoundsException} if {@code n} is negative or greater than the set's
     * cardinality.
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

    /**
     * Returns the first {@code n} elements from set {@code set}.
     *
     * @param set a set
     * @param n   elements to return
     * @param <E> the {@link Set}'s element type
     * @return first {@code n} elements from set {@code set}
     * @throws IllegalArgumentException if {@code n} is negative
     */
    @SuppressWarnings({ "java:S881", "java:S1166", "java:S3242" })
    public static <E> Set<E> firstElements(final Set<E> set, final int n) {
        requireNonNegative(n);
        final Set<E> subSet = new HashSet<>();
        int count = 0;
        for (final E element : set) {
            if (n == count++) {
                break;
            }
            subSet.add(element);
        }

        return subSet;
    }

    /**
     * Returns the cartesian product of the two sets {@code a} and {@code b}. This is the set of all
     * ordered {@link Pair}s {@code (x,y)} where {@code x} is in {@code a} and {@code y} is in
     * {@code b}.
     *
     * @param a   first set for the cartesian product
     * @param b   second set for the cartesian product
     * @param <A> the type of the elements in set {@code a}
     * @param <B> the type of the elements in set {@code b}
     * @return the cartesian product of the two sets {@code a} and {@code b}
     */
    public static <A, B> Set<Pair<A, B>> cartesianProduct(final Set<A> a, final Set<B> b) {
        final Set<Pair<A, B>> result = new HashSet<>(a.size() * b.size());
        for (final A i : a) {
            for (final B j : b) {
                result.add(Pair.of(i, j));
            }
        }

        return result;
    }
}
