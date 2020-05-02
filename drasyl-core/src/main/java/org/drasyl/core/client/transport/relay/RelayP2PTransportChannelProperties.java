package org.drasyl.core.client.transport.relay;

import org.drasyl.core.client.transport.relay.handler.RelayP2PTransportChannelInitializer;
import com.typesafe.config.Config;
import org.drasyl.core.models.CompressedPublicKey;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Set;
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

    public CompressedPublicKey getPublicKey() {
        //TODO: Hier muss der Public Key des Nodes zurückgegeben werden. Am besten irgendwie aus dem IdentityManager herausholen.
        return null;
    }

    public Set<URI> getEndpoints() {
        //TODO: Hier müssen die Endpoints dieses Nodes zurückgegeben werden. Wahrscheinlich stehen diese in der application.conf
        return null;
    }
}
