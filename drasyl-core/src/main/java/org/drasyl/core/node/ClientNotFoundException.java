package org.drasyl.core.node;

import org.drasyl.core.models.DrasylException;

/**
 * This exception is thrown if the desired client was not found.
 */
public class ClientNotFoundException extends DrasylException {
    public ClientNotFoundException(Throwable cause) {
        super(cause);
    }

    public ClientNotFoundException(String cause) {
        super(cause);
    }
}
