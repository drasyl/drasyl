package org.drasyl.core.client;

import org.drasyl.core.models.DrasylException;
import org.drasyl.core.server.NodeServer;

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
