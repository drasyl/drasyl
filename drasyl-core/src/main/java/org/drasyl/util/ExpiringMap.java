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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A map that expires entries based on oldest age (when maximum size has been exceeded), write time,
 * or last access time.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public class ExpiringMap<K, V> implements Map<K, V> {
    private final Map<K, V> map;

    ExpiringMap(final Map<K, V> map) {
        this.map = requireNonNull(map);
    }

    /**
     * @param maximumSize       maximum number of entries that the map should contain. On overflow,
     *                          oldest entries are removed
     * @param expireAfterWrite  time in milliseconds after which entries are automatically removed
     *                          from the map after being added
     * @param expireAfterAccess time in milliseconds after which entries are automatically removed
     *                          from the map after last access
     */
    public ExpiringMap(final long maximumSize,
                       final long expireAfterWrite,
                       final long expireAfterAccess) {
        final CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();

        if (maximumSize != -1) {
            builder.maximumSize(maximumSize);
        }

        if (expireAfterWrite != -1) {
            builder.expireAfterWrite(expireAfterWrite, MILLISECONDS);
        }

        if (expireAfterAccess != -1) {
            builder.expireAfterAccess(expireAfterAccess, MILLISECONDS);
        }

        final Cache<K, V> cache = builder.build();
        map = cache.asMap();
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
    public boolean containsKey(final Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(final Object value) {
        return map.containsValue(value);
    }

    @Override
    public V get(final Object key) {
        return map.get(key);
    }

    @Override
    public V put(final K key, final V value) {
        return map.put(key, value);
    }

    @Override
    public V remove(final Object key) {
        return map.remove(key);
    }

    @Override
    public void putAll(final Map<? extends K, ? extends V> m) {
        map.putAll(m);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Set<K> keySet() {
        return map.keySet();
    }

    @Override
    public Collection<V> values() {
        return map.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return map.entrySet();
    }
}
