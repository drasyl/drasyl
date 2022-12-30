/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.handler.connection;

import io.netty.channel.Channel;
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

    RetransmissionQueue(final Channel channel) {
        this(channel, new PendingWriteQueue(channel));
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
