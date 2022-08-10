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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Hash table based implementation of the {@code SetMultimap} interface.
 * <p>
 * This data structure is not thread-safe!
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public class HashSetMultimap<K, V> implements SetMultimap<K, V> {
    private final Map<K, Set<V>> map;
    private final Set<V> defaultValue;
    private final Supplier<Set<V>> setSupplier;

    HashSetMultimap(final Map<K, Set<V>> map,
                    final Set<V> defaultValue,
                    final Supplier<Set<V>> setSupplier) {
        this.map = requireNonNull(map);
        this.defaultValue = requireNonNull(defaultValue);
        this.setSupplier = requireNonNull(setSupplier);
    }

    public HashSetMultimap(final int initialMapCapacity, final int initialSetCapacity) {
        this(new HashMap<>(initialMapCapacity), new HashSet<>(), () -> new HashSet<>(initialSetCapacity));
    }

    public HashSetMultimap() {
        this(new HashMap<>(), new HashSet<>(), HashSet::new);
    }

    @Override
    public boolean put(final K key, final V value) {
        map.putIfAbsent(key, setSupplier.get());
        return map.get(key).add(value);
    }

    @Override
    public boolean remove(final K key, final V value) {
        final Set<V> values = map.get(key);
        if (values != null) {
            final boolean removed = values.remove(value);
            if (removed && values.isEmpty()) {
                map.remove(key);
            }
            return removed;
        }
        return false;
    }

    @Override
    public Set<V> get(final Object key) {
        final Set<V> values = map.getOrDefault(key, defaultValue);
        return Set.copyOf(values);
    }

    @Override
    public Set<K> keySet() {
        return map.keySet();
    }
}
