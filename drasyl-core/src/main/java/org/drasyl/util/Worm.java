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

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * This class wraps an object of type {@code T} and serves as a write once read many (worm) memory.
 *
 * @param <T> the type of the worm object
 */
@SuppressWarnings({ "java:S2789", "OptionalUsedAsFieldOrParameterType", "OptionalAssignedToNull" })
public class Worm<T> {
    private Optional<T> value;

    Worm() {
    }

    Worm(final T value) {
        this.value = Optional.ofNullable(value);
    }

    /**
     * If a value is present, returns the value, otherwise throws {@code NoSuchElementException}.
     *
     * @return the value described by this {@code Worm}
     * @throws NoSuchElementException if no value is present
     */
    public T get() {
        synchronized (this) {
            if (value == null) {
                throw new NoSuchElementException("No value present");
            }
            else {
                return value.orElse(null);
            }
        }
    }

    /**
     * Sets the value to {@code value}.
     *
     * @param value the new value
     * @throws IllegalStateException if value is already present
     */
    @SuppressWarnings("java:S2886")
    public void set(final T value) {
        if (!trySet(value)) {
            throw new IllegalStateException("Value already present");
        }
    }

    /**
     * Sets the value to {@code value} if no value is present yet.
     *
     * @param value the new value
     * @return returns {@code true}, if value has been set to {@code value}
     */
    public boolean trySet(final T value) {
        synchronized (this) {
            if (this.value == null) {
                this.value = Optional.ofNullable(value);
                return true;
            }
            else {
                return false;
            }
        }
    }

    /**
     * If no value has been set yet, it will be set to the return of {@code supplier}. Otherwise the
     * existing value is returned.
     *
     * @param supplier is used to compute the value
     * @return the value of this {@code Worm}
     */
    public synchronized T getOrCompute(final Supplier<T> supplier) {
        synchronized (this) {
            if (value == null) {
                value = Optional.ofNullable(supplier.get());
            }

            return value.orElse(null);
        }
    }

    /**
     * If no value has been set yet, it will be set to {@code value}. Otherwise the existing value
     * is returned.
     *
     * @param value is used to set the value
     * @return the value of this {@code Worm}
     */
    public synchronized T getOrSet(final T value) {
        synchronized (this) {
            if (this.value == null) {
                this.value = Optional.ofNullable(value);
            }

            return this.value.orElse(null);
        }
    }

    /**
     * If a value is present, returns {@code true}, otherwise {@code false}.
     *
     * @return {@code true} if a value is present, otherwise {@code false}
     */
    public boolean isPresent() {
        return value != null;
    }

    /**
     * If a value is not present, returns {@code true}, otherwise {@code false}.
     *
     * @return {@code true} if a value is not present, otherwise {@code false}
     */
    public boolean isEmpty() {
        return value == null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Worm<?> worm = (Worm<?>) o;
        return Objects.deepEquals(value, worm.value);
    }

    @Override
    public String toString() {
        return "Worm{" +
                "value=" + value +
                '}';
    }

    /**
     * Creates a new {@code Worm} object with not set value.
     *
     * @param <T> the type of the {@code Worm} object
     * @return the {@code Worm} object
     */
    public static <T> Worm<T> of() {
        return new Worm<>();
    }

    /**
     * Creates a new {@code Worm} object with value set to {@code value}.
     *
     * @param <T> the type of the {@code Worm} object
     * @return the {@code Worm} object
     */
    public static <T> Worm<T> of(final T value) {
        return new Worm<>(value);
    }
}
