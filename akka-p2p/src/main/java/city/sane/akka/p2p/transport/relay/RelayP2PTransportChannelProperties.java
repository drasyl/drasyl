package city.sane.akka.p2p.transport.relay;

import city.sane.akka.p2p.transport.relay.handler.RelayP2PTransportChannelInitializer;
import com.typesafe.config.Config;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * This class contains all the properties necessary for running a {@link RelayP2PTransportChannelInitializer}.
 */
public class RelayP2PTransportChannelProperties {
    private final URI relayUrl;
    private final Duration joinTimeout;
    private final String systemName;
    private final String channel;

    private final Duration retryDelay;
    private final int maxRetries;
    private final Duration forgetDelay;

    RelayP2PTransportChannelProperties(URI relayUrl,
                                       int relayPort,
                                       Duration joinTimeout,
                                       String systemName,
                                       String channel,
                                       Duration retryDelay,
                                       int maxRetries,
                                       Duration forgetDelay) {
        this.relayUrl = requireNonNull(relayUrl);
        this.joinTimeout = requireNonNull(joinTimeout);
        this.systemName = requireNonNull(systemName);
        this.channel = requireNonNull(channel);
        this.retryDelay = requireNonNull(retryDelay);
        this.maxRetries = maxRetries;
        this.forgetDelay = requireNonNull(forgetDelay);
    }

    public RelayP2PTransportChannelProperties(String systemName, Config config) throws URISyntaxException {
        this(
                new URI(config.getString("relay.url")),
                config.getInt("relay.port"),
                Duration.ofMillis(config.getDuration("relay.join-timeout", TimeUnit.MILLISECONDS)),
                systemName,
                config.getString("relay.channel"),
                config.getDuration("relay.retry-delay"),
                config.getInt("relay.max-retries"),
                config.getDuration("relay.forget-delay")
        );
    }

    public URI getRelayUrl() {
        return relayUrl;
    }

    public Duration getJoinTimeout() {
        return joinTimeout;
    }

    public String getSystemName() {
        return systemName;
    }

    public String getChannel() {
        return channel;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Duration getForgetDelay() {
        return forgetDelay;
    }
}
