/*
 * Copyright (c) 2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.pipeline;

import org.drasyl.serialization.NullSerializer;
import org.drasyl.serialization.Serializer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.Objects.requireNonNull;

public class Serialization {
    public static final NullSerializer NULL_SERIALIZER = new NullSerializer();
    private final ReadWriteLock lock;
    private final Map<String, Serializer> serializers;
    private final Map<Class<?>, String> bindings;
    private final Map<Class<?>, Optional<Serializer>> mapping;

    Serialization(final ReadWriteLock lock,
                  final Map<String, Serializer> serializers,
                  final Map<Class<?>, String> bindings,
                  final Map<Class<?>, Optional<Serializer>> mapping) {
        this.lock = requireNonNull(lock);
        this.serializers = requireNonNull(serializers);
        this.bindings = requireNonNull(bindings);
        this.mapping = requireNonNull(mapping);
    }

    public Serialization(final Map<String, Serializer> serializers,
                         final Map<Class<?>, String> bindings) {
        this(new ReentrantReadWriteLock(true), serializers, bindings, new HashMap<>());
    }

    /**
     * Returns the configured {@link Serializer} for the given {@code clazz}. The configured {@link
     * Serializer} is used if the configured class `isAssignableFrom` from the {@code object}'s
     * class, i.e. the configured class is a super class or implemented interface. In case of
     * ambiguity it is primarily using the most specific configured class, and secondly the entry
     * configured first.
     *
     * @param o object for which a serializer should be searched for
     * @return serializer for given object or {@code null} if nothing found
     */
    public Serializer findSerializerFor(final Object o) {
        if (o == null) {
            return NULL_SERIALIZER;
        }
        else {
            return findSerializerFor(o.getClass());
        }
    }

    /**
     * Returns the configured {@link Serializer} for the given {@code clazz}. The configured {@link
     * Serializer} is used if the configured class `isAssignableFrom` from the {@code clazz}, i.e.
     * the configured class is a super class or implemented interface. In case of ambiguity it is
     * primarily using the most specific configured class, and secondly the entry configured first.
     *
     * @param clazz class for which a serializer should be searched for
     * @return serializer for given clazz or {@code null} if nothing found
     */
    @SuppressWarnings({ "java:S2789", "OptionalAssignedToNull" })
    public Serializer findSerializerFor(final Class<?> clazz) {
        // cached serializer?
        Optional<Serializer> serializer = mapping.get(clazz);
        if (serializer == null) {
            try {
                lock.writeLock().lock();

                // no! -> do lookup for concrete class
                String name = bindings.get(clazz);

                if (name == null) {
                    // nothing found! -> do lookup for super class or interface
                    final Optional<String> match = bindings.entrySet().stream()
                            .filter(entry -> entry.getKey().isAssignableFrom(clazz))
                            .map(Entry::getValue)
                            .findFirst();
                    if (match.isPresent()) {
                        // found!
                        name = match.get();
                    }
                }

                if (name == null) {
                    // nothing found!
                    serializer = Optional.empty();
                }
                else {
                    serializer = Optional.ofNullable(serializers.get(name));
                }

                mapping.put(clazz, serializer);
            }
            finally {
                lock.writeLock().unlock();
            }
        }

        return serializer.orElse(null);
    }

    /**
     * Adds a new serializer for {@code clazz}.
     *
     * @param name       unique name of the serializer
     * @param clazz      clazz the serializer should be used for
     * @param serializer the serializer
     */
    public void addSerializer(final String name,
                              final Class<?> clazz,
                              final Serializer serializer) {
        try {
            lock.writeLock().lock();

            serializers.put(name, serializer);
            bindings.put(clazz, name);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Adds a new serializer for {@code clazz}.
     *
     * @param clazz      clazz the serializer should be used for
     * @param serializer the serializer
     */
    public void addSerializer(final Class<?> clazz, final Serializer serializer) {
        addSerializer(serializer.getClass().getName(), clazz, serializer);
    }

    public void removeSerializer(final String name) {
        try {
            lock.writeLock().lock();

            serializers.remove(name);
            final Collection<String> values = bindings.values();
            for (; ; ) {
                if (!values.remove(name)) {
                    break;
                }
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void removeSerializer(final Serializer serializer) {
        removeSerializer(serializer.getClass().getName());
    }
}
