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
import org.drasyl.handler.connection.SegmentOption.TimestampsOption;
import org.drasyl.util.NumberUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.connection.Segment.ACK;
import static org.drasyl.handler.connection.Segment.FIN;
import static org.drasyl.handler.connection.Segment.PSH;
import static org.drasyl.handler.connection.Segment.SYN;
import static org.drasyl.handler.connection.Segment.add;
import static org.drasyl.handler.connection.Segment.lessThan;
import static org.drasyl.handler.connection.SegmentOption.TIMESTAMPS;
import static org.drasyl.handler.connection.State.CLOSED;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Holds all segments that has been written to the network (called in-flight) but have not been
 * acknowledged yet. This FIFO queue also updates the {@link io.netty.channel.Channel} writability
 * for the bytes it holds.
 * <p>
 * This queue mainly implements <a href="https://www.rfc-editor.org/rfc/rfc7323">RFC 7323 TCP
 * Extensions for High Performance</a>, <a
 * href="https://www.rfc-editor.org/rfc/rfc7323#section-3">Section 3 TCP Timestamps Option</a> and
 * <a href="https://www.rfc-editor.org/rfc/rfc7323#section-4">Section 4 The RTTM Mechanism</a>.
 */
public class RetransmissionQueue {
    private static final Logger LOG = LoggerFactory.getLogger(RetransmissionQueue.class);
    private static final double INITIAL_SRTT = -1; // used to detect if we do initial or a subsequent RTTM
    private final Channel channel;
    ScheduledFuture<?> userTimer;
    ScheduledFuture<?> retransmissionTimer;
    double rttVar; // RTTVAR: round-trip time variation
    double sRtt; // SRTT: smoothed round-trip time
    private long synSeq = -1;
    private long pshSeq = -1;
    private long finSeq = -1;
    private long rto; // RTO: retransmission timeout

    RetransmissionQueue(final Channel channel,
                        final double rttVar,
                        final double sRtt,
                        final long rto) {
        this.channel = requireNonNull(channel);
        this.rttVar = rttVar;
        this.sRtt = sRtt;
        this.rto = requirePositive(rto);
    }

    RetransmissionQueue(final Channel channel,
                        final double rttVar,
                        final double sRtt) {
        //  Until a round-trip time (RTT) measurement has been made for a segment sent between the sender and receiver, the sender SHOULD set RTO <- 1 second
        this(channel, rttVar, sRtt, 1000);
    }

    RetransmissionQueue(final Channel channel,
                        final long tsRecent) {
        this(channel, tsRecent, 0);
    }

    RetransmissionQueue(final Channel channel) {
        this(channel, 0, 0);
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
        recreateUserTimer(ctx, tcb);

        // RFC 6298: (5.1) Every time a packet containing data is sent (including a retransmission),
        // RFC 6298:       if the timer is not running, start it running so that it will expire
        // RFC 6298:       after RTO seconds (for the current value of RTO).
        recreateRetransmissionTimer(ctx, tcb);
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
            if (!tcb.sendBuffer().hasOutstandingData()) {
                // RFC 6298: (5.2) When all outstanding data has been acknowledged, turn off the
                // RFC 6298:       retransmission timer.
                cancelRetransmissionTimer();
            } else if (somethingWasAcked) {
                // RFC 6298: (5.3) When an ACK is received that acknowledges new data, restart the
                // RFC 6298:       retransmission timer so that it will expire after RTO seconds
                // RFC 6298:       (for the current value of RTO).
                recreateRetransmissionTimer(ctx, tcb);
            }
        }

