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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.PromiseNotifier;
import org.drasyl.handler.connection.ConnectionHandshakeSegment.Option;
import org.drasyl.util.SerialNumberArithmetic;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.PSH;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.SEQ_NO_SPACE;

// es kann sein, dass wir in einem Rutsch (durch mehrere channelReads) Segmente empfangen und die dann z.B. alle jeweils ACKen
// zum Zeitpunkt des channelReads wissen wir noch nicht, ob noch mehr kommt
// daher speichern wir die nachrichten und warten auf ein channelReadComplete. Dort gucken wir dann, ob wir z.B. ACKs zusammenfassen können/etc.
class OutgoingSegmentQueue {
    private static final Logger LOG = LoggerFactory.getLogger(OutgoingSegmentQueue.class);
    private final ArrayDeque<OutgoingSegmentEntry> deque;
    private final Channel channel;
    private final RetransmissionQueue retransmissionQueue;
    private final RttMeasurement rttMeasurement;
    private long seq;
    private long ack;
    private byte ctl;
    private Map<Option, Object> options = new EnumMap<>(Option.class);
    private int len;

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

    public void add(final ConnectionHandshakeSegment seg,
                    final ChannelPromise ackPromise) {
        if (seq == 0) {
            seq = seg.seq();
        }
        len += seg.len();
        if (SerialNumberArithmetic.greaterThan(seg.ack(), ack, SEQ_NO_SPACE)) {
            ack = seg.ack();
        }
        ctl |= seg.ctl();
        options.putAll(seg.options());

        deque.add(new OutgoingSegmentEntry(seg, ackPromise));
    }

    public void flush(final ChannelHandlerContext ctx) {
        if (deque.isEmpty()) {
            return;
        }

        final int size = deque.size();
        LOG.trace("Channel read complete. Now check if we can repackage/cumulate {} outgoing segments.", size);

        if (size == 1) {
            final OutgoingSegmentEntry current = deque.remove();
            final ChannelPromise ackPromise = current.ackPromise();

            final ByteBuf data = current.content();
            final ConnectionHandshakeSegment newSeg = new ConnectionHandshakeSegment(seq, ack, ctl, options, data);
            final OutgoingSegmentEntry newCurrent = new OutgoingSegmentEntry(newSeg, ackPromise);

            seq = ConnectionHandshakeSegment.advanceSeq(seq, current.content().readableBytes());
            assert current.seg().equals(newCurrent.seg());
            seq = 0;
            ack = 0;
            ctl = 0;
            options.clear();
            len = 0;

            write(ctx, newCurrent);
            ctx.flush();
            return;
        }

        // multiple segments in queue. Check if we can cumulate them
        OutgoingSegmentEntry current = deque.poll();
        while (!deque.isEmpty()) {
            OutgoingSegmentEntry next = deque.remove();

            final ConnectionHandshakeSegment currentSeg = current.seg();
            final ConnectionHandshakeSegment nextSeg = next.seg();
            final ConnectionHandshakeSegment piggybackingSeq = nextSeg.piggybackAck(currentSeg);

            if (piggybackingSeq != null) {
                // piggyback ACK
                LOG.trace("Piggyback current ACK `{}` to next segment `{}`.", currentSeg, nextSeg);
                next = new OutgoingSegmentEntry(piggybackingSeq, next.ackPromise());
                next.ackPromise().addListener(new PromiseNotifier<>(current.ackPromise()));
            }
            else {
                byte myCtl = ctl;
                // use PSH flag only for last data
                if (nextSeg != null) {
                    myCtl &= ~PSH;
                }
                final ConnectionHandshakeSegment newCurrentSeg = new ConnectionHandshakeSegment(seq, ack, myCtl, options, current.content());
                final OutgoingSegmentEntry newCurrent = new OutgoingSegmentEntry(newCurrentSeg, current.ackPromise());
                seq = ConnectionHandshakeSegment.advanceSeq(seq, current.content().readableBytes());
                assert current.equals(newCurrent);

                write(ctx, newCurrent);
            }

            current = next;
        }

        byte myCtl = ctl;
        final ConnectionHandshakeSegment newCurrentSeg = new ConnectionHandshakeSegment(seq, ack, myCtl, options, current.content());
        final OutgoingSegmentEntry newCurrent = new OutgoingSegmentEntry(newCurrentSeg, current.ackPromise());
        seq = ConnectionHandshakeSegment.advanceSeq(seq, current.content().readableBytes());
        assert current.seg().equals(newCurrentSeg);

        seq = 0;
        ack = 0;
        ctl = 0;
        options.clear();
        len = 0;

        write(ctx, current);
        ctx.flush();
    }

