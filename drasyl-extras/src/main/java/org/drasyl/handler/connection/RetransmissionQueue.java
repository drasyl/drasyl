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
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.Option.TIMESTAMPS;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.PSH;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.SEQ_NO_SPACE;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.SYN;
import static org.drasyl.util.SerialNumberArithmetic.lessThan;
import static org.drasyl.util.SerialNumberArithmetic.lessThanOrEqualTo;

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
    public static final int K = 4;
    public static final double G = 1.0 / 1_000; // clock granularity in seconds
    static final float ALPHA = .8F; // smoothing factor (e.g., .8 to .9)
    static final float BETA = 1.3F; // delay variance factor (e.g., 1.3 to 2.0)
    static final long LOWER_BOUND = 1_000; // lower bound for retransmission (e.g., 1 second)
    static final long UPPER_BOUND = 60_000; // upper bound for retransmission (e.g., 1 minute)
    long tsRecent; // holds a timestamp to be echoed in TSecr whenever a segment is sent
    long lastAckSent; // holds the ACK field from the last segment sent
    boolean addTimestamps;
    // FIXME: move these variables to RetransmissionQueue?
    private double RTTVAR;
    private double SRTT = -1; // default value
    private double RTO = 1000; //  Until a round-trip time (RTT) measurement has been made for a segment sent between the sender and receiver, the sender SHOULD set RTO <- 1 second

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
        long rto = (long) RTO;
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
            timeoutOccurred();

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

    public void segmentArrives(final ChannelHandlerContext ctx,
                               final ConnectionHandshakeSegment seg,
                               final TransmissionControlBlock tcb) {
        final int smss = tcb.mss();
        final long flightSize = tcb.sndWnd();
        final Object timestampsOption = seg.options().get(TIMESTAMPS);
        if (timestampsOption != null) {
            final long[] timestamps = (long[]) timestampsOption;
            final long tsVal = timestamps[0];
            final long tsEcr = timestamps[1];
            if (lessThanOrEqualTo(tsRecent, tsVal, SEQ_NO_SPACE) && lessThanOrEqualTo(seg.seq(), lastAckSent, SEQ_NO_SPACE)) {
                tsRecent = tsVal;

                // calculate RTO
//                calculateRto(ctx, tsEcr, smss, flightSize);
            }
        }
    }

    public void write(final ConnectionHandshakeSegment seg) {
        if (!addTimestamps && seg.isSyn()) {
            // we start adding timestamps only with the first SYN
            // otherwise, RST messages caused by unexpected segments before the handshake will contain timestamps
            addTimestamps = true;
        }

        if (addTimestamps) {
            // TSval contains current value of the sender's timestamp clock
            final long tsVal = System.nanoTime() / 1_000_000;

            // tsEcr is only valid if ACK bit is set
            final long tsEcr;
            if (seg.isAck()) {
                // when timestamps are sent, the TSecr field is set to the current TS.Recent value
                tsEcr = tsRecent;
            }
            else {
                // when ACK bit is not set, set TSecr field to zero
                tsEcr = 0;
            }

            // add timestamps to segment
            seg.options().put(TIMESTAMPS, new long[]{ tsVal, tsEcr });

            // record ACK field from the last segment sent
            lastAckSent = seg.ack();
        }
    }

    private void calculateRto(final ChannelHandlerContext ctx,
                              final long tsEcr,
                              final int smss,
                              final long flightSize) {
        double oldRto = RTO;
        if (SRTT == -1) {
            // first measurement
            int r = (int) (System.nanoTime() / 1_000_000 - tsEcr);
            SRTT = r;
            RTTVAR = r / 2.0;
            RTO = SRTT + Math.max(G, K * RTTVAR);
            assert RTO > 0;
        }
        else {
            // subsequent measurement
            int rDash = (int) (System.nanoTime() / 1_000_000 - tsEcr);

            int ExpectedSamples = (int) Math.ceil(flightSize / (smss * 2));
            double alphaDash = ALPHA / ExpectedSamples;
            double betaDash = BETA / ExpectedSamples;
            RTTVAR = (1 - betaDash) * RTTVAR + betaDash * Math.abs(SRTT - rDash);
            SRTT = (1 - alphaDash) * SRTT + alphaDash * rDash;
            RTO = SRTT + Math.max(G, K * RTTVAR);
            assert RTO > 0;
        }

        if (RTO < LOWER_BOUND) {
            // Whenever RTO is computed, if it is less than 1 second, then the
            //         RTO SHOULD be rounded up to 1 second.
            RTO = LOWER_BOUND;
        }
        else if (RTO > UPPER_BOUND) {
            // A maximum value MAY be placed on RTO provided it is at least 60
            //         seconds.
            RTO = UPPER_BOUND;
        }

        if (RTO != oldRto) {
            LOG.trace("{} RTO set to {}ms.", ctx.channel(), RTO);
        }
    }

    void timeoutOccurred() {
        RTO *= 2;

        if (RTO > UPPER_BOUND) {
            // A maximum value MAY be placed on RTO provided it is at least 60
            //         seconds.
            RTO = UPPER_BOUND;
        }
    }

    public double rto() {
        return RTO;
    }
}
