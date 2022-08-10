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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;

/**
 * A {@link SortedSet} where elements are ordered using their
 * {@linkplain Comparable natural ordering}, or by a {@link Comparator} provided at set creation
 * time, depending on which constructor is used.
 * <p>
 * Unlike {@link java.util.TreeSet}, ordering does not have to be <i>consistent with equals</i>. In
 * other words, {@link java.util.TreeSet} implies that objects are equal if
 * {@link Comparable#compareTo(Object)} returns {@code 0}. In contrast,
 * {@link InconsistentSortedSet} uses {@link Object#equals(Object)} to determine equality.
 * <p>
 * <strong>Here an example which cannot be handled by {@link java.util.TreeSet} but from
 * {@link InconsistentSortedSet}:</strong><br>Assume the DAO class {@code Person} with attributes
 * {@code name} and {@code height}. Now assume a data structure containing a set of {@code Person}s
 * where each {@code name} should be unique, and the elements should be sorted by {@code height}.
 * <p>
 * Internally, this class is backed by a {@link HashSet} and {@link ArrayUtil}, so the memory
 * consumption of this class is probably equal to the sum of these two classes.
 *
 * @param <E> the type of elements maintained by this set
 */
public class InconsistentSortedSet<E> implements SortedSet<E> {
    private final Set<E> internalSet;
    private final List<E> internalList;
    private final Comparator<? super E> comparator;

    /**
     * Constructs a new, empty set; the backing {@link HashSet} and {@link ArrayList} instances have
     * the initial capacity specified by their respective implementation.
     */
    public InconsistentSortedSet() {
        internalSet = new HashSet<>();
        internalList = new ArrayList<>();
        comparator = null;
    }

    /**
     * Constructs a new set containing the elements in the specified collection.
     *
     * @param c the collection whose elements are to be placed into this set
     * @throws NullPointerException if the specified collection is {@code null}
     */
    public InconsistentSortedSet(final Collection<? extends E> c) {
        this();
        addAll(c);
    }

    /**
     * Constructs a new, empty set; the backing {@link HashSet} and {@link ArrayList} instances have
     * the specified initial capacity.
     *
     * @param initialCapacity the initial capacity of the hash set and array list
     */
    @SuppressWarnings("unused")
    public InconsistentSortedSet(final int initialCapacity) {
        internalSet = new HashSet<>(initialCapacity);
        internalList = new ArrayList<>(initialCapacity);
        comparator = null;
    }

    /**
     * Constructs a new, empty set, ordered according to the given comparator.  All keys inserted
     * into the map must be <em>mutually comparable</em> by the given comparator:
     * {@code comparator.compare(k1, k2)} must not throw a {@code ClassCastException} for any
     * elements {@code k1} and {@code k2} in the set. If the user attempts to put an element into
     * the set that violates this constraint, the {@code put(Object key, Object value)} call will
     * throw a {@code ClassCastException}.
     *
     * @param comparator the comparator that will be used to order this set. If {@code null}, the
     *                   {@linkplain Comparable natural ordering} of the keys will be used.
     */
    @SuppressWarnings("unused")
    public InconsistentSortedSet(final Comparator<? super E> comparator) {
        internalSet = new HashSet<>();
        internalList = new ArrayList<>();
        this.comparator = comparator;
    }

    @Override
    public int size() {
        return internalSet.size();
    }

    @Override
    public boolean isEmpty() {
        return internalSet.isEmpty();
    }

    @Override
    public boolean contains(final Object o) {
        return internalSet.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return internalList.iterator();
    }

    @Override
    public Object[] toArray() {
        return internalList.toArray();
    }

    @SuppressWarnings("SuspiciousToArrayCall")
    @Override
    public <T> T[] toArray(final T[] a) {
        return internalList.toArray(a);
    }

    @Override
    public boolean add(final E e) {
        final boolean modified = internalSet.add(e);
        if (modified) {
            internalList.add(e);
            internalList.sort(comparator);
        }
        return modified;
    }

    @SuppressWarnings("java:S2250")
    @Override
    public boolean remove(final Object o) {
        final boolean modified = internalSet.remove(o);
        if (modified) {
            internalList.remove(o);
        }
        return modified;
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        return internalSet.containsAll(c);
    }

    @Override
    public boolean addAll(final Collection<? extends E> c) {
        boolean modified = false;
        for (final E e : c) {
            if (internalSet.add(e)) {
                modified = true;
                internalList.add(e);
            }
        }
        if (modified) {
            internalList.sort(comparator);
        }
        return modified;
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        final boolean modified = internalSet.retainAll(c);
        if (modified) {
            internalList.retainAll(c);
        }
        return modified;
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        final boolean modified = internalSet.removeAll(c);
        if (modified) {
            internalList.removeAll(c);
        }
        return modified;
    }

    @Override
    public void clear() {
        internalSet.clear();
        internalList.clear();
    }

    @Override
    public Comparator<? super E> comparator() {
        return null;
    }

    @Override
    public SortedSet<E> subSet(final E fromElement, final E toElement) {
        final int fromIndex = internalList.indexOf(fromElement);
        final int toIndex = internalList.indexOf(toElement);
        if (fromIndex == -1) {
            throw new IllegalArgumentException("fromElement lies outside the bounds of the range.");
        }
        else if (toIndex == -1) {
            throw new IllegalArgumentException("toIndex lies outside the bounds of the range.");
        }
        else if (fromIndex > toIndex) {
            throw new IllegalArgumentException("fromElement is greater than toElement.");
        }
        return new InconsistentSortedSet<>(internalList.subList(fromIndex, toIndex));
    }

    @Override
    public SortedSet<E> headSet(final E toElement) {
        final int toIndex = internalList.indexOf(toElement);
        return new InconsistentSortedSet<>(internalList.subList(0, toIndex));
    }

    @Override
    public SortedSet<E> tailSet(final E fromElement) {
        final int fromIndex = internalList.indexOf(fromElement);
        if (fromIndex == -1) {
            throw new IllegalArgumentException("fromElement lies outside the bounds of the range.");
        }
        return new InconsistentSortedSet<>(internalList.subList(fromIndex, internalList.size()));
    }

    @SuppressWarnings("java:S1166")
    @Override
    public E first() {
        try {
            return internalList.get(0);
        }
        catch (final IndexOutOfBoundsException e) {
            throw new NoSuchElementException();
        }
    }

    @SuppressWarnings("java:S1166")
    @Override
    public E last() {
        try {
            return internalList.get(internalList.size() - 1);
        }
        catch (final IndexOutOfBoundsException e) {
            throw new NoSuchElementException();
        }
    }
}
