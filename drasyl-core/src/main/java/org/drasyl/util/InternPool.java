package org.drasyl.util;

/**
 * Represents a pool of unique objects of type {@code T}. Should only be used if objects of type
 * {@code T} are immutable and have a {@link #equals(Object)} implementation, which returns {@code
 * true} only if both objects have the same content.
 * <p>
 * Inspired by: https://github.com/verhas/intern
 *
 * @param <T> Type of objects in the pool
 */
public class InternPool<T> {
    private final WeakPool<T> pool = new WeakPool<>();

    /**
     * Returns a canonical representation for the object.
     * <p>
     * A pool of objects of type {@code T}, initially empty, is maintained privately by this class.
     * <p>
     * When the intern method is invoked, if the pool already contains a object equal to {@code
     * object} as determined by the {@link #equals(Object)} method, then the object from the pool is
     * returned. Otherwise, {@code object} is added to the pool and a reference to {@code object} is
     * returned.
     * <p>
     * It follows that for any two objects {@code a} and {@code b}, {@code intern(a) == intern(b)}
     * is {@code true} if and only if {@code a.equals(b)} is {@code true}.
     *
     * @return a object that has the same contents as {@code object}, but is guaranteed to be from a
     * pool of unique objects of type {@code T}.
     */
    public synchronized T intern(final T object) {
        T res = pool.get(object);
        if (res == null) {
            pool.put(object);
            res = object;
        }
        return res;
    }
}