    private void write(final ChannelHandlerContext ctx,
                       OutgoingSegmentEntry entry) {
        // RTTM
        entry = new OutgoingSegmentEntry(entry.seg().copy(), entry.ackPromise());

        rttMeasurement.write(entry.seg());

        final boolean mustBeAcked = mustBeAcked(entry.seg());
        if (mustBeAcked) {
            retransmissionQueue.add(entry.seg(), entry.ackPromise());
            final ConnectionHandshakeSegment copy = entry.seg().copy();

            OutgoingSegmentEntry finalEntry = entry;
            ctx.write(copy).addListener(new RetransmissionTimeoutApplier(ctx, entry.seg(), entry.ackPromise())).addListener(CLOSE_ON_FAILURE).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture channelFuture) {
                    LOG.trace("{} WRITTEN `{}`: {}", channelFuture.channel(), finalEntry.seg(), channelFuture.isSuccess());
                }
            });
        }
        else {
            OutgoingSegmentEntry finalEntry1 = entry;
            ctx.write(entry.seg()).addListener(new PromiseNotifier<>(entry.ackPromise())).addListener(CLOSE_ON_FAILURE).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture channelFuture) {
                    LOG.trace("{} WRITTEN `{}`: {}", channelFuture.channel(), finalEntry1.seg(), channelFuture.isSuccess());
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
            final ChannelPromise ackPromise = entry.ackPromise();

            seg.release();
            ackPromise.tryFailure(cause);
        }
    }

    public int size() {
        return retransmissionQueue.size();
    }

    @Override
    public String toString() {
        return String.valueOf(size());
    }

    static class OutgoingSegmentEntry implements ReferenceCounted {
        private final ConnectionHandshakeSegment seg;
        private final ChannelPromise ackPromise;

        OutgoingSegmentEntry(final ConnectionHandshakeSegment seg,
                             final ChannelPromise ackPromise) {
            this.seg = requireNonNull(seg);
            this.ackPromise = requireNonNull(ackPromise);
        }

        @Override
        public String toString() {
            return "OutgoingSegmentEntry{" +
                    "seg=" + seg +
                    ", ackPromise=" + ackPromise +
                    '}';
        }

        public ConnectionHandshakeSegment seg() {
            return seg;
        }

        public ChannelPromise ackPromise() {
            return ackPromise;
        }

        @Override
        public int refCnt() {
            return 0;
        }

        @Override
        public ReferenceCounted retain() {
            return null;
        }

        @Override
        public ReferenceCounted retain(int increment) {
            return null;
        }

        @Override
        public ReferenceCounted touch() {
            return null;
        }

        @Override
        public ReferenceCounted touch(Object hint) {
            return null;
        }

        @Override
        public boolean release() {
            return false;
        }

        @Override
        public boolean release(int decrement) {
            return false;
        }

        public ByteBuf content() {
            return seg().content();
        }

        @Override
        public int hashCode() {
            return Objects.hash(seg, ackPromise);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final OutgoingSegmentEntry that = (OutgoingSegmentEntry) o;
            return Objects.equals(seg, that.seg) && Objects.equals(ackPromise, that.ackPromise);
        }
    }
}
