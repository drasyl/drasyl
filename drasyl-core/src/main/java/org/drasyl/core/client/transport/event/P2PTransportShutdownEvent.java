package org.drasyl.core.client.transport.event;

import org.drasyl.core.client.transport.P2PTransport;

/**
 * This event is sent to the Akka system when {@link P2PTransport} shuts down.
 */
public class P2PTransportShutdownEvent implements P2PTransportLifecycleEvent {
    @Override
    public int logLevel() {
        return 3; // Logging.InfoLevel()
    }

    @Override
    public String toString() {
        return "P2PTransport shut down";
    }
}
