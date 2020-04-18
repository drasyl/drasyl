package org.drasyl.core.client.transport.retry;

import org.drasyl.core.client.transport.P2PTransportChannel;

import java.util.concurrent.CompletableFuture;

public interface RetryStrategy {

    /**
     * Gives the next channel for use. The RetryStrategy takes care of shutting down the oldChannel.
     *
     * @param oldChannel the previously used channel
     * @return future of a already started RelayP2PTransportChannel which should be used next
     * future throws P2PTransportChannelException when failing to get next channel
     */
    CompletableFuture<P2PTransportChannel> nextChannel(P2PTransportChannel oldChannel);


    /**
     * True when a next channel is available
     *
     * @return whether a next channel is available
     */
    default boolean nextChannelAvailable() {
        return true;
    }
}
