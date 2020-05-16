package org.drasyl.peer.connection.superpeer;

import org.drasyl.DrasylException;

/**
 * A SuperPeerClientException is thrown by the {@link SuperPeerClient} when errors occur.
 */
public class SuperPeerClientException extends DrasylException {
    public SuperPeerClientException(Throwable cause) {
        super(cause);
    }

    public SuperPeerClientException(String cause) {
        super(cause);
    }
}
