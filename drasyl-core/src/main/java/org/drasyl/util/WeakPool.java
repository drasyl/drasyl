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
