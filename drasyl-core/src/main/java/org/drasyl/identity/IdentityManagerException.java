package org.drasyl.identity;

import org.drasyl.DrasylException;

/**
 * A IdentityManagerException is thrown by the {@link IdentityManager} when errors occur.
 */
public class IdentityManagerException extends DrasylException {
    public IdentityManagerException(Throwable cause) {
        super(cause);
    }

    public IdentityManagerException(String cause) {
        super(cause);
    }
}
