package city.sane.akka.p2p.transport.event;

import akka.actor.Address;
import city.sane.akka.p2p.transport.P2PTransport;

import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * This event is sent to the Akka system when a {@link P2PTransport} is started. The event contains the addresses for which
 * {@link P2PTransport} accepts requests.
 */
public class P2PTransportListenEvent implements P2PTransportLifecycleEvent {
    private final Set<Address> listenAddresses;

    public P2PTransportListenEvent(Set<Address> listenAddresses) {
        this.listenAddresses = requireNonNull(listenAddresses);
    }

    public P2PTransportListenEvent(Address listenAddress) {
        this(Set.of(listenAddress));
    }

    @Override
    public int logLevel() {
        return 3; // Logging.InfoLevel()
    }

    @Override
    public String toString() {
        Set<String> printableAddresses = listenAddresses.stream().map(Address::toString).collect(Collectors.toSet());
        return "P2PTransport now listens on addresses: " + String.join(", ", printableAddresses);
    }
}
