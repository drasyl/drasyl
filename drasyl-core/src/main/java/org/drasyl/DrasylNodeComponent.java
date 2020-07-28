package org.drasyl;

/**
 * This interface describes components of the {@link org.drasyl.DrasylNode}.
 */
public interface DrasylNodeComponent extends AutoCloseable {
    /**
     * Starts the component.
     *
     * @throws DrasylException if error occurs during opening
     */
    void open() throws DrasylException;

    /**
     * Stops the component.
     */
    @Override
    void close();
}
