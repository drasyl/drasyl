package org.drasyl.handler.membership.cyclon;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SortedList<E> extends AbstractList<E> {
    private final ArrayList<E> internalList = new ArrayList<>();

    public SortedList() {
    }

    public SortedList(final List<E> c) {
        internalList.addAll(c);
    }

    // Note that add(E e) in AbstractList is calling this one
    @Override
    public void add(final int position, final E e) {
        internalList.add(e);
        Collections.sort(internalList, null);
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
        return internalList.size();
    }

    @Override
    public boolean remove(final Object o) {
        return internalList.remove(o);
    }
}
