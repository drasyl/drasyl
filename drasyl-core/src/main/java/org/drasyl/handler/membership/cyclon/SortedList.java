package org.drasyl.handler.membership.cyclon;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

public class SortedList<E> extends AbstractList<E> {
    private final ArrayList<E> internalList = new ArrayList<>();
    private final TreeSet<E>  internalList2 = new TreeSet<>();

    public SortedList() {
    }

    public SortedList(final List<E> c) {
        internalList.addAll(c);
        internalList2.addAll(c);
        System.out.println();
    }

    // Note that add(E e) in AbstractList is calling this one
    @Override
    public void add(final int position, final E e) {
        internalList.add(e);
        Collections.sort(internalList, null);
        internalList2.add(e);
        System.out.println();
    }

    @Override
    public E get(final int i) {
        return internalList.get(i);
    }

    public E getLast() {
        return internalList.get(size() - 1);
    }

    @Override
    public int size() {
        final int size = internalList.size();
        final int size2 = internalList2.size();
        return size;
    }

    @Override
    public boolean remove(final Object o) {
        final boolean remove = internalList.remove(o);
        final boolean remove2 = internalList2.remove(o);
        return remove;
    }
}
