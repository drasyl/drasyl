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
import java.util.Set;

/**
 * A map in which more than one value may be associated with and returned for a given key
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public interface Multimap<K, V> {
    /**
     * Associates the specified {@code value} with the specified {@code key} in this map.
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return {@code true} if the specified {@code value} could be associated with the specified
     * {@code key}
     * @throws NullPointerException if {@code key} is {@code null}
     */
    boolean put(K key, V value);

    /**
     * Removes the specified {@code value} from the specified {@code key}. If no value left, the
     * associated {@code key} will be removed from the map.
     *
     * @param key   key with which the specified value is associated
     * @param value value expected to be associated with the specified key
     * @return {@code true} if the specified {@code value} could be removed from the specified
     * {@code key}
     * @throws NullPointerException if {@code key} is {@code null}
     */
    boolean remove(K key, V value);

    /**
     * Returns the values that are associated to the specified {@code key}. Returns a empty
     * {@link Collection} if currently no values are assosicated to the {@code key}.
     *
     * @param key the key whose associated values should be returned
     * @return the values that are associated to the specified {@code key}. Returns a empty
     * {@link Collection} if currently no values are assosicated to the {@code key}.
     * @throws NullPointerException if {@code key} is {@code null}
     */
    Collection<V> get(Object key);

    /**
     * Returns a {@link Set} with all the keys contained in this map.
     *
     * @return {@link Set} with all the keys contained in this map
     */
    Set<K> keySet();

    /**
     * Returns {@code true} if this multimap contains no entries.
     *
     * @return {@code true} if this multimap contains no entries
     */
    boolean isEmpty();

    /**
     * Removes all entries from this multimap.
     */
    void clear();
}
