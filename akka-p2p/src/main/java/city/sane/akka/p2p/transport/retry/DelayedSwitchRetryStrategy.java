package city.sane.akka.p2p.transport.retry;

import city.sane.akka.p2p.transport.P2PTransportChannel;
import city.sane.akka.p2p.transport.P2PTransportChannelException;
import city.sane.akka.p2p.transport.relay.RelayP2PTransportChannelProperties;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;

public class DelayedSwitchRetryStrategy implements RetryStrategy {
    private final RetryAgent retryAgent;
    private final Supplier<P2PTransportChannel> channelSupplier;

    DelayedSwitchRetryStrategy(RetryAgent retryAgent, Supplier<P2PTransportChannel> channelSupplier) {
        this.retryAgent = retryAgent;
        this.channelSupplier = channelSupplier;
    }

    public DelayedSwitchRetryStrategy(RelayP2PTransportChannelProperties properties, Supplier<P2PTransportChannel> channelSupplier) {
        this(new RetryAgent(
                properties.getRetryDelay(),
                properties.getMaxRetries(),
                properties.getForgetDelay()),
        channelSupplier);

    }

    @Override
    public CompletableFuture<P2PTransportChannel> nextChannel(P2PTransportChannel oldChannel) {
        if (retryAgent.tooManyRetries()) {
            return CompletableFuture.failedFuture(new P2PTransportChannelException("Too many retries!"));
        }
        CompletableFuture<Void> shutdownFuture = ofNullable(oldChannel)
                .map(P2PTransportChannel::shutdown)
                .orElse(CompletableFuture.completedFuture(null));

        return CompletableFuture.allOf(shutdownFuture, retryAgent.retry())
                .thenCompose(v -> {
                    P2PTransportChannel channel = channelSupplier.get();
                    return channel.start()
                            .thenApply(v2 -> channel);
                });
    }

    @Override
    public boolean nextChannelAvailable() {
        return !retryAgent.tooManyRetries();
    }
}
