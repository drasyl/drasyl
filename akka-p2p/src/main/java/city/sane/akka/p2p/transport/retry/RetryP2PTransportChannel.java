package city.sane.akka.p2p.transport.retry;

import city.sane.akka.p2p.P2PActorRef;
import city.sane.akka.p2p.transport.InboundMessageEnvelope;
import city.sane.akka.p2p.transport.OutboundMessageEnvelope;
import city.sane.akka.p2p.transport.P2PTransportChannel;
import city.sane.akka.p2p.transport.P2PTransportChannelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

/**
 * Defines a channel that tries to reopen the channel according to a defined strategy in case of connection failures.
 */
public class RetryP2PTransportChannel implements P2PTransportChannel {
    private static final Logger log = LoggerFactory.getLogger(RetryP2PTransportChannel.class);
    private final RetryStrategy retryStrategy;
    private final CompletableFuture<Void> shutdownFuture;
    private AtomicBoolean searchingChannel;
    private CompletableFuture<P2PTransportChannel> currentChannel;

    protected RetryP2PTransportChannel(RetryStrategy retryStrategy) {
        this.currentChannel = new CompletableFuture<>();
        this.retryStrategy = retryStrategy;
        this.searchingChannel = new AtomicBoolean(false);
        this.shutdownFuture = new CompletableFuture<>();
    }

    @Override
    public CompletableFuture<Void> start() {
        try {
            return searchNextChannel()
                    .thenRunAsync(() -> log.info("Channel is now open."));
        }
        catch (P2PTransportChannelException e) {
            log.error("Start failed!", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<Void> closeFuture() {
        return shutdownFuture;
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        try {
            return getCurrentChannel().shutdown();
        }
        catch (P2PTransportChannelException e) {
            log.warn("No channel available for shutdown!", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public void send(OutboundMessageEnvelope outboundMessage) throws P2PTransportChannelException {
        getCurrentChannel().send(outboundMessage);
    }

    @Override
    public void receive(InboundMessageEnvelope inboundMessage) {
        try {
            getCurrentChannel().receive(inboundMessage);
        }
        catch (P2PTransportChannelException e) {
            log.error("Receive failed!", e);
        }
    }

    @Override
    public boolean accept(P2PActorRef recipient) {
        try {
            return getCurrentChannel().accept(recipient);
        }
        catch (P2PTransportChannelException e) {
            try {
                searchNextChannel();
            }
            catch (P2PTransportChannelException ex) {
                log.error("Will never be able to accept!", ex);
            }
            log.warn("Wants to accept but no channel available!", e);
            return false;
        }
    }

    private CompletableFuture<P2PTransportChannel> searchNextChannel() throws P2PTransportChannelException {
        if (!searchingChannel.getAndSet(true)) {
            currentChannel = nextChannel(null, 1);
            try {
                if (currentChannel.isCompletedExceptionally()) {
                    // allow catching exception if currentChannel fails immediately
                    currentChannel.join();
                }
                currentChannel.thenAcceptAsync(this::useChannel);
            }
            catch (CompletionException e) {
                throw new P2PTransportChannelException("Next channel search failed!", e.getCause());
            }
            finally {
                searchingChannel.set(false);
            }
        }
        return currentChannel;
    }

    private CompletableFuture<P2PTransportChannel> nextChannel(Throwable t, int attempt) {
        // tries to get a next channel from the retryStrategy

        if (retryStrategy.nextChannelAvailable()) {
            return retryStrategy.nextChannel(currentChannel.getNow(null))
                    .thenApply(CompletableFuture::completedFuture)
                    .exceptionally(tt -> {
                        log.warn("Opening new channel failed! Attempt: {}", attempt);
                        // optionally: t.addSuppressed(tt);
                        return nextChannel(t, attempt + 1);
                    })
                    .thenCompose(Function.identity());
        }
        else {
            P2PTransportChannelException ex = new P2PTransportChannelException(
                    String.format("No next channel available after %s attempts!", attempt),
                    t);
            return CompletableFuture.failedFuture(ex);
        }
    }

    private synchronized void useChannel(P2PTransportChannel channel) {
        this.currentChannel.complete(channel);
        channel.closeFuture()
                .thenRunAsync(() -> {
                    try {
                        searchNextChannel()
                                .thenRunAsync(() -> log.info("Channel is reopened now."));
                    }
                    catch (P2PTransportChannelException e) {
                        // should inform transport that retry channel failed
                        log.error("Retry channel failed!", e);
                    }
                });
    }

    private synchronized P2PTransportChannel getCurrentChannel() throws P2PTransportChannelException {
        return ofNullable(currentChannel
                .exceptionally(t -> null)
                .getNow(null))
                .orElseThrow(() -> new P2PTransportChannelException("No current channel available!"));
    }

}
