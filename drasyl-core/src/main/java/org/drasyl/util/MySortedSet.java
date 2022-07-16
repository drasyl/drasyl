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
 * A sorted {@link Set} that applies {@linkplain Comparable natural ordering} to its elements.
 * <p>
 * Unlike {@link java.util.TreeSet}, ordering does not have to be <i>consistent with equals</i>.
 *
 * @param <E> the type of elements maintained by this set
 */
public class MySortedSet<E> implements SortedSet<E> {
    private final Set<E> internalSet = new HashSet<>();
    private final List<E> internalList = new ArrayList<>();

    public MySortedSet() {
    }

    public MySortedSet(final Collection<? extends E> c) {
        addAll(c);
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
            internalList.sort(null);
        }
        return modified;
    }

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
            internalList.sort(null);
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
        return new MySortedSet<>(internalList.subList(fromIndex, toIndex));
    }

    @Override
    public SortedSet<E> headSet(final E toElement) {
        final int toIndex = internalList.indexOf(toElement);
        return new MySortedSet<>(internalList.subList(0, toIndex));
    }

    @Override
    public SortedSet<E> tailSet(final E fromElement) {
        final int fromIndex = internalList.indexOf(fromElement);
        if (fromIndex == -1) {
            throw new IllegalArgumentException("fromElement lies outside the bounds of the range.");
        }
        return new MySortedSet<>(internalList.subList(fromIndex, internalList.size()));
    }

    @Override
    public E first() {
        try {
            return internalList.get(0);
        }
        catch (final IndexOutOfBoundsException e) {
            throw new NoSuchElementException();
        }
    }

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
