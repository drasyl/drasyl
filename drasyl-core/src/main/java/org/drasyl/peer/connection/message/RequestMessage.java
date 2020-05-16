package org.drasyl.peer.connection.message;

/**
 * Messages of this type represent a request. Responses to such a message are of type {@link
 * ResponseMessage}
 *
 * @param <T>
 */
public interface RequestMessage<T extends Message<?>> extends Message<T> {
}
