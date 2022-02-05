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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.LongSupplier;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * A {@link Map} that expires entries based on oldest age (when maximum size has been exceeded),
 * write time, or last access time.
 * <p>
 * The expiration policy is only enforced on map access. There will be no automatic expiration
 * handling running in a background thread or similar. For performance reasons the policy is not
 * enforced on every single access, but only once every "expiration window" ({@link
 * Math}.max(expireAfterWrite, expireAfterAccess)). Therefore, it may happen that entries are kept
 * in the map up to the double expiration window length.
 * <p>
 * This data structure is not thread-safe!
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public class ExpiringMap<K, V> implements Map<K, V> {
    private final LongSupplier currentTimeProvider;
    private final Map<K, V> map;
    private final SortedSet<ExpiringEntry<K, V>> sortedEntries;
    private final long maximumSize;
    private final long expireAfterWrite;
    private final long expireAfterAccess;
    private long lastExpirationExecution;

    ExpiringMap(final LongSupplier currentTimeProvider,
                final long maximumSize,
                final long expireAfterWrite,
                final long expireAfterAccess,
                final Map<K, V> map, final SortedSet<ExpiringEntry<K, V>> sortedEntries) {
        this.currentTimeProvider = requireNonNull(currentTimeProvider);
        if (maximumSize == 0) {
            throw new IllegalArgumentException("maximumSize must be -1 or positive.");
        }
        this.maximumSize = maximumSize;
        if (expireAfterWrite == -1 && expireAfterAccess == -1) {
            throw new IllegalArgumentException("expireAfterWrite and expireAfterAccess can not both be -1.");
        }
        this.expireAfterWrite = expireAfterWrite;
        this.expireAfterAccess = expireAfterAccess;
        this.map = requireNonNull(map);
        this.sortedEntries = requireNonNull(sortedEntries);
        this.lastExpirationExecution = currentTimeProvider.getAsLong();
    }

    /**
     * @param maximumSize       maximum number of entries that the map should contain. On overflow,
     *                          first elements based on expiration policy are removed. {@code -1}
     *                          deactivates a size limitation.
     * @param expireAfterWrite  time in milliseconds after which elements are automatically removed
     *                          from the map after being added. {@code -1} deactivates this
     *                          expiration policy.
     * @param expireAfterAccess time in milliseconds after which elements are automatically removed
     *                          from the map after last access. {@code -1} deactivates this
     *                          expiration  policy. Keep in mind, that only {@link #get(Object)} is
     *                          treated as an element access.
     * @throws IllegalArgumentException if {@code maximumSize} is {@code 0}, or {@code
     *                                  expireAfterWrite} and {@code expireAfterAccess} are both
     *                                  {@code -1}.
     */
    public ExpiringMap(final long maximumSize,
                       final long expireAfterWrite,
                       final long expireAfterAccess) {
        this(System::currentTimeMillis, maximumSize, expireAfterWrite, expireAfterAccess, new HashMap<>(), new TreeSet<>());
    }

    @Override
    public int size() {
        // we return something, check for expired entries
        removeExpiredEntries();

        return map.size();
    }

    @Override
    public boolean isEmpty() {
        // we return something, check for expired entries
        removeExpiredEntries();

        return map.isEmpty();
    }

    @Override
    public boolean containsKey(final Object key) {
        // we return something, check for expired entries
        removeExpiredEntries();

        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(final Object value) {
        // we return something, check for expired entries
        removeExpiredEntries();

        return map.containsValue(value);
    }

    @Override
    public V get(final Object key) {
        final V value = map.get(key);
        if (expireAfterAccess != -1 && value != null) {
            // remove existing entry, so it can be added below with updated access time
            final ExpiringEntry<K, V> entry = new ExpiringEntry<>(currentTimeProvider, (K) key, value);
            sortedEntries.remove(entry);
            sortedEntries.add(entry);
        }

        // we return something, check for expired entries
        removeExpiredEntries();

        return map.get(key);
    }

    @Override
    public V put(final K key, final V value) {
        final ExpiringEntry<K, V> entry = new ExpiringEntry<>(currentTimeProvider, key, value);
        if (expireAfterWrite != -1) {
            // remove existing entry, so it can be added below with updated write time
            sortedEntries.remove(entry);
        }
        sortedEntries.add(entry);

        // we add something, check capacity
        evictExceedingEntries();

        // we return something, check for expired entries
        removeExpiredEntries();

        return map.put(key, value);
    }

    @Override
    public V remove(final Object key) {
        // we return something, check for expired entries
        removeExpiredEntries();

        return map.remove(key);
    }

    @Override
    public void putAll(final Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        map.clear();
        sortedEntries.clear();
    }

    @Override
    public Set<K> keySet() {
        // we return something, check for expired entries
        removeExpiredEntries();

        return map.keySet();
    }

    @Override
    public Collection<V> values() {
        // we return something, check for expired entries
        removeExpiredEntries();

        return map.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        // we return something, check for expired entries
        removeExpiredEntries();

        return map.entrySet();
    }

    private void evictExceedingEntries() {
        if (maximumSize != -1 && sortedEntries.size() > maximumSize) {
            final ExpiringEntry<K, V> first = sortedEntries.first();
            map.remove(first.key());
            sortedEntries.remove(first);
        }
    }

    private void removeExpiredEntries() {
        final long currentTime = currentTimeProvider.getAsLong();

        if (expireAfterWrite != -1 && currentTime - lastExpirationExecution < expireAfterWrite ||
                expireAfterAccess != -1 && currentTime - lastExpirationExecution < expireAfterAccess) {
            // perform this task only once per "expiration window"
            return;
        }

        lastExpirationExecution = currentTime;

        while (!sortedEntries.isEmpty()) {
            final ExpiringEntry<K, V> first = sortedEntries.first();
            if (first.isExpired(currentTime, expireAfterWrite, expireAfterAccess)) {
                map.remove(first.key());
                sortedEntries.remove(first);
            }
            else {
                break;
            }
        }
    }

    /**
     * Internal helper class representing an expiring entry of an {@link ExpiringMap}.
     * <p>
     * This is an immutable object.
     *
     * @param <K> the key type maintained by the corresponding {@link ExpiringMap}
     * @param <V> the value type mainted by the corresponding {@link ExpiringMap}
     */
    static class ExpiringEntry<K, V> implements Comparable<ExpiringEntry<K, V>> {
        private final long timestamp;
        private final K key;
        private final V value;

        ExpiringEntry(final long timestamp,
                      final K key,
                      final V value) {
            this.timestamp = requirePositive(timestamp);
            this.key = requireNonNull(key);
            this.value = value;
        }

        ExpiringEntry(final LongSupplier currentTimeProvider,
                      final K key,
                      final V value) {
            this(currentTimeProvider.getAsLong(), key, value);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ExpiringEntry<?, ?> that = (ExpiringEntry<?, ?>) o;
            return Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key);
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }

        @Override
        public int compareTo(final ExpiringEntry<K, V> o) {
            // java's SortedSet implementations cannot sort elements by the "timestamp" attribute and
            // distinguish elements by the "key" attribute. as soon as two elements have the same value
            // in the attribute used for sorting, they are considered to be equal which would result in
            // the removal of the "wrong" element. for this reason, we return 0 only if both key are
            // equal. this comes at the cost of deterministic sorting, but in our use case this should
            // (hopefully!) not be a problem.
            if (key.equals(o.key)) {
                return 0;
            }
            else {
                return timestamp < o.timestamp ? -1 : 1;
            }
        }

        K key() {
            return key;
        }

        boolean isExpired(final long currentTime,
                          final long expireAfterWrite,
                          final long expireAfterAccess) {
            return expireAfterAccess != -1 && currentTime - timestamp >= expireAfterAccess ||
                    expireAfterWrite != -1 && currentTime - timestamp >= expireAfterWrite;
        }
    }
}
