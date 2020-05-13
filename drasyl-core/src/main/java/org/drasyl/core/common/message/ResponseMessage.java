package org.drasyl.core.common.message;

/**
 * Messages of this type represent a response to a previously received {@link RequestMessage}.
 *
 * @param <T>
 */
public interface ResponseMessage<R extends RequestMessage<?>, T extends Message<?>> extends Message<T> {
    /**
     * Returns the id of the {@link RequestMessage} to which this response corresponds.
     *
     * @return
     */
    String getCorrespondingId();
}
