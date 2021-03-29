/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.pipeline.serialization;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.drasyl.serialization.NullSerializer;
import org.drasyl.serialization.Serializer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNull;

/**
 * Holds a {@link Map} with all available {@link Serializer}s and the classes each serializer should
 * be used for. This class is used by {@link org.drasyl.pipeline.serialization.MessageSerializer} to
 * (de)serialize message objects when communicating with remote nodes.
 *
 * <p>Each serializer is applied for objects of the assigned class, all subclasses, and all
 * implementations.</p>
 *
 * <p>Each received message includes the class name of the content. Normally, we would have to
 * invoke the method {@link Class#forName(String)} to load the associated class to find a serializer
 * for the class, any subclass, or implementation. However, this method invocation poses a security
 * risk because we cannot control the class name received from the remote node. For this reason,
 * when adding a {@link Serializer}, the classpath is scanned in advance for subclasses and
 * implementations. These results will then later used when a message should be deserialized. This
 * approach allows us to load only classes with valid {@link Serializer}s.</p>
 *
 * @see org.drasyl.pipeline.serialization.MessageSerializer
 */
public class Serialization {
    protected static final NullSerializer NULL_SERIALIZER = new NullSerializer();
    private static final int EXPECTED_VALUES_PER_KEY = 16; // 2^4
    private static final Logger LOG = LoggerFactory.getLogger(Serialization.class);
    private static Multimap<String, String> subclasses;

    static {
        buildInheritanceGraph();
    }

    private final Map<String, Serializer> mapping;
    private final ReadWriteLock lock;

    Serialization(final ReadWriteLock lock, final Map<String, Serializer> mapping) {
        this.lock = requireNonNull(lock);
        this.mapping = requireNonNull(mapping);
    }

    public Serialization(final Map<String, Serializer> serializers,
                         final Map<Class<?>, String> bindings) {
        this.lock = new ReentrantReadWriteLock(true);
        this.mapping = new HashMap<>();

        for (final Entry<Class<?>, String> entry : bindings.entrySet()) {
            final Class<?> clazz = entry.getKey();
            final String serializerName = entry.getValue();
            final Serializer serializer = serializers.get(serializerName);

            addSerializer(clazz, serializer);
        }
    }

    /**
     * Rebuilds the inheritance graph, but keeps the old values.
     */
    public static synchronized void buildInheritanceGraph() {
        LOG.debug("Scan classpath and build inheritance graph. This can take some time...");

        // scan classpath
        final ScanResult scanResult = new ClassGraph()
                .enableClassInfo()
                .ignoreClassVisibility()
                .enableSystemJarsAndModules()
                .removeTemporaryFilesAfterScan()
                .scan();

        // build inheritance graph
        final int expectedKeys = scanResult.getAllClasses().size();
        final Multimap<String, String> newSubclasses = MultimapBuilder.hashKeys(expectedKeys)
                .hashSetValues(EXPECTED_VALUES_PER_KEY).build();
        // does not include "java.lang.Object"
        for (final ClassInfo classInfo : scanResult.getAllClasses()) {
            final String className = classInfo.getName().intern();

            for (final ClassInfo subclass : classInfo.getSubclasses()) {
                newSubclasses.put(className, subclass.getName().intern());
            }

            if (classInfo.isInterface()) {
                for (final ClassInfo implementation : classInfo.getClassesImplementing()) {
                    newSubclasses.put(className, implementation.getName().intern());
                }
            }
        }
        subclasses = newSubclasses;
        LOG.debug("Inheritance graph built!");
    }

    /**
     * Returns the configured {@link Serializer} for the given {@code clazzName}. The configured
     * {@link Serializer} is used if the configured class `isAssignableFrom` from the {@code clazz},
     * i.e. the configured class is a super class or implemented interface. In case of ambiguity it
     * is primarily using the most specific configured class, and secondly the entry configured
     * first.
     *
     * @param clazzName name of class for which a serializer should be searched for
     * @return serializer for given clazz or {@code null} if nothing found
     */
    public Serializer findSerializerFor(final String clazzName) {
        if (isNullOrEmpty(clazzName)) {
            return NULL_SERIALIZER;
        }
        else {
            Serializer rtn = mapping.get(clazzName);

            if (rtn == null) {
                rtn = mapping.get("java.lang.Object"); // default
            }

            return rtn;
        }
    }

    /**
     * Adds a {@code serializer} as serializer for objects of type {@code clazz}.
     *
     * @param clazz      class the serializer should be used for
     * @param serializer the serializer
     */
    public void addSerializer(final Class<?> clazz,
                              final Serializer serializer) {
        try {
            lock.writeLock().lock();
            final String clazzName = clazz.getName().intern();

            mapping.put(clazzName, serializer);
            subclasses.get(clazzName).forEach(expandClass -> mapping.put(expandClass, serializer));
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes all serializers for objects of type {@code clazz}.
     *
     * @param clazz class the serializer should be removed for
     */
    public void removeSerializer(final Class<?> clazz) {
        try {
            lock.writeLock().lock();
            final String clazzName = clazz.getName().intern();

            mapping.remove(clazzName);
            subclasses.get(clazzName).forEach(mapping::remove);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes given serializer from
     *
     * @param serializer the serializer that should be removed
     */
    public void removeSerializer(final Serializer serializer) {
        try {
            lock.writeLock().lock();

            final Collection<Serializer> values = mapping.values();
            for (; ; ) {
                if (!values.remove(serializer)) {
                    break;
                }
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }
}
