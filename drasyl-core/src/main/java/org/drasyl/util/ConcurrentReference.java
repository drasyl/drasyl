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
package org.drasyl.util;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * This class wraps an object of type {@code T} and serves as a write many read many memory. The
 * advantage of this class is that all methods are lock free until a given condition is met for a
 * write/update operation. Therefore this class requires less locks than an
 * {@link java.util.concurrent.atomic.AtomicReference}.
 *
 * @param <T> the type of the {@link ConcurrentReference} object
 */
@SuppressWarnings({ "java:S2789", "OptionalUsedAsFieldOrParameterType" })
public class ConcurrentReference<T> {
    private final ReentrantReadWriteLock lock;
    private Optional<T> value;

    private ConcurrentReference(final ReentrantReadWriteLock lock, final T value) {
        this.lock = lock;
        this.value = Optional.ofNullable(value);
    }

    private ConcurrentReference() {
        this(new ReentrantReadWriteLock(), null);
    }

    /**
     * Creates a new empty write many read many memory.
     *
     * @param <T> the type of the {@link ConcurrentReference} object
     * @return an empty write many read many memory
     */
    public static <T> ConcurrentReference<T> of() {
        return new ConcurrentReference<>();
    }

    /**
     * Creates a new write many read many memory with the initial value of {@code initialValue}.
     *
     * @param <T> the type of the {@link ConcurrentReference} object
     * @return a write many read many memory with the initial value of {@code initialValue}
     */
    public static <T> ConcurrentReference<T> of(final T initialValue) {
        return new ConcurrentReference<>(new ReentrantReadWriteLock(), initialValue);
    }

    /**
     * Sets the write many read many memory to the return value of {@code supplier} if
     * {@link #value} is {@link Objects#isNull(Object)}. Otherwise, nothing happens. Blocks only if
     * the if {@link #value} is {@link Objects#isNull(Object)} and another thread tries to read or
     * write concurrently the {@code #value}.
     *
     * @param supplier the value supplier
     * @return the value after applying the {@code supplier} or the old value if not {@code null}
     */
    public T computeIfAbsent(final Supplier<T> supplier) {
        return computeOnCondition(Objects::isNull, t -> supplier.get()).orElse(null);
    }

    /**
     * @return returns the internal optional that holds the current value
     */
    public Optional<T> getValue() {
        try {
            lock.readLock().lock();

            return value;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Sets the write many read many memory to the return value of {@code unaryFunction} if
     * {@link #value} fulfills the {@code condition}. Otherwise, nothing happens. Blocks only if
     * {@code condition} is fulfilled and another thread tries to read or write concurrently the
     * {@code #value}.
     *
     * @param condition     the condition to test
     * @param unaryFunction the unary function to apply
     * @return the value after applying the {@code unaryFunction} or the old value if
     * {@code condition} not fulfilled
     */
    public Optional<T> computeOnCondition(final Predicate<T> condition,
                                          final UnaryOperator<T> unaryFunction) {
        boolean upgrade = false;

        try {
            lock.readLock().lock();

            if (condition.test(value.orElse(null))) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                upgrade = true;
                // we check now with write lock (mutex) again for condition
                if (condition.test(value.orElse(null))) {
                    // compute
                    value = Optional.ofNullable(unaryFunction.apply(value.orElse(null)));
                }
            }

            return value;
        }
        finally {
            if (upgrade) {
                lock.writeLock().unlock();
            }
            else {
                lock.readLock().unlock();
            }
        }
    }

    @Override
    public int hashCode() {
        try {
            lock.readLock().lock();

            return value.map(Object::hashCode).orElseGet(() -> Objects.hash(value));
        }
        finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean equals(final Object o) {
        try {
            lock.readLock().lock();

            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ConcurrentReference<?> concurrentReference = (ConcurrentReference<?>) o;
            return Objects.deepEquals(value, concurrentReference.value);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public String toString() {
        try {
            lock.readLock().lock();

            return "ConcurrentReference{" +
                    "value=" + value +
                    '}';
        }
        finally {
            lock.readLock().unlock();
        }
    }
}
