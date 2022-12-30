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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.PromiseNotifier;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.ArrayDeque;

import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.SEQ_NO_SPACE;
import static org.drasyl.util.SerialNumberArithmetic.lessThanOrEqualTo;

// es kann sein, dass wir in einem Rutsch (durch mehrere channelReads) Segmente empfangen und die dann z.B. alle jeweils ACKen
// zum Zeitpunkt des channelReads wissen wir noch nicht, ob noch mehr kommt
// daher speichern wir die nachrichten und warten auf ein channelReadComplete. Dort gucken wir dann, ob wir z.B. ACKs zusammenfassen können/etc.
class OutgoingSegmentQueue {
    private static final Logger LOG = LoggerFactory.getLogger(OutgoingSegmentQueue.class);
    private final ArrayDeque<OutgoingSegmentEntry> deque;
    private final Channel channel;
    private final RetransmissionQueue retransmissionQueue;
    private final RttMeasurement rttMeasurement;

    OutgoingSegmentQueue(final Channel channel,
                         final ArrayDeque<OutgoingSegmentEntry> deque,
                         final RetransmissionQueue retransmissionQueue,
                         final RttMeasurement rttMeasurement) {
        this.deque = requireNonNull(deque);
        this.channel = requireNonNull(channel);
        this.retransmissionQueue = requireNonNull(retransmissionQueue);
        this.rttMeasurement = requireNonNull(rttMeasurement);
    }

    OutgoingSegmentQueue(final Channel channel,
                         final RetransmissionQueue retransmissionQueue,
                         final RttMeasurement rttMeasurement) {
        this(channel, new ArrayDeque<>(), retransmissionQueue, rttMeasurement);
    }

    public ChannelPromise add(final ConnectionHandshakeSegment seg,
                              final ChannelPromise writePromise,
                              final ChannelPromise ackPromise) {
        deque.add(new OutgoingSegmentEntry(seg, writePromise, ackPromise));
        return writePromise;
    }

    public ChannelPromise add(final ChannelHandlerContext ctx,
                              final ConnectionHandshakeSegment seg,
                              final ChannelPromise writePromise) {
        deque.add(new OutgoingSegmentEntry(seg, writePromise, ctx.newPromise()));
        return writePromise;
    }

    public ChannelPromise add(final ChannelHandlerContext ctx,
                              final ConnectionHandshakeSegment seg) {
        return add(ctx, seg, channel.newPromise());
    }

    public ChannelPromise addAndFlush(final ChannelHandlerContext ctx,
                                      final ConnectionHandshakeSegment seg,
                                      final ChannelPromise writePromise,
                                      final ChannelPromise ackPromise) {
        try {
            return add(seg, writePromise, ackPromise);
        }
        finally {
            flush(ctx);
        }
    }

    public ChannelPromise addAndFlush(final ChannelHandlerContext ctx,
                                      final ConnectionHandshakeSegment seg,
                                      final ChannelPromise writePromise) {
        return addAndFlush(ctx, seg, writePromise, ctx.newPromise());
    }

    public ChannelPromise addAndFlush(final ChannelHandlerContext ctx,
                                      final ConnectionHandshakeSegment seg) {
        return addAndFlush(ctx, seg, ctx.newPromise());
    }

