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

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Represents a pool of objects of type {@code T}. Objects without a reference are deleted from the
 * pool during garbage collection.
 * <p>
 * Inspired by: https://github.com/verhas/intern
 *
 * @param <T> Type of objects in the pool
 */
class WeakPool<T> {
    private final WeakHashMap<T, WeakReference<T>> pool = new WeakHashMap<>();

    /**
     * If there is an object equal to {@code object} then this method returns it. If there is none,
     * this method will return {@code null}.
     *
     * @param object an object
     * @return object equal to {@code object} or {@code null}
     */
    public T get(final T object) {
        final T result;
        final WeakReference<T> reference = pool.get(object);
        if (reference != null) {
            result = reference.get();
        }
        else {
            result = null;
        }
        return result;
    }

    /**
     * This will put {@code object} into the pool and if there was any equal to the new one, it will
     * overwrite the place of the old object one with the new.
     *
     * @param object The object to add to the pool.
     */
    public void put(final T object) {
        pool.put(object, new WeakReference<>(object));
    }
}
