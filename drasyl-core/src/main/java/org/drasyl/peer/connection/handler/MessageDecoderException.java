package org.drasyl.peer.connection.handler;

import org.drasyl.DrasylException;

/**
 * A MessageDecoderException is thrown by the {@link MessageDecoder} when errors occur.
 */
public class MessageDecoderException extends DrasylException {
    public MessageDecoderException(String message, Throwable cause) {
        super(message, cause);
    }
}