    public void flush(final ChannelHandlerContext ctx) {
        if (deque.isEmpty()) {
            return;
        }

        final int size = deque.size();
        LOG.trace("Channel read complete. Now check if we can repackage/cumulate {} outgoing segments.", size);
        if (size == 1) {
            final OutgoingSegmentEntry current = deque.remove();
            LOG.trace("Outgoing queue 1: Write and flush `{}`.", current.seg());
            write(ctx, current);
            ctx.flush();
            return;
        }

        // multiple segments in queue. Check if we can cumulate them
        OutgoingSegmentEntry current = deque.poll();
        while (!deque.isEmpty()) {
            OutgoingSegmentEntry next = deque.remove();

            if (current.seg().isOnlyAck() && next.seg().isOnlyAck() && current.seg().seq() == next.seg().seq() && lessThanOrEqualTo(current.seg().seq(), next.seg().seq(), SEQ_NO_SPACE)) {
                // cumulate ACKs
                LOG.trace("Outgoing queue: Current segment `{}` is followed by segment `{}` with same flags set, same SEQ, and >= ACK. We can purge current segment.", current.seg(), next.seg());
                current.seg().release();
                next.writePromise().addListener(new PromiseNotifier<>(current.writePromise()));
                next.ackPromise().addListener(new PromiseNotifier<>(current.ackPromise()));
            }
            else if (current.seg().isOnlyAck() && current.seg().seq() == next.seg().seq() && current.seg().len() == 0) {
                // piggyback ACK
                LOG.trace("Outgoing queue: Piggyback current ACKnowledgement `{}` to next segment `{}`.", current.seg(), next.seg());
                next = new OutgoingSegmentEntry(ConnectionHandshakeSegment.piggybackAck(next.seg(), current.seg()), next.writePromise(), next.ackPromise());
                next.writePromise().addListener(new PromiseNotifier<>(current.writePromise()));
                next.ackPromise().addListener(new PromiseNotifier<>(current.ackPromise()));
            }
            else {
                LOG.trace("Outgoing queue 2: Write `{}`.", current.seg());
                write(ctx, current);
            }

            current = next;
        }

        LOG.trace("Outgoing queue 3: Write and flush `{}`.", current.seg());
        write(ctx, current);
        ctx.flush();
    }

    private void write(final ChannelHandlerContext ctx,
                       final OutgoingSegmentEntry entry) {
        final boolean mustBeAcked = mustBeAcked(entry.seg());
        if (mustBeAcked) {
            retransmissionQueue.add(entry.seg(), entry.ackPromise());
            entry.writePromise().addListener(new RetransmissionTimeoutApplier(ctx, entry.seg(), entry.ackPromise()));
            final ConnectionHandshakeSegment copy = entry.seg().copy();

            // RTTM
            rttMeasurement.sendAck(copy);

            ctx.write(copy, entry.writePromise()).addListener(CLOSE_ON_FAILURE).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture channelFuture) {
                    LOG.trace("{} WRITTEN `{}`: {}", channelFuture.channel(), entry.seg(), channelFuture.isSuccess());
                }
            });
        }
        else {
            // RTTM
            rttMeasurement.sendAck(entry.seg());

            entry.writePromise().addListener(new PromiseNotifier<>(entry.ackPromise()));
            ctx.write(entry.seg(), entry.writePromise()).addListener(CLOSE_ON_FAILURE).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture channelFuture) {
                    LOG.trace("{} WRITTEN `{}`: {}", channelFuture.channel(), entry.seg(), channelFuture.isSuccess());
                }
            });
        }
    }

    private boolean mustBeAcked(final ConnectionHandshakeSegment seg) {
        return (!seg.isOnlyAck() && !seg.isRst()) || seg.len() != 0;
    }

    public void releaseAndFailAll(final Throwable cause) {
        OutgoingSegmentEntry entry;
        while ((entry = deque.poll()) != null) {
            final ConnectionHandshakeSegment seg = entry.seg();
            final ChannelPromise writePromise = entry.writePromise();
            final ChannelPromise ackPromise = entry.ackPromise();

            seg.release();
            writePromise.tryFailure(cause);
            ackPromise.tryFailure(cause);
        }
    }

    static class OutgoingSegmentEntry {
        private final ConnectionHandshakeSegment seg;
        private final ChannelPromise writePromise;
        private final ChannelPromise ackPromise;

        OutgoingSegmentEntry(final ConnectionHandshakeSegment seg,
                             final ChannelPromise writePromise,
                             final ChannelPromise ackPromise) {
            this.seg = requireNonNull(seg);
            this.writePromise = requireNonNull(writePromise);
            this.ackPromise = requireNonNull(ackPromise);
        }

        @Override
        public String toString() {
            return "OutgoingSegmentEntry{" +
                    "seg=" + seg +
                    ", writePromise=" + writePromise +
                    ", ackPromise=" + ackPromise +
                    '}';
        }

        public ConnectionHandshakeSegment seg() {
            return seg;
        }

        public ChannelPromise writePromise() {
            return writePromise;
        }

        public ChannelPromise ackPromise() {
            return ackPromise;
        }
    }
}
