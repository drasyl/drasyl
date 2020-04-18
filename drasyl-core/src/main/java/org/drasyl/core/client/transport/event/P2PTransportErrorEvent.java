package org.drasyl.core.client.transport.event;

import org.drasyl.core.client.transport.P2PTransport;

/**
 * This event is sent to the Akka system when an error occurs in the {@link P2PTransport}.
 */
public class P2PTransportErrorEvent implements P2PTransportLifecycleEvent {
    private final Throwable cause;

    public P2PTransportErrorEvent(Throwable cause) {
        this.cause = cause;
    }

    @Override
    public int logLevel() {
        return 1; // Logging.ErrorLevel()
    }

    @Override
    public String toString() {
        return "P2PTransport error: " + cause;
    }
}
