package city.sane.akka.p2p.transport.event;

import city.sane.akka.p2p.transport.P2PTransport;

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
