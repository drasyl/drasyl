package org.drasyl.core.node.identity;

import org.drasyl.core.models.DrasylException;

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
