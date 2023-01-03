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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.PromiseNotifier;
import org.drasyl.handler.connection.ConnectionHandshakeSegment.Option;
import org.drasyl.util.SerialNumberArithmetic;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.ACK;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.FIN;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.Option.MAXIMUM_SEGMENT_SIZE;
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
    private int len;

    OutgoingSegmentQueue(final RetransmissionQueue retransmissionQueue,
                         final RttMeasurement rttMeasurement) {
        this.retransmissionQueue = requireNonNull(retransmissionQueue);
        this.rttMeasurement = requireNonNull(rttMeasurement);
    }

    void addBytes(long seq,
                  int readableBytes,
                  long ack,
                  int ctl) {
        if (this.seq == 0) {
            this.seq = seq;
        }
        len += readableBytes;
        if (SerialNumberArithmetic.greaterThan(ack, this.ack, SEQ_NO_SPACE)) {
            this.ack = ack;
        }
        this.ctl |= ctl;
    }

    public void flush(final ChannelHandlerContext ctx, final SendBuffer sendBuffer, int mss) {
        final boolean doFlush = len != 0 || ctl != 0;
        while (len != 0 || ctl != 0) {
            final ChannelPromise promise = ctx.newPromise();
            final ByteBuf data = sendBuffer.remove(Math.min(mss, len), promise);
            len -= data.readableBytes();

            // use PSH for last data
            if (len == 0 && data.isReadable()) {
                ctl |= PSH;
            }

            final Map<Option, Object> options = new EnumMap<>(Option.class);
            if ((ctl & SYN) != 0) {
                options.put(MAXIMUM_SEGMENT_SIZE, mss);
            }

            final ConnectionHandshakeSegment seg = new ConnectionHandshakeSegment(seq, ack, ctl, options, data);
            seq = ConnectionHandshakeSegment.advanceSeq(seq, data.readableBytes());

            write(ctx, seg, promise);

            // use SYN once, as early as possible
            ctl &= ~SYN;

            if (len == 0) {
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
        assert ctl == 0;
        seq = 0;
        ack = 0;

        if (doFlush) {
            ctx.flush();
        }
    }

    private void write(final ChannelHandlerContext ctx,
                       final ConnectionHandshakeSegment seg,
                       final ChannelPromise promise) {
        // RTTM
        rttMeasurement.write(seg);

        if (seg.mustBeAcked()) {
            retransmissionQueue.add(seg, promise);

            ctx.write(seg.copy()).addListener(new RetransmissionApplier(ctx, seg, promise));
        }
        else {
            ctx.write(seg).addListener(new PromiseNotifier<>(promise));
        }
    }

    public int size() {
        return retransmissionQueue.size();
    }

    @Override
    public String toString() {
        return String.valueOf(size());
    }
}
