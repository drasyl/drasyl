package city.sane.akka.p2p.transport.relay;

import akka.actor.ExtendedActorSystem;
import city.sane.akka.p2p.transport.P2PTransport;
import city.sane.akka.p2p.transport.retry.DelayedSwitchRetryStrategy;
import city.sane.akka.p2p.transport.retry.RetryP2PTransportChannel;
import com.typesafe.config.Config;

import java.net.URISyntaxException;

/**
 * Defines a channel, which deliver all messages between actors via a single relay server.
 * The channel will try to restart the connection on failure.
 */
public class RetryRelayP2PTransportChannel extends RetryP2PTransportChannel {

    RetryRelayP2PTransportChannel(P2PTransport transport,
                                  RelayP2PTransportChannelProperties properties,
                                  ExtendedActorSystem system) {
        super(new DelayedSwitchRetryStrategy(properties, () -> new RelayP2PTransportChannel(
                transport,
                properties,
                system))
        );
    }

    // constructor call in P2PTransport::new from class reflection via className in config
    @SuppressWarnings("unused")
    public RetryRelayP2PTransportChannel(String system,
                                         Config config,
                                         P2PTransport transport,
                                         ExtendedActorSystem actorSystem) throws URISyntaxException {
        this(transport, new RelayP2PTransportChannelProperties(system, config),
                actorSystem);
    }
}
