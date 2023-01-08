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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.PendingWriteQueue;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Holds all segments that has been written to the network (called in-flight) but have not been
 * acknowledged yet. This FIFO queue also updates the {@link io.netty.channel.Channel} writability
 * for the bytes it holds.
 */
public class RetransmissionQueue {
    private static final Logger LOG = LoggerFactory.getLogger(RetransmissionQueue.class);
    private final Channel channel;
    private final PendingWriteQueue pendingWrites;
    private ScheduledFuture<?> retransmissionTimer;

    RetransmissionQueue(final Channel channel,
                        final PendingWriteQueue pendingWrites) {
        this.channel = requireNonNull(channel);
        this.pendingWrites = requireNonNull(pendingWrites);
    }

    RetransmissionQueue(final Channel channel) {
        this(channel, new PendingWriteQueue(channel));
    }

    public void add(final ChannelHandlerContext ctx,
                    final ConnectionHandshakeSegment seg,
                    final ChannelPromise promise,
                    final RttMeasurement rttMeasurement) {
        pendingWrites.add(seg, promise);
        recreateRetransmissionTimer(ctx, rttMeasurement);
    }

    /**
     * Release all buffers in the queue and complete all listeners and promises.
     */
    public void releaseAndFailAll() {
        pendingWrites.removeAndFailAll(new Exception("FIXME"));
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

    @Override
    public String toString() {
        return String.valueOf(size());
    }

    public void handleAcknowledgement(final ChannelHandlerContext ctx,
                                      final ConnectionHandshakeSegment seg,
                                      final TransmissionControlBlock tcb,
                                      final RttMeasurement rttMeasurement) {
        ConnectionHandshakeSegment current = current();
        final boolean queueWasNotEmpty = current != null;
        boolean somethingWasAcked = true;
        while (current != null && tcb.isFullyAcknowledged(current)) {
            LOG.trace("{} Segment `{}` has been fully ACKnowledged. Remove from retransmission queue. {} writes remain in retransmission queue.", ctx.channel(), current, size() - 1);
            removeAndSucceedCurrent();
            somethingWasAcked = true;

            current = current();
        }

        if (queueWasNotEmpty) {
            if (current == null) {
                // everything was ACKed, cancel retransmission timer
                cancelRetransmissionTimer();
            }
            else if (somethingWasAcked) {
                // as something was ACKed, recreate retransmission timer
                recreateRetransmissionTimer(ctx, rttMeasurement);
            }
        }
    }

    private void recreateRetransmissionTimer(final ChannelHandlerContext ctx,
                                             final RttMeasurement rttMeasurement) {
        // reset existing timer
        if (retransmissionTimer != null) {
            retransmissionTimer.cancel(false);
        }

        // create new timer
        long rto = (long) rttMeasurement.rto();
        retransmissionTimer = ctx.executor().schedule(() -> {
            // https://www.rfc-editor.org/rfc/rfc6298 kapitel 5
            ConnectionHandshakeSegment current = current();
            LOG.error("{} Retransmission timeout after {}ms! Current SEG: {}", channel, rto, current);

            // retransmit the earliest segment that has not been acknowledged
            ctx.writeAndFlush(current.copy());

            // The host MUST set RTO <- RTO * 2 ("back off the timer")
            rttMeasurement.timeoutOccured();

            // Start the retransmission timer, such that it expires after RTO seconds
            recreateRetransmissionTimer(ctx, rttMeasurement);
        }, rto, MILLISECONDS);
    }

    private void cancelRetransmissionTimer() {
        if (retransmissionTimer != null) {
            retransmissionTimer.cancel(false);
            retransmissionTimer = null;
        }
    }

    public long bytes() {
        // remove pending writes overhead
        return pendingWrites.bytes() - pendingWrites.size() * 64;
    }
}
