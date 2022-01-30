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

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNull;

/**
 * A map that expires entries based on oldest age (when maximum size has been exceeded), write time,
 * or last access time.
 *
 * @param <E> the type of elements maintained by this set
 */
public class ExpiringSet<E> implements Set<E> {
    private final Map<E, Boolean> map;

    public ExpiringSet(final Map<E, Boolean> map) {
        this.map = requireNonNull(map);
    }

    /**
     * @param maximumSize       maximum number of entries that the set should contain. On overflow,
     *                          oldest elements are removed
     * @param expireAfterWrite  time in milliseconds after which elements are automatically removed
     *                          from the set after being added
     * @param expireAfterAccess time in milliseconds after which elements are automatically removed
     *                          from the set after last access
     */
    public ExpiringSet(final long maximumSize,
                       final long expireAfterWrite,
                       final long expireAfterAccess) {
        this(new ExpiringMap<>(maximumSize, expireAfterWrite, expireAfterAccess));
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean contains(final Object o) {
        return map.containsKey(o);
    }

    @Override
    public Iterator<E> iterator() {
        return map.keySet().iterator();
    }

    @Override
    public Object[] toArray() {
        return map.keySet().toArray();
    }

    @Override
    public <T> T[] toArray(final T[] a) {
        return map.keySet().toArray(a);
    }

    @Override
    public boolean add(final E e) {
        return map.put(e, TRUE) == null;
    }

    @Override
    public boolean remove(final Object o) {
        return map.remove(o);
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(final Collection<? extends E> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        map.clear();
    }
}
