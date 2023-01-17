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
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.handler.connection.ConnectionHandshakeSegment.Option;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.EnumMap;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.ACK;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.FIN;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.PSH;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.SEQ_NO_SPACE;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.SYN;
import static org.drasyl.util.SerialNumberArithmetic.lessThan;

/**
 * Holds all segments that has been written to the network (called in-flight) but have not been
 * acknowledged yet. This FIFO queue also updates the {@link io.netty.channel.Channel} writability
 * for the bytes it holds.
 */
// https://www.rfc-editor.org/rfc/rfc6298
public class RetransmissionQueue {
    private static final Logger LOG = LoggerFactory.getLogger(RetransmissionQueue.class);
    private final Channel channel;
    ScheduledFuture<?> retransmissionTimer;
    private long synSeq = -1;
    private long pshSeq = -1;
    private long finSeq = -1;

    RetransmissionQueue(final Channel channel) {
        this.channel = requireNonNull(channel);
    }

    public void enqueue(final ChannelHandlerContext ctx,
                        final ConnectionHandshakeSegment seg,
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

        // (5.1) Every time a packet containing data is sent (including a
        //         retransmission), if the timer is not running, start it running
        //         so that it will expire after RTO seconds (for the current value
        //         of RTO).
        recreateRetransmissionTimer(ctx, tcb);
    }

    @Override
    public String toString() {
        return "RTNS.Q(SYN=" + synSeq + ", PSH=" + pshSeq + ", FIN=" + finSeq + ")";
    }

    public byte handleAcknowledgement(final ChannelHandlerContext ctx,
                                      final ConnectionHandshakeSegment seg,
                                      final TransmissionControlBlock tcb,
                                      final RttMeasurement rttMeasurement,
                                      final long ackedBytes) {
        byte ackedCtl = 0;

        boolean somethingWasAcked = ackedBytes > 0;
        boolean synWasAcked = false;
        boolean finWasAcked = false;
        if (synSeq != -1 && lessThan(synSeq, tcb.sndUna(), SEQ_NO_SPACE)) {
            // SYN has been ACKed
            synSeq = -1;
            somethingWasAcked = true;
            synWasAcked = true;
            ackedCtl |= SYN;
        }
        if (pshSeq != -1 && lessThan(pshSeq, tcb.sndUna(), SEQ_NO_SPACE)) {
            // PSH has been ACKed
            pshSeq = -1;
            somethingWasAcked = true;
        }
        if (finSeq != -1 && lessThan(finSeq, tcb.sndUna(), SEQ_NO_SPACE)) {
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
            if (tcb.sendBuffer().acknowledgeableBytes() == 0) {
                // (5.2) When all outstanding data has been acknowledged, turn off the
                //         retransmission timer.
                // everything was ACKed, cancel retransmission timer
                cancelRetransmissionTimer();
            }
            else if (somethingWasAcked) {
                //    (5.3) When an ACK is received that acknowledges new data, restart the
                //         retransmission timer so that it will expire after RTO seconds
                //         (for the current value of RTO).
                // as something was ACKed, recreate retransmission timer
                recreateRetransmissionTimer(ctx, tcb);
            }
        }

        return ackedCtl;
    }

    private void recreateRetransmissionTimer(final ChannelHandlerContext ctx,
                                             final TransmissionControlBlock tcb) {
        // reset existing timer
        if (retransmissionTimer != null) {
            retransmissionTimer.cancel(false);
        }

        // create new timer
        long rto = (long) tcb.rttMeasurement().rto();
        retransmissionTimer = ctx.executor().schedule(() -> {
//            // FIXME: https://www.rfc-editor.org/rfc/rfc6298 kapitel 5
            //  (5.4) Retransmit the earliest segment that has not been acknowledged
            //         by the TCP receiver.
            // retransmit the earliest segment that has not been acknowledged
            ConnectionHandshakeSegment retransmission = retransmissionSegment(tcb);
            LOG.error("{} Retransmission timeout after {}ms! Retransmit: {}. {} unACKed bytes remaining.", channel, rto, retransmission, tcb.sendBuffer().acknowledgeableBytes());
            ctx.writeAndFlush(retransmission);

            //    (5.5) The host MUST set RTO <- RTO * 2 ("back off the timer").  The
            //         maximum value discussed in (2.5) above may be used to provide
            //         an upper bound to this doubling operation.
            tcb.rttMeasurement().timeoutOccurred();

            // (5.6) Start the retransmission timer, such that it expires after RTO
            //         seconds (for the value of RTO after the doubling operation
            //         outlined in 5.5).
            recreateRetransmissionTimer(ctx, tcb);

//            LOG.error("{} Congestion Control: Timeout: Set ssthresh from {} to {}.", ctx.channel(), tcb.ssthresh(), tcb.mss());
//            tcb.ssthresh = tcb.mss();
        }, rto, MILLISECONDS);
    }

    ConnectionHandshakeSegment retransmissionSegment(final TransmissionControlBlock tcb) {
        final EnumMap<Option, Object> options = new EnumMap<>(Option.class);
        final ByteBuf data = tcb.sendBuffer().unacknowledged(tcb.mss());
        byte ctl = ACK;
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
            System.out.printf("");
        }
        ConnectionHandshakeSegment retransmission = new ConnectionHandshakeSegment(tcb.sndUna(), tcb.rcvNxt(), ctl, tcb.rcvWnd(), options, data);
        return retransmission;
    }

    private void cancelRetransmissionTimer() {
        if (retransmissionTimer != null) {
            retransmissionTimer.cancel(false);
            retransmissionTimer = null;
        }
    }
}
