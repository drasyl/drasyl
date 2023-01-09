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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.handler.connection.ConnectionHandshakeSegment.Option;
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
import static org.drasyl.util.SerialNumberArithmetic.greaterThan;

// es kann sein, dass wir in einem Rutsch (durch mehrere channelReads) Segmente empfangen und die dann z.B. alle jeweils ACKen
// zum Zeitpunkt des channelReads wissen wir noch nicht, ob noch mehr kommt
// daher speichern wir die nachrichten und warten auf ein channelReadComplete. Dort gucken wir dann, ob wir z.B. ACKs zusammenfassen können/etc.
public class OutgoingSegmentQueue {
    private static final Logger LOG = LoggerFactory.getLogger(OutgoingSegmentQueue.class);
    private final RetransmissionQueue retransmissionQueue;
    private final RttMeasurement rttMeasurement;
    private long seq = -1;
    private long ack = -1;
    private byte ctl;
    private int len;

    OutgoingSegmentQueue(final RetransmissionQueue retransmissionQueue,
                         final RttMeasurement rttMeasurement) {
        this.retransmissionQueue = requireNonNull(retransmissionQueue);
        this.rttMeasurement = requireNonNull(rttMeasurement);
    }

    void addBytes(final long seq,
                  final long readableBytes,
                  final long ack,
                  final int ctl) {
        if (this.seq == -1) {
            this.seq = seq;
        }
        len += readableBytes;
        if (this.ack == -1 || greaterThan(ack, this.ack, SEQ_NO_SPACE)) {
            this.ack = ack;
        }
        this.ctl |= ctl;
    }

    public void flush(final ChannelHandlerContext ctx,
                      final TransmissionControlBlock tcb) {
        final boolean doFlush = len != 0 || ctl != 0;
        while (len != 0 || ctl != 0) {
            final ChannelPromise promise = ctx.newPromise();
            final ByteBuf data;
            if (len > 0) {
                final int bytes = Math.min(tcb.mss(), len);
                data = tcb.sendBuffer().read(bytes);
            }
            else {
                data = Unpooled.EMPTY_BUFFER;
            }
            len -= data.readableBytes();

            // use PSH for last data
            if (len == 0 && data.isReadable()) {
                ctl |= PSH;
            }

            final Map<Option, Object> options = new EnumMap<>(Option.class);
            if ((ctl & SYN) != 0) {
                options.put(MAXIMUM_SEGMENT_SIZE, tcb.mss());
            }

            final ConnectionHandshakeSegment seg = new ConnectionHandshakeSegment(seq, ack, ctl, tcb.rcvWnd(), options, data);
            seq = ConnectionHandshakeSegment.advanceSeq(seq, data.readableBytes());

            write(ctx, tcb, seg, promise);

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
        seq = -1;
        ack = -1;

        if (doFlush) {
            ctx.flush();
        }
    }

    private void write(final ChannelHandlerContext ctx,
                       final TransmissionControlBlock tcb,
                       final ConnectionHandshakeSegment seg,
                       final ChannelPromise promise) {
        LOG.trace("{} Write SEG `{}` to network.", ctx.channel(), seg);

        // RTTM
        rttMeasurement.write(seg);

        if (seg.mustBeAcked()) {
            // ACKnowledgement necessary. Add SEG to retransmission queue and apply retransmission
            retransmissionQueue.add(ctx, seg.copy(), promise, rttMeasurement);
            ctx.write(seg);
        }
        else {
            // no ACKnowledgement necessary, just write to network and succeed
            ctx.write(seg, promise);
        }
    }

    public int len() {
        return len;
    }

    @Override
    public String toString() {
        return String.valueOf(len());
    }
}
