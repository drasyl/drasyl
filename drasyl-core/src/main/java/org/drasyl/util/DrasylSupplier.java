package org.drasyl.util;

import org.drasyl.DrasylException;

/**
 * {@link java.util.function.Supplier} that can throw a {@link org.drasyl.DrasylException}.
 *
 * @param <T> the type of results supplied by this supplier
 */
@FunctionalInterface
public interface DrasylSupplier<T, E extends DrasylException> {
    /**
     * Gets a result.
     *
     * @return a result
     */
    T get() throws E;
}