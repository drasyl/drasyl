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
import static org.drasyl.util.Preconditions.requirePositive;
import static org.drasyl.util.SerialNumberArithmetic.greaterThanOrEqualTo;
import static org.drasyl.util.SerialNumberArithmetic.lessThan;
import static org.drasyl.util.SerialNumberArithmetic.lessThanOrEqualTo;

/**
 * Holds all segments that has been written to the network (called in-flight) but have not been
 * acknowledged yet. This FIFO queue also updates the {@link io.netty.channel.Channel} writability
 * for the bytes it holds.
 */
// https://www.rfc-editor.org/rfc/rfc6298
public class RetransmissionQueue {
    // https://www.rfc-editor.org/rfc/rfc6298#section-3
    static final int K = 4;
    static final float ALPHA = 1f / 8; // smoothing factor
    static final float BETA = 1f / 4; // delay variance factor
    static final long LOWER_BOUND = 1_000; // lower bound for retransmission (e.g., 1 second)
    static final long UPPER_BOUND = 60_000; // upper bound for retransmission (e.g., 1 minute)
    private static final Logger LOG = LoggerFactory.getLogger(RetransmissionQueue.class);
    private static final double INITIAL_SRTT = -1;
    private final Channel channel;
    private final Clock clock;
    ScheduledFuture<?> retransmissionTimer;
    long tsRecent; // holds a timestamp to be echoed in TSecr whenever a segment is sent
    long lastAckSent; // holds the ACK field from the last segment sent
    boolean addTimestamps;
    double rttVar; // round-trip time variation
    double sRtt;// smoothed round-trip time
    private long synSeq = -1;
    private long pshSeq = -1;
    private long finSeq = -1;
    private double rto; // retransmission timeout

    RetransmissionQueue(final Channel channel,
                        final long tsRecent,
                        final long lastAckSent,
                        final boolean addTimestamps,
                        final double rttVar,
                        final double sRtt,
                        final double rto,
                        final Clock clock) {
        this.channel = requireNonNull(channel);
        this.tsRecent = tsRecent;
        this.lastAckSent = lastAckSent;
        this.addTimestamps = addTimestamps;
        this.rttVar = rttVar;
        this.sRtt = sRtt;
        this.rto = requirePositive(rto);
        this.clock = requireNonNull(clock);
    }

    RetransmissionQueue(final Channel channel,
                        final long tsRecent,
                        final long lastAckSent,
                        final boolean addTimestamps,
                        final Clock clock) {
        //  Until a round-trip time (RTT) measurement has been made for a segment sent between the sender and receiver, the sender SHOULD set RTO <- 1 second
        this(channel, tsRecent, lastAckSent, addTimestamps, 0, INITIAL_SRTT, 1000, clock);
    }

