package city.sane.akka.p2p.transport;

import akka.AkkaException;

/**
 * A P2PTransportException is thrown by the {@link P2PTransport} when errors occur.
 */
public class P2PTransportException extends AkkaException {
    public P2PTransportException(String message, Throwable cause) {
        super(message, cause);
    }

    public P2PTransportException(String message) {
        super(message);
    }
}
