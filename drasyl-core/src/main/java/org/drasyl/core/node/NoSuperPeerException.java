package org.drasyl.core.node;

import org.drasyl.core.models.DrasylException;

/**
 * This exception is thrown if the node does not have a super peer.
 */
public class NoSuperPeerException extends DrasylException {
    public NoSuperPeerException(Throwable cause) {
        super(cause);
    }

    public NoSuperPeerException(String cause) {
        super(cause);
    }
}
