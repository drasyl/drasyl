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
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

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
    private final long maximumSize;
    private final long expireAfterWrite;
    private final long expireAfterAccess;
    private final BiConsumer<K, V> removalListener;
    private final Map<K, ExpiringEntry<K, V>> map;
    private final SortedSet<ExpiringEntry<K, V>> sortedEntries;
    private long lastExpirationExecution;

    ExpiringMap(final LongSupplier currentTimeProvider,
                final long maximumSize,
                final long expireAfterWrite,
                final long expireAfterAccess,
                final BiConsumer<K, V> removalListener,
                final Map<K, ExpiringEntry<K, V>> map,
                final SortedSet<ExpiringEntry<K, V>> sortedEntries) {
        this.currentTimeProvider = requireNonNull(currentTimeProvider);
        this.removalListener = removalListener;
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
     * @param removalListener   called every time an entry is removed from the map. First passed
     *                          argument is the entry key, second argument the value.
     * @throws IllegalArgumentException if {@code maximumSize} is {@code 0}, or {@code
     *                                  expireAfterWrite} and {@code expireAfterAccess} are both
     *                                  {@code -1}.
     */
    public ExpiringMap(final long maximumSize,
                       final long expireAfterWrite,
                       final long expireAfterAccess,
                       final BiConsumer<K, V> removalListener) {
        this(System::currentTimeMillis, maximumSize, expireAfterWrite, expireAfterAccess, removalListener, new HashMap<>(), new TreeSet<>());
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
        this(maximumSize, expireAfterWrite, expireAfterAccess, (k, v) -> {
        });
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
        final ExpiringEntry<K, V> existingEntry = map.get(key);

        if (existingEntry != null && expireAfterAccess != -1) {
            // remove existing entry, so it can be added below with updated time
            map.remove(key);
            sortedEntries.remove(existingEntry);

            final ExpiringEntry<K, V> newEntry = new ExpiringEntry<>(currentTimeProvider, existingEntry.key, existingEntry.value);
            map.put((K) key, newEntry);
            sortedEntries.add(newEntry);
        }

        // we return something, check for expired entries
        removeExpiredEntries();

        return existingEntry != null ? existingEntry.value : null;
    }

    @Override
    public V put(final K key, final V value) {
        final ExpiringEntry<K, V> existingEntry = map.get(key);

        if (existingEntry != null && expireAfterWrite != -1) {
            // remove existing entry, so it can be added below with updated time
            map.remove(key);
            sortedEntries.remove(existingEntry);
        }

        final ExpiringEntry<K, V> newEntry = new ExpiringEntry<>(currentTimeProvider, key, value);
        map.put(key, newEntry);
        sortedEntries.add(newEntry);

        if (existingEntry == null) {
            // we added something, check capacity
            evictExceedingEntries();
        }

        // we return something, check for expired entries
        removeExpiredEntries();

        return existingEntry != null ? existingEntry.value : null;
    }

    @Override
    public V remove(final Object key) {
        // we return something, check for expired entries
        removeExpiredEntries();

        final ExpiringEntry<K, V> removedEntry = map.remove(key);
        return removedEntry != null ? removedEntry.value : null;
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

        return map.values().stream().map(e -> e.value).collect(Collectors.toList());
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException();
    }

    private void evictExceedingEntries() {
        if (maximumSize != -1 && sortedEntries.size() > maximumSize) {
            final ExpiringEntry<K, V> first = sortedEntries.first();
            map.remove(first.key);
            sortedEntries.remove(first);
            removalListener.accept(first.key, first.value);
        }
    }

    private void removeExpiredEntries() {
        if (sortedEntries.isEmpty()) {
            // skip all housekeeping operation if map is empty
            return;
        }

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
                map.remove(first.key);
                sortedEntries.remove(first);
                removalListener.accept(first.key, first.value);
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
    private static class ExpiringEntry<K, V> implements Comparable<ExpiringEntry<K, V>> {
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
            return timestamp == that.timestamp && key.equals(that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(timestamp, key);
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }

        @Override
        public int compareTo(final ExpiringEntry<K, V> o) {
            if (timestamp < o.timestamp) {
                return -1;
            }
            else if (timestamp > o.timestamp) {
                return 1;
            }
            else {
                return Integer.compare(hashCode(), o.hashCode());
            }
        }

        boolean isExpired(final long currentTime,
                          final long expireAfterWrite,
                          final long expireAfterAccess) {
            return expireAfterAccess != -1 && currentTime - timestamp >= expireAfterAccess ||
                    expireAfterWrite != -1 && currentTime - timestamp >= expireAfterWrite;
        }
    }
}
