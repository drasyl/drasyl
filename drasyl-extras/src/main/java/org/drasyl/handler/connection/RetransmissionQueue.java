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
import io.netty.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.EnumMap;

import static org.drasyl.handler.connection.Segment.ACK;
import static org.drasyl.handler.connection.Segment.FIN;
import static org.drasyl.handler.connection.Segment.PSH;
import static org.drasyl.handler.connection.Segment.SYN;
import static org.drasyl.handler.connection.Segment.add;
import static org.drasyl.handler.connection.Segment.lessThan;

/**
 * Holds all segments that has been written to the network (called in-flight) but have not been
 * acknowledged yet. This FIFO queue also updates the {@link io.netty.channel.Channel} writability
 * for the bytes it holds.
 * <p>
 * This queue mainly implements <a href="https://www.rfc-editor.org/rfc/rfc7323">RFC 7323 TCP
 * Extensions for High Performance</a>, <a href="https://www.rfc-editor.org/rfc/rfc7323#section-3">Section
 * 3 TCP Timestamps Option</a> and
 * <a href="https://www.rfc-editor.org/rfc/rfc7323#section-4">Section 4 The RTTM Mechanism</a>.
 */
public class RetransmissionQueue {
    private static final Logger LOG = LoggerFactory.getLogger(RetransmissionQueue.class);
    private long synSeq = -1;
    private long pshSeq = -1;
    private long finSeq = -1;

    RetransmissionQueue() {
    }

    public void enqueue(final ChannelHandlerContext ctx,
                        final Segment seg,
                        final TransmissionControlBlock tcb) {
        ReferenceCountUtil.touch(seg, "RetransmissionQueue enqueue " + seg.toString());
        if (seg.isSyn()) {
            synSeq = seg.seq();
        }
        if (seg.isPsh()) {
            pshSeq = seg.seq();
        }
        if (seg.isFin()) {
            finSeq = seg.seq();
        }

        // RFC 5482: The Transmission Control Protocol (TCP) specification [RFC0793] defines a
        // RFC 5482: local, per-connection "user timeout" parameter that specifies the maximum
        // RFC 5482: amount of time that transmitted data may remain unacknowledged before TCP
        // RFC 5482: will forcefully close the corresponding connection.

        // RFC 9293: If a timeout is specified, the current user timeout for this connection is
        // RFC 9293: changed to the new one.
        final ReliableTransportHandler handler = (ReliableTransportHandler) ctx.handler();
        if (tcb.config().userTimeout().toMillis() > 0) {
            handler.cancelUserTimer();
            handler.startUserTime(ctx);
        }

        // RFC 6298: (5.1) Every time a packet containing data is sent (including a retransmission),
        // RFC 6298:       if the timer is not running, start it running so that it will expire
        // RFC 6298:       after RTO seconds (for the current value of RTO).
        if (handler.retransmissionTimer == null) {
            handler.startRetransmissionTimer(ctx, tcb);
        }
    }

    @Override
    public String toString() {
        return "RTNS.Q(SYN=" + synSeq + ", PSH=" + pshSeq + ", FIN=" + finSeq + ")";
    }

    public byte handleAcknowledgement(final ChannelHandlerContext ctx,
                                      final Segment seg,
                                      final TransmissionControlBlock tcb,
                                      final long ackedBytes) {
        byte ackedCtl = 0;

        boolean somethingWasAcked = ackedBytes > 0;
        boolean synWasAcked = false;
        boolean finWasAcked = false;
        if (synSeq != -1 && lessThan(synSeq, tcb.sndUna())) {
            // SYN has been ACKed
            synSeq = -1;
            somethingWasAcked = true;
            synWasAcked = true;
            ackedCtl |= SYN;
        }
        if (pshSeq != -1 && lessThan(pshSeq, tcb.sndUna())) {
            // PSH has been ACKed
            pshSeq = -1;
            somethingWasAcked = true;
        }
        if (finSeq != -1 && lessThan(finSeq, tcb.sndUna())) {
            // FIN has been ACKed
            finSeq = -1;
            somethingWasAcked = true;
            finWasAcked = true;
        }

        if (somethingWasAcked && !((synWasAcked || finWasAcked) && ackedBytes == 1)) {
            tcb.sendBuffer().acknowledge((int) ackedBytes);
        }

        boolean queueWasNotEmpty = ackedBytes != 0;
        if (queueWasNotEmpty) {
            final ReliableTransportHandler handler = (ReliableTransportHandler) ctx.handler();
            if (!tcb.sendBuffer().hasOutstandingData()) {
                // RFC 6298: (5.2) When all outstanding data has been acknowledged, turn off the
                // RFC 6298:       retransmission timer.
                handler.cancelRetransmissionTimer();
            }
            else if (somethingWasAcked) {
                // RFC 6298: (5.3) When an ACK is received that acknowledges new data, restart the
                // RFC 6298:       retransmission timer so that it will expire after RTO seconds
                // RFC 6298:       (for the current value of RTO).
                handler.cancelRetransmissionTimer();
                handler.startRetransmissionTimer(ctx, tcb);
            }
        }

        return ackedCtl;
    }

    Segment retransmissionSegment(ChannelHandlerContext ctx,
                                  final TransmissionControlBlock tcb,
                                  final int offset,
                                  final int bytes) {
        final EnumMap<SegmentOption, Object> options = new EnumMap<>(SegmentOption.class);
        final ByteBuf data = tcb.sendBuffer().unacknowledged(offset, bytes);
        byte ctl = ACK; // FIXME: das macht aus einem SYN und FIN aber ein SYN/ACK bzw. FIN/ACK :/
        if (synSeq != -1) {
            ctl |= SYN;
        }
        else if (pshSeq != -1) {
            ctl |= PSH;
        }
        else if (finSeq != -1) {
            ctl |= FIN;
        }
        if (ctl == ACK && data.readableBytes() == 0) {
            System.out.print("");
        }
        final long seq = add(tcb.sndUna(), offset);
        final long ack = tcb.rcvNxt();
        // FIXME: add options
        final Segment retransmission = new Segment(seq, ack, ctl, tcb.rcvWnd(), options, data);
        return retransmission;
    }

    public void flush() {
        synSeq = pshSeq = finSeq = -1;
    }
}
