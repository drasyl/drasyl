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

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.ACK;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.FIN;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.PSH;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.RST;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.SEQ_NO_SPACE;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.SYN;

// es kann sein, dass wir in einem Rutsch (durch mehrere channelReads) Segmente empfangen und die dann z.B. alle jeweils ACKen
// zum Zeitpunkt des channelReads wissen wir noch nicht, ob noch mehr kommt
// daher speichern wir die nachrichten und warten auf ein channelReadComplete. Dort gucken wir dann, ob wir z.B. ACKs zusammenfassen können/etc.
class OutgoingSegmentQueue {
    private static final Logger LOG = LoggerFactory.getLogger(OutgoingSegmentQueue.class);
    private final RetransmissionQueue retransmissionQueue;
    private final RttMeasurement rttMeasurement;
    private long seq;
    private long ack;
    private byte ctl;
    private Map<Option, Object> options = new EnumMap<>(Option.class);
    private int len;

    OutgoingSegmentQueue(final RetransmissionQueue retransmissionQueue,
                         final RttMeasurement rttMeasurement) {
        this.retransmissionQueue = requireNonNull(retransmissionQueue);
        this.rttMeasurement = requireNonNull(rttMeasurement);
    }

    void addBytes(long seq,
                  int readableBytes,
                  long ack,
                  int ctl,
                  Map<Option, Object> options) {
        if (this.seq == 0) {
            this.seq = seq;
        }
        len += readableBytes;
        if (SerialNumberArithmetic.greaterThan(ack, this.ack, SEQ_NO_SPACE)) {
            this.ack = ack;
        }
        this.ctl |= ctl;
        this.options.putAll(options);
    }

    public void flush(final ChannelHandlerContext ctx, final SendBuffer sendBuffer, int mss) {
        while (!(len == 0 && ctl == 0)) {
            final ChannelPromise ackPromise = ctx.newPromise();
            final ByteBuf data = sendBuffer.remove2(mss, ackPromise);
            len -= data.readableBytes();
            byte myCtl = ctl;
            // use PSH flag only for last data
            final boolean notLast = len != 0;
            if (notLast) {
                myCtl &= ~PSH;
            }
            final ConnectionHandshakeSegment newCurrentSeg = new ConnectionHandshakeSegment(seq, ack, myCtl, options, data);
            final OutgoingSegmentEntry newCurrent = new OutgoingSegmentEntry(newCurrentSeg, ackPromise);
            seq = ConnectionHandshakeSegment.advanceSeq(seq, newCurrent.content().readableBytes());

            write(ctx, newCurrent);

            // use SYN once, as early as possible
            ctl &= ~SYN;

            if (!notLast) {
                // remove ACK after last write
                ctl &= ~ACK;

                ctl &= ~PSH;

                // remove FIN after last write
                ctl &= ~FIN;

                // remove RST after last write
                ctl &= ~RST;
            }
        }

        assert len == 0;
//        assert ctl == 0;
        seq = 0;
        ack = 0;
        ctl = 0;
        options.clear();

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
            return Objects.equals(seg, that.seg);
        }
    }
}
