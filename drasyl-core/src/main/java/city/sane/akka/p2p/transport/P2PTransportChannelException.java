package city.sane.akka.p2p.transport;

/**
 * A P2PTransportChannelException is thrown by the {@link P2PTransportChannel} when errors occur.
 */
public class P2PTransportChannelException extends Exception {
    public P2PTransportChannelException(Throwable cause) {
        super(cause);
    }

    public P2PTransportChannelException(String message) {
        super(message);
    }

    public P2PTransportChannelException(String message, Throwable cause) {
        super(message, cause);
    }
}
