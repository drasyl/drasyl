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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
    public static <E> Set<E> merge(Set<E> a, Set<E> b) {
        HashSet<E> result = new HashSet<>(a);
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
    public static <E> Set<E> merge(Set<E> a, E... b) {
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
    public static <E> Set<E> difference(Set<E> a, Collection<E> b) {
        HashSet<E> result = new HashSet<>(a);
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
    public static <E> Set<E> difference(Set<E> a, E... b) {
        return difference(a, Set.of(b));
    }
}