        return ackedCtl;
    }

    private void recreateUserTimer(final ChannelHandlerContext ctx,
                                   final TransmissionControlBlock tcb) {
        if (tcb.config().userTimeout().toMillis() > 0) {
            // reset existing timer
            if (userTimer != null) {
                userTimer.cancel(false);
            }

            // create new timer
            userTimer = ctx.executor().schedule(() -> {
                userTimer = null;

                // USER TIMEOUT event as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.8">RFC
                // 9293, Section 3.10.8</a>.

                // RFC 9293: For any state if the user timeout expires,
                LOG.trace("{}[{}] USER TIMEOUT expired after {}ms. Close channel.", ctx.channel(), tcb.config().userTimeout().toMillis());
                // RFC 9293: flush all queues,
                // (this is done by deleteTcb)
                // RFC 9293: signal the user "error: connection aborted due to user timeout" in
                // RFC 9293: general and for any outstanding calls,
                final ConnectionHandshakeException cause = new ConnectionHandshakeException("USER TIMEOUT expired after " + tcb.config().userTimeout().toMillis() + "ms. Close channel.");
                ctx.fireExceptionCaught(cause);

                final ReliableTransportHandler handler = (ReliableTransportHandler) ctx.handler();

                // RFC 9293: delete the TCB,
                handler.deleteTcb();
                // RFC 9293: enter the CLOSED state,
                handler.changeState(ctx, CLOSED);
                // RFC 9293: and return.
            }, tcb.config().userTimeout().toMillis(), MILLISECONDS);
        }
    }

    private void recreateRetransmissionTimer(final ChannelHandlerContext ctx,
                                             final TransmissionControlBlock tcb) {
        // reset existing timer
        if (retransmissionTimer != null) {
            retransmissionTimer.cancel(false);
        }

        // create new timer
        long rto = this.rto;
        retransmissionTimer = ctx.executor().schedule(() -> {
            retransmissionTimer = null;

            // RFC 6298: (5.4) Retransmit the earliest segment that has not been acknowledged by the
            // RFC 6298:       TCP receiver.
            final Segment retransmission = retransmissionSegment(ctx, tcb, 0, tcb.effSndMss());
            LOG.error("{} Retransmission timeout after {}ms! Retransmit `{}`. {} unACKed bytes remaining.", channel, rto, retransmission, tcb.flightSize());
            ctx.writeAndFlush(retransmission);

            // RFC 6298: (5.5) The host MUST set RTO <- RTO * 2 ("back off the timer"). The maximum
            // RFC 6298:       value discussed in (2.5) above may be used to provide an upper bound
            // RFC 6298:       to this doubling operation.
            final long oldRto = this.rto;
            rto(this.rto * 2);
            LOG.trace("{} Retransmission timeout: Change RTO from {}ms to {}ms.", ctx.channel(), oldRto, rto());

            // RFC 6298: (5.6) Start the retransmission timer, such that it expires after RTO
            // RFC 6298:       seconds (for the value of RTO after the doubling operation outlined
            // RFC 6298:       in 5.5).
            recreateRetransmissionTimer(ctx, tcb);

            // RFC 5681: When a TCP sender detects segment loss using the retransmission timer and
            // RFC 5681: the given segment has not yet been resent by way of the retransmission
            // RFC 5681: timer, the value of ssthresh MUST be set to no more than the value given in
            // RFC 5681: equation (4):
            // RFC 5681: ssthresh = max (FlightSize / 2, 2*SMSS) (4)
            final long newSsthresh = NumberUtil.max(tcb.flightSize() / 2, 2L * tcb.smss());
            if (tcb.ssthresh != newSsthresh) {
                LOG.trace("{} Congestion Control: Retransmission timeout: Set ssthresh from {} to {}.", ctx.channel(), tcb.ssthresh(), newSsthresh);
                tcb.ssthresh = newSsthresh;
            }

            // RFC 5681: Furthermore, upon a timeout (as specified in [RFC2988]) cwnd MUST be set to
            // RFC 5681: no more than the loss window, LW, which equals 1 full-sized segment
            // RFC 5681: (regardless of the value of IW).  Therefore, after retransmitting the
            // RFC 5681: dropped segment the TCP sender uses the slow start algorithm to increase
            // RFC 5681: the window from 1 full-sized segment to the new value of ssthresh, at which
            // RFC 5681: point congestion avoidance again takes over.
            if (tcb.cwnd() != tcb.mss()) {
                LOG.trace("{} Congestion Control: Retransmission timeout: Set cwnd from {} to {}.", ctx.channel(), tcb.cwnd(), tcb.mss());
                tcb.cwnd = tcb.mss();
            }
        }, rto, MILLISECONDS);
    }

    Segment retransmissionSegment(ChannelHandlerContext ctx,
                                  final TransmissionControlBlock tcb,
                                  final int offset,
                                  final int bytes) {
        final EnumMap<SegmentOption, Object> options = new EnumMap<>(SegmentOption.class);
        final ByteBuf data = tcb.sendBuffer().unacknowledged(offset, bytes);
        byte ctl = ACK;
        if (synSeq != -1) {
            ctl |= SYN;
        } else if (pshSeq != -1) {
            ctl |= PSH;
        } else if (finSeq != -1) {
            ctl |= FIN;
        }
        if (ctl == ACK && data.readableBytes() == 0) {
            System.out.print("");
        }
        final long seq = add(tcb.sndUna(), offset);
        final long ack = tcb.rcvNxt();
        tcb.retransmissionQueue.addOption(ctx, seq, ack, ctl, options, tcb);
        final Segment retransmission = new Segment(seq, ack, ctl, tcb.rcvWnd(), options, data);
        return retransmission;
    }

    void cancelRetransmissionTimer() {
        if (retransmissionTimer != null) {
            retransmissionTimer.cancel(false);
            retransmissionTimer = null;
        }
    }

    void cancelUserTimer() {
        if (userTimer != null) {
            userTimer.cancel(false);
            userTimer = null;
        }
    }

    void calculateRto(final ChannelHandlerContext ctx,
                      final long tsEcr,
                      final TransmissionControlBlock tcb) {
        final long oldRto = rto;
        if (firstRttMeasurement()) {
            // RFC 6298: (2.2) When the first RTT measurement R is made,
            final int r = (int) (tcb.config().clock().time() - tsEcr);
            LOG.trace("RTT R = {}", r);
            // RFC 6298:       the host MUST set
            // RFC 6298:       SRTT <- R
            sRtt = r;
            // RFC 6298:       RTTVAR <- R/2
            rttVar = r / 2.0;
            // RFC 6298:       RTO <- SRTT + max (G, K*RTTVAR)
            // RFC 6298: where K = 4
            final int k = tcb.config().k();
            rto((long) (sRtt + NumberUtil.max(tcb.config().clock().g(), k * rttVar)));
        } else {
            // RFC 6298: (2.3) When a subsequent RTT measurement R' is made,
            final int rDash = (int) (tcb.config().clock().time() - tsEcr);
            LOG.trace("RTT R' = {}", rDash);
            // RFC 6298:       a host MUST set
            // RFC 6298:       RTTVAR <- (1 - beta) * RTTVAR + beta * |SRTT - R'|
            // RFC 6298:       SRTT <- (1 - alpha) * SRTT + alpha * R'

            // RFC 6298:       The value of SRTT used in the update to RTTVAR is its value before
            // RFC 6298:       updating SRTT itself using the second assignment. That is, updating
            // RFC 6298:       RTTVAR and SRTT MUST be computed in the above order.

            // RFC 6298:       The above SHOULD be computed using alpha=1/8 and beta=1/4 (as
            // RFC 6298:       suggested in [JK88]).
            final float alpha = tcb.config().alpha();
            final float beta = tcb.config().beta();

            // RFC 7323: Taking multiple RTT samples per window would shorten the history calculated
            // RFC 7323: by the RTO mechanism in [RFC6298], and the below algorithm aims to maintain
            // RFC 7323: a similar history as originally intended by [RFC6298].

            // RFC 7323: It is roughly known how many samples a congestion window worth of data will
            // RFC 7323: yield, not accounting for ACK compression, and ACK losses. Such events will
            // RFC 7323: result in more history of the path being reflected in the final value for
            // RFC 7323: RTO, and are uncritical. This modification will ensure that a similar
            // RFC 7323: amount of time is taken into account for the RTO estimation, regardless of
            // RFC 7323: how many samples are taken per window:

            // RFC 7323: ExpectedSamples = ceiling(FlightSize / (SMSS * 2))
            final int expectedSamples = NumberUtil.max((int) Math.ceil((float) tcb.flightSize() / (tcb.smss() * 2)), 1);
            // RFC 7323: alpha' = alpha / ExpectedSamples
            final double alphaDash = alpha / expectedSamples;
            // RFC 7323: beta' = beta / ExpectedSamples
            final double betaDash = beta / expectedSamples;
            // RFC 7323: Note that the factor 2 in ExpectedSamples is due to "Delayed ACKs".

            // RFC 7323: Instead of using alpha and beta in the algorithm of [RFC6298], use alpha'
            // RFC 7323: and beta' instead:
            // RFC 7323: RTTVAR <- (1 - beta') * RTTVAR + beta' * |SRTT - R'|
            rttVar = (1 - betaDash) * rttVar + betaDash * Math.abs(sRtt - rDash);
            // RFC 7323: SRTT <- (1 - alpha') * SRTT + alpha' * R'
            sRtt = (1 - alphaDash) * sRtt + alphaDash * rDash;
            // RFC 7323: (for each sample R')

            // RFC 6298:       After the computation, a host MUST update
            // RFC 6298:       RTO <- SRTT + max (G, K*RTTVAR)
            final int k = 4;
            rto((long) (sRtt + NumberUtil.max(tcb.config().clock().g(), k * rttVar)));
        }

        if (rto != oldRto) {
            LOG.trace("{} New RTT measurement: Change RTO from {}ms to {}ms.", ctx.channel(), oldRto, rto);
        }
    }

    private boolean firstRttMeasurement() {
        return sRtt == INITIAL_SRTT;
    }

    public long rto() {
        return rto;
    }

    void rto(final long rto) {
        assert rto > 0;
        if (rto < (long) 1_000) {
            // RFC 6298: (2.4) Whenever RTO is computed, if it is less than 1 second, then the RTO
            // RFC 6298:       SHOULD be rounded up to 1 second.
            this.rto = 1_000;

            // RFC 6298:       Traditionally, TCP implementations use coarse grain clocks to measure
            // RFC 6298:       the RTT and trigger the RTO, which imposes a large minimum value on
            // RFC 6298:       the RTO. Research suggests that a large minimum RTO is needed to keep
            // RFC 6298:       TCP conservative and avoid spurious retransmissions [AP99].
            // RFC 6298:       Therefore, this specification requires a large minimum RTO as a
            // RFC 6298:       conservative approach, while at the same time acknowledging that at
            // RFC 6298:       some future point, research may show that a smaller minimum RTO is
            // RFC 6298:       acceptable or superior.
        } else if (rto > (long) 60_000) {
            // RFC 6298: (2.5) A maximum value MAY be placed on RTO provided it is at least 60
            // RFC 6298:       seconds.
            this.rto = 60_000;
        } else {
            this.rto = rto;
        }
    }

    public void addOption(final ChannelHandlerContext ctx,
                          final long seq,
                          final long ack,
                          final byte ctl,
                          final Map<SegmentOption, Object> options,
                          TransmissionControlBlock tcb) {
        if (tcb.sndTsOk) {
            final TimestampsOption tsOpt = new TimestampsOption(tcb.config().clock().time(), tcb.tsRecent);
            options.put(TIMESTAMPS, tsOpt);
            if ((ctl & ACK) != 0) {
                tcb.lastAckSent = ack;
            }
            LOG.trace("{} > {}", ctx.channel(), tsOpt);
        } else if ((ctl & SYN) != 0) {
            // add MSS option to SYN
            options.put(TIMESTAMPS, new TimestampsOption(tcb.config().clock().time()));
        }
    }

    public void flush() {
        synSeq = pshSeq = finSeq = -1;
        cancelUserTimer();
        cancelRetransmissionTimer();
    }
}
