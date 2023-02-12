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
import io.netty.util.ReferenceCountUtil;
import org.drasyl.handler.connection.ReceiveBuffer.ReceiveBufferBlock;
import org.drasyl.handler.connection.SegmentOption.SackOption;
import org.drasyl.util.NumberUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.drasyl.handler.connection.Segment.ACK;
import static org.drasyl.handler.connection.Segment.FIN;
import static org.drasyl.handler.connection.Segment.PSH;
import static org.drasyl.handler.connection.Segment.RST;
import static org.drasyl.handler.connection.Segment.SYN;
import static org.drasyl.handler.connection.Segment.greaterThan;
import static org.drasyl.handler.connection.SegmentOption.MAXIMUM_SEGMENT_SIZE;
import static org.drasyl.handler.connection.SegmentOption.SACK;

/**
 * This queue holds all control bits and data to be sent.
 */
public class OutgoingSegmentQueue {
    private static final Logger LOG = LoggerFactory.getLogger(OutgoingSegmentQueue.class);
    private long seq = -1;
    private long ack = -1;
    private byte ctl;
    private int len;
    private Map<SegmentOption, Object> options;

    void place(final Segment seg) {
        place(seg.seq(), seg.content().readableBytes(), seg.ack(), seg.ctl(), seg.options());
    }

    void place(final long seq,
               final long readableBytes,
               final long ack,
               final int ctl,
               final Map<SegmentOption, Object> options) {
        if (this.seq == -1) {
            this.seq = seq;
        }
        len += readableBytes;
        if (this.ack == -1 || greaterThan(ack, this.ack)) {
            this.ack = ack;
        }
        this.ctl |= ctl;
        this.options = options;
    }

    public void flush(final ChannelHandlerContext ctx,
                      final TransmissionControlBlock tcb) {
        LOG.trace("Flush triggered.");

        final boolean doFlush = len != 0 || ctl != 0;
        while (len != 0 || ctl != 0) {
            final ByteBuf data;
            if (len > 0) {
                final int bytes = NumberUtil.min(tcb.effSndMss(), len);
                data = tcb.sendBuffer().read(bytes);
            } else {
                data = Unpooled.EMPTY_BUFFER;
            }
            len -= data.readableBytes();

            // use PSH for last data
            if (len == 0 && data.isReadable()) {
                ctl |= PSH;
            }

            final Segment seg = new Segment(seq, ack, ctl, tcb.rcvWnd(), options, data);
            seq = Segment.advanceSeq(seq, data.readableBytes());

            write(ctx, tcb, seg);

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

                // remote options after last write
                options.clear();
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
                       final Segment seg) {
        ReferenceCountUtil.touch(seg, "OutgoingSegmentQueue write " + seg.toString());
        LOG.trace("{} Write SEG `{}` to network.", ctx.channel(), seg);

        if (seg.mustBeAcked()) {
            // ACKnowledgement necessary. Add SEG to retransmission queue and apply retransmission
            tcb.retransmissionQueue().enqueue(ctx, seg, tcb);
        }
        ctx.write(seg);
    }

    public int len() {
        return len;
    }

    @Override
    public String toString() {
        return String.valueOf(len());
    }
}
