package org.drasyl.handler.connection;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.PendingWriteQueue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.Objects.requireNonNull;

/**
 * Holds all segments that has been written to the network (called in-flight) but have not been
 * acknowledged yet. This FIFO queue also updates the {@link io.netty.channel.Channel} writability
 * for the bytes it holds.
 */
class RetransmissionQueue {
    private final Channel channel;
    private final PendingWriteQueue pendingWrites;

    RetransmissionQueue(final Channel channel,
                        final PendingWriteQueue pendingWrites) {
        this.channel = requireNonNull(channel);
        this.pendingWrites = requireNonNull(pendingWrites);
    }

    RetransmissionQueue(final ChannelHandlerContext ctx) {
        this(ctx.channel(), new PendingWriteQueue(ctx));
    }

    public void add(final ConnectionHandshakeSegment seg, final ChannelPromise promise) {
        pendingWrites.add(seg, promise);
    }

    /**
     * Release all buffers in the queue and complete all listeners and promises.
     */
    public void releaseAndFailAll(final Throwable cause) {
        pendingWrites.removeAndFailAll(cause);
    }

    /**
     * Return the current message or {@code null} if empty.
     */
    public ConnectionHandshakeSegment current() {
        return (ConnectionHandshakeSegment) pendingWrites.current();
    }

    public void removeAndSucceedCurrent() {
        pendingWrites.remove().setSuccess();
    }

    /**
     * Returns the number of pending write operations.
     */
    public int size() {
        if (channel.eventLoop().inEventLoop()) {
            return pendingWrites.size();
        }
        else {
            // FIXME: remove
            final CompletableFuture<Integer> future = new CompletableFuture<>();
            channel.eventLoop().execute(() -> {
                future.complete(pendingWrites.size());
            });
            try {
                return future.get();
            }
            catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
            catch (final ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
