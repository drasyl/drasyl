package org.drasyl.peer.connection.handler;

import org.drasyl.DrasylException;

/**
 * A MessageEncoderException is thrown by the {@link MessageEncoder} when errors occur.
 */
public class MessageEncoderException extends DrasylException {
    public MessageEncoderException(String message, Throwable cause) {
        super(message, cause);
    }
}