    RetransmissionQueue(final Channel channel) {
        this(channel, 0, 0, false, new Clock() {
            @Override
            public long time() {
                return System.nanoTime() / 1_000_000; // convert to ms
            }

            @Override
            public double g() {
                return 1.0 / 1_000;
            }
        });
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
        long rto = (long) this.rto;
        retransmissionTimer = ctx.executor().schedule(() -> {
            // RFC 6298: https://www.rfc-editor.org/rfc/rfc6298#section-5
            //  (5.4) Retransmit the earliest segment that has not been acknowledged
            //         by the TCP receiver.
            // retransmit the earliest segment that has not been acknowledged
            ConnectionHandshakeSegment retransmission = retransmissionSegment(tcb);
            LOG.error("{} Retransmission timeout after {}ms! Retransmit: {}. {} unACKed bytes remaining.", channel, rto, retransmission, tcb.sendBuffer().acknowledgeableBytes());
            ctx.writeAndFlush(retransmission);

            //    (5.5) The host MUST set RTO <- RTO * 2 ("back off the timer").  The
            //         maximum value discussed in (2.5) above may be used to provide
            //         an upper bound to this doubling operation.
            final double oldRto = this.rto;
            rto(this.rto * 2);
            LOG.error("{} Retransmission timeout: Change RTO from {}ms to {}ms.", ctx.channel(), oldRto, rto());

            // (5.6) Start the retransmission timer, such that it expires after RTO
            //         seconds (for the value of RTO after the doubling operation
            //         outlined in 5.5).
            recreateRetransmissionTimer(ctx, tcb);

            // RFC 5681: https://www.rfc-editor.org/rfc/rfc5681#section-3.1
            // When a TCP sender detects segment loss using the retransmission timer
            //   and the given segment has not yet been resent by way of the
            //   retransmission timer, the value of ssthresh MUST be set to no more
            //   than the value given in equation (4):
            //
            //      ssthresh = max (FlightSize / 2, 2*SMSS)            (4)
            final int smss = tcb.mss();
            final float flightSize = tcb.sendBuffer().acknowledgeableBytes();
            final long newSsthresh = (long) Math.max(flightSize / 2, 2 * smss);
            LOG.error("{} Congestion Control: Retransmission timeout: Set ssthresh from {} to {}.", ctx.channel(), tcb.ssthresh(), newSsthresh);
            tcb.ssthresh = newSsthresh;


            // Furthermore, upon a timeout (as specified in [RFC2988]) cwnd MUST be
            //   set to no more than the loss window, LW, which equals 1 full-sized
            //   segment (regardless of the value of IW).  Therefore, after
            //   retransmitting the dropped segment the TCP sender uses the slow start
            //   algorithm to increase the window from 1 full-sized segment to the new
            //   value of ssthresh, at which point congestion avoidance again takes
            //   over.
            LOG.error("{} Congestion Control: Retransmission timeout: Set cwnd from {} to {}.", ctx.channel(), tcb.cwnd(), tcb.mss());
            tcb.cwnd = tcb.mss();
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

    public void segmentArrivesOnOtherStates(final ChannelHandlerContext ctx,
                                            final ConnectionHandshakeSegment seg,
                                            final TransmissionControlBlock tcb) {
        // we use the timestamp option (RFC 7323, Section 3.2) for RTT measurement.
        // Using this option we satify Karn's algorithm that state that RTT sampling must not be
        // made using retransmitted segments
        final Object timestampsOption = seg.options().get(TIMESTAMPS);
        if (timestampsOption != null) {
            final long[] timestamps = (long[]) timestampsOption;
            final long tsVal = timestamps[0];
            final long tsEcr = timestamps[1];
            // Use procedure explained in RFC 7323 to be able to handle timestamps in retransmitted segments
            final boolean b = greaterThanOrEqualTo(tsVal, tsRecent, SEQ_NO_SPACE) && lessThanOrEqualTo(seg.seq(), lastAckSent, SEQ_NO_SPACE);
            LOG.error("< TSval = {}; TSecr = {}; b = {}", tsVal, tsEcr, b);
            if (b) {
                tsRecent = tsVal;

                // calculate RTO
                calculateRto(ctx, tsEcr, tcb);
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
            final long tsVal = clock.time();

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
            LOG.trace("> TSval = {}; TSecr = {}", tsVal, tsEcr);

            // add timestamps to segment
            seg.options().put(TIMESTAMPS, new long[]{ tsVal, tsEcr });

            // record ACK field from the last segment sent
            if (seg.isAck()) {
                lastAckSent = seg.ack();
            }
        }
    }

    private void calculateRto(final ChannelHandlerContext ctx,
                              final long tsEcr,
                              final TransmissionControlBlock tcb) {
        // With the timestamp option applied by us, we perform multiple RTT measurements per RTT.
        // RFC 7323, Section 4.2. state that too many samples will truncate the RTT history
        // (applied by ALPHA and BETA) too soon.
        // To handle this, we apply the implementation suggestion statet in RFC 7323, Appendix G.

        final double oldRto = rto;
        if (sRtt == INITIAL_SRTT) {
            // (2.2) When the first RTT measurement R is made, the host MUST set
            final int r = (int) (clock.time() - tsEcr);
            LOG.error("RTT R = {}", r);
            // SRTT <- R
            sRtt = r;
            // RTTVAR <- R/2
            rttVar = r / 2.0;
            // RTO <- SRTT + max (G, K*RTTVAR)
            rto(sRtt + Math.max(clock.g(), K * rttVar));
        }
        else {
            // Taking multiple RTT samples per window would shorten the history
            //   calculated by the RTO mechanism in [RFC6298]
            final int smss = tcb.mss();
            final float flightSize = tcb.sendBuffer().acknowledgeableBytes();
            int expectedSamples = Math.max((int) Math.ceil(flightSize / (smss * 2)), 1);
            double alphaDash = ALPHA / expectedSamples;
            double betaDash = BETA / expectedSamples;
            // Instead of using alpha and beta in the algorithm of [RFC6298], use
            //   alpha' and beta' instead:

            // (2.3) When a subsequent RTT measurement R' is made, a host MUST set
            final int rDash = (int) (clock.time() - tsEcr);
            LOG.error("RTT R' = {}", rDash);
            // RTTVAR <- (1 - beta) * RTTVAR + beta * |SRTT - R'|
            rttVar = (1 - betaDash) * rttVar + betaDash * Math.abs(sRtt - rDash);
            // SRTT <- (1 - alpha) * SRTT + alpha * R'
            sRtt = (1 - alphaDash) * sRtt + alphaDash * rDash;
            rto(sRtt + Math.max(clock.g(), K * rttVar));
        }

        if (rto != oldRto) {
            LOG.trace("{} New RTT measurement: Change RTO from {}ms to {}ms.", ctx.channel(), oldRto, rto);
        }
    }

    public double rto() {
        return rto;
    }

    void rto(final double rto) {
        assert rto > 0;
        if (rto < LOWER_BOUND) {
            // (2.4) Whenever RTO is computed, if it is less than 1 second, then the
            //         RTO SHOULD be rounded up to 1 second.
            this.rto = LOWER_BOUND;
        }
        else if (rto > UPPER_BOUND) {
            // (2.5) A maximum value MAY be placed on RTO provided it is at least 60
            //         seconds.
            this.rto = UPPER_BOUND;
        }
        else {
            this.rto = rto;
        }
    }

    public void segmentArrivesOnListenState(final ChannelHandlerContext ctx,
                                            final ConnectionHandshakeSegment seg,
                                            final TransmissionControlBlock tcb) {
        //         Check for a TSopt option; if one is found, save SEG.TSval in the
        //        variable TS.Recent and turn on the Snd.TS.OK bit.
        final Object timestampsOption = seg.options().get(TIMESTAMPS);
        if (timestampsOption != null) {
            final long[] timestamps = (long[]) timestampsOption;
            final long tsVal = timestamps[0];
            final long tsEcr = timestamps[1];
            LOG.trace("{} RTT measurement: Set TS.Recent to {} (SEG.TSval) and LAST.ACK.sent to {} (RCV.NXT).", ctx.channel(), tsVal, tcb.rcvNxt());
            tsRecent = tsVal;

            // Last.ACK.sent is set to RCV.NXT
            lastAckSent = tcb.rcvNxt();
        }
    }

    public void segmentArrivesOnSynSentState(final ChannelHandlerContext ctx,
                                             final ConnectionHandshakeSegment seg,
                                             final TransmissionControlBlock tcb) {
        //   Check for a TSopt option; if one is found, save SEG.TSval in
        //        variable TS.Recent and turn on the Snd.TS.OK bit in the
        //        connection control block.  If the ACK bit is set, use my.TSclock
        //        - SEG.TSecr as the initial RTT estimate.
        final Object timestampsOption = seg.options().get(TIMESTAMPS);
        if (timestampsOption != null) {
            final long[] timestamps = (long[]) timestampsOption;
            final long tsVal = timestamps[0];
            final long tsEcr = timestamps[1];
            LOG.trace("{} RTT measurement: Set TS.Recent to {} (SEG.TSval).", ctx.channel(), tsVal);
            tsRecent = tsVal;

            if (seg.isAck()) {
                // calculate RTO
                calculateRto(ctx, tsEcr, tcb);
            }
        }
    }

    public interface Clock {
        long time();

        // clock granularity in seconds
        double g();
    }
}
