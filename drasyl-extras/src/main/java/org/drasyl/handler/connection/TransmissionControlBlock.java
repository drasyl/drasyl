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
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.util.NumberUtil;
import org.drasyl.util.SerialNumberArithmetic;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.connection.Segment.ACK;
import static org.drasyl.handler.connection.Segment.MAX_SEQ_NO;
import static org.drasyl.handler.connection.Segment.MIN_SEQ_NO;
import static org.drasyl.handler.connection.Segment.SEQ_NO_SPACE;
import static org.drasyl.handler.connection.Segment.add;
import static org.drasyl.handler.connection.Segment.advanceSeq;
import static org.drasyl.handler.connection.Segment.greaterThan;
import static org.drasyl.handler.connection.Segment.greaterThanOrEqualTo;
import static org.drasyl.handler.connection.Segment.lessThan;
import static org.drasyl.handler.connection.Segment.lessThanOrEqualTo;
import static org.drasyl.handler.connection.Segment.sub;
import static org.drasyl.handler.connection.SegmentOption.MAXIMUM_SEGMENT_SIZE;
import static org.drasyl.handler.connection.State.ESTABLISHED;
import static org.drasyl.util.Preconditions.requireInRange;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * <pre>
 *       Send Sequence Space
 *
 *                    1         2          3          4
 *               ----------|----------|----------|----------
 *                      SND.UNA    SND.NXT    SND.UNA
 *                                           +SND.WND
 *
 *         1 - old sequence numbers which have been acknowledged
 *         2 - sequence numbers of unacknowledged data
 *         3 - sequence numbers allowed for new data transmission
 *         4 - future sequence numbers which are not yet allowed
 *  </pre>
 * <pre>
 *          Receive Sequence Space
 *
 *                        1          2          3
 *                    ----------|----------|----------
 *                           RCV.NXT    RCV.NXT
 *                                     +RCV.WND
 *
 *         1 - old sequence numbers which have been acknowledged
 *         2 - sequence numbers allowed for new reception
 *         3 - future sequence numbers which are not yet allowed
 * </pre>
 */
public class TransmissionControlBlock {
    public static final int OVERRIDE_TIMEOUT = 100; // RFC 9293: The override timeout should be in the range 0.1 - 1.0
    private static final Logger LOG = LoggerFactory.getLogger(TransmissionControlBlock.class);
    private static final boolean NODELAY = false; // bypass Nagle Delays by disabling Nagle's algorithm
    private static final int DRASYL_HDR_SIZE = 0; // FIXME: ermitteln
    final SendBuffer sendBuffer;
    final RetransmissionQueue retransmissionQueue;
    private final OutgoingSegmentQueue outgoingSegmentQueue;
    private final ReceiveBuffer receiveBuffer;
    private final int rcvBuff;
    private final long iss; // initial send sequence number
    protected long ssthresh; // slow start threshold
    // congestion control
    long cwnd; // congestion window
    long lastAdvertisedWindow;
    // Receive Sequence Variables
    private long rcvNxt; // next sequence number expected on an incoming segments, and is the left or lower edge of the receive window
    private int rcvWnd; // receive window
    private long allowedBytesToFlush = -1;
    // Send Sequence Variables
    private long sndUna; // oldest unacknowledged sequence number
    private long sndNxt; // next sequence number to be sent
    private long sndWnd; // send window
    private long sndWl1; // segment sequence number used for last window update
    private long sndWl2; // segment acknowledgment number used for last window update
    private long irs; // initial receive sequence number
    private int mss; // maximum segment size
    // sender's silly window syndrome avoidance algorithm (Nagle algorithm)
    private long maxSndWnd;
    private int duplicateAcks;
    private ScheduledFuture<?> overrideTimer;
    private long recover;

    @SuppressWarnings("java:S107")
    TransmissionControlBlock(final long sndUna,
                             final long sndNxt,
                             final int sndWnd,
                             final long iss,
                             final long rcvNxt,
                             final int rcvWnd,
                             final int rcvBuff,
                             final long irs,
                             final SendBuffer sendBuffer,
                             final OutgoingSegmentQueue outgoingSegmentQueue,
                             final RetransmissionQueue retransmissionQueue,
                             final ReceiveBuffer receiveBuffer,
                             final int mss,
                             final long cwnd,
                             final long ssthresh,
                             final long maxSndWnd,
                             final long recover) {
        this.sndUna = requireInRange(sndUna, MIN_SEQ_NO, MAX_SEQ_NO);
        this.sndNxt = requireInRange(sndNxt, MIN_SEQ_NO, MAX_SEQ_NO);
        this.sndWnd = requireNonNegative(sndWnd);
        this.iss = requireInRange(iss, MIN_SEQ_NO, MAX_SEQ_NO);
        this.rcvNxt = requireInRange(rcvNxt, MIN_SEQ_NO, MAX_SEQ_NO);
        this.rcvWnd = requireNonNegative(rcvWnd);
        this.rcvBuff = requirePositive(rcvBuff);
        this.irs = requireInRange(irs, MIN_SEQ_NO, MAX_SEQ_NO);
        this.sendBuffer = requireNonNull(sendBuffer);
        this.outgoingSegmentQueue = requireNonNull(outgoingSegmentQueue);
        this.retransmissionQueue = requireNonNull(retransmissionQueue);
        this.receiveBuffer = requireNonNull(receiveBuffer);
        this.mss = requirePositive(mss);
        this.cwnd = requireNonNegative(cwnd);
        this.ssthresh = requireNonNegative(ssthresh);
        this.maxSndWnd = requireNonNegative(maxSndWnd);
        this.recover = requireInRange(recover, MIN_SEQ_NO, MAX_SEQ_NO);
    }

    @SuppressWarnings("java:S107")
    TransmissionControlBlock(final long sndUna,
                             final long sndNxt,
                             final int sndWnd,
                             final long iss,
                             final long rcvNxt,
                             final int rcvWnd,
                             final long irs,
                             final SendBuffer sendBuffer,
                             final RetransmissionQueue retransmissionQueue,
                             final ReceiveBuffer receiveBuffer,
                             final int mss) {
        this(sndUna, sndNxt, sndWnd, iss, rcvNxt, rcvWnd, rcvWnd, irs, sendBuffer, new OutgoingSegmentQueue(), retransmissionQueue, receiveBuffer, mss, effSndMss(mss) * 3L, rcvWnd, sndWnd, iss);
    }

    TransmissionControlBlock(final Channel channel,
                             final long sndUna,
                             final long sndNxt,
                             final int sndWnd,
                             final long iss,
                             final int rcvWnd,
                             final long irs,
                             final int mss) {
        this(sndUna, sndNxt, sndWnd, iss, irs, rcvWnd, irs, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), mss);
    }

    TransmissionControlBlock(final Channel channel,
                             final long sndUna,
                             final long sndNxt,
                             final long iss,
                             final long irs,
                             final int windowSize,
                             final int mss) {
        this(sndUna, sndNxt, windowSize, iss, irs, windowSize, irs, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), mss);
    }

    TransmissionControlBlock(final Channel channel,
                             final long iss,
                             final long irs,
                             final int windowSize,
                             final int mss) {
        this(iss, iss, windowSize, iss, irs, windowSize, irs, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), mss);
    }

    // RFC 1122, Section 4.2.2.6
    // https://www.rfc-editor.org/rfc/rfc1122#section-4.2.2.6
    // Eff.snd.MSS = min(SendMSS+20, MMS_S) - TCPhdrsize - IPoptionsize
    private static int effSndMss(final int mss) {
        return NumberUtil.min(mss + DRASYL_HDR_SIZE, 1432 - DRASYL_HDR_SIZE) - Segment.SEG_HDR_SIZE;
    }

    public long sndUna() {
        return sndUna;
    }

    public long sndNxt() {
        return sndNxt;
    }

    public long sndWnd() {
        return sndWnd;
    }

    public long iss() {
        return iss;
    }

    public long rcvNxt() {
        return rcvNxt;
    }

    public int rcvWnd() {
        return rcvWnd;
    }

    public long irs() {
        return irs;
    }

    public int mss() {
        return mss;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TransmissionControlBlock that = (TransmissionControlBlock) o;
        return sndUna == that.sndUna && sndNxt == that.sndNxt && sndWnd == that.sndWnd && iss == that.iss && rcvNxt == that.rcvNxt && rcvWnd == that.rcvWnd && irs == that.irs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sndUna, sndNxt, sndWnd, iss, rcvNxt, rcvWnd, irs);
    }

    @Override
    public String toString() {
        return "TransmissionControlBlock{" +
                "SND.UNA=" + sndUna +
                ", SND.NXT=" + sndNxt +
                ", SND.WND=" + sndWnd +
                ", ISS=" + iss +
                ", RCV.NXT=" + rcvNxt +
                ", RCV.WND=" + rcvWnd +
                ", IRS=" + irs +
                ", " + sendBuffer +
                ", OG.SEG.Q=" + outgoingSegmentQueue +
                ", " + retransmissionQueue +
                ", " + receiveBuffer +
                ", MSS=" + mss +
                ", CWND=" + cwnd +
                ", SSTHRESH=" + ssthresh +
                '}';
    }

    public void delete() {
        cancelOverrideTimer();
        sendBuffer.release();
        receiveBuffer.release();
    }

    public ReceiveBuffer receiveBuffer() {
        return receiveBuffer;
    }

    public boolean isDuplicateAck(final Segment seg) {
        return lessThanOrEqualTo(seg.ack(), sndUna);
    }

    public boolean isAckSomethingNotYetSent(final Segment seg) {
        return seg.isAck() && greaterThan(seg.ack(), sndNxt);
    }

    public boolean isAcceptableAck(final Segment seg) {
        return seg.isAck() && lessThan(sndUna, seg.ack()) && lessThanOrEqualTo(seg.ack(), sndNxt);
    }

    public boolean synHasBeenAcknowledged() {
        return greaterThan(sndUna, iss);
    }

    public boolean isAckOurSynOrFin(final Segment seg) {
        return seg.isAck() && lessThan(sndUna, seg.ack()) && lessThanOrEqualTo(seg.ack(), sndNxt);
    }

    public boolean isAckSomethingNeverSent(final Segment seg) {
        return seg.isAck() && (lessThanOrEqualTo(seg.ack(), iss) || greaterThan(seg.ack(), sndNxt));
    }

    private void writeBytes(final long seg,
                            final long readableBytes,
                            final long ack) {
        if (readableBytes > 0) {
            sndNxt = advanceSeq(sndNxt, readableBytes);
            writeWithout(seg, readableBytes, ack, ACK, new EnumMap<>(SegmentOption.class));
        }
    }

    void write(final Segment seg) {
        final int len = seg.len();
        if (len > 0) {
            sndNxt = advanceSeq(sndNxt, len);
        }
        writeWithout(seg);
    }

    void writeWithout(final Segment seg) {
        writeWithout(seg.seq(), seg.content().readableBytes(), seg.ack(), seg.ctl(), seg.options());
    }

    private void writeWithout(final long seq,
                              final long readableBytes,
                              final long ack,
                              final int ctl, Map<SegmentOption, Object> options) {
        outgoingSegmentQueue.addBytes(seq, readableBytes, ack, ctl, options);
    }

    void writeAndFlush(final ChannelHandlerContext ctx,
                       final Segment seg) {
        write(seg);
        outgoingSegmentQueue.flush(ctx, this);
    }

    void addToSendBuffer(final ByteBuf data, final ChannelPromise promise) {
        sendBuffer.enqueue(data, promise);
    }

    void flush(final ChannelHandlerContext ctx,
               final State state,
               final boolean newFlush,
               final boolean overrideTimeoutOccurred) {
        try {
            if (newFlush) {
                // merke dir wie viel byes wir jetzt im buffer haben und verwende auch nur bis dahin
                allowedBytesToFlush = sendBuffer.readableBytes();
            }

            if (state != ESTABLISHED || allowedBytesToFlush < 1) {
                return;
            }

            while (allowedBytesToFlush > 0) {
                if (!NODELAY) {
                    // apply Nagle algorithm, which aims to coalesce short segments (sender's SWS avoidance algorithm)
                    // https://www.rfc-editor.org/rfc/rfc9293.html#section-3.8.6.2.1
                    // The "usable window" is: U = SND.UNA + SND.WND - SND.NXT
                    final long u = sub(add(sndUna, NumberUtil.min(sndWnd(), cwnd())), sndNxt);
                    // D is the amount of data queued in the sending TCP endpoint but not yet sent
                    final long d = allowedBytesToFlush;

                    // Send data...
                    final double fs = 0.5; // Nagle algorithm: Fs is a fraction whose recommended value is 1/2
                    final boolean sendData;
                    if (NumberUtil.min(d, u) >= (long) effSndMss()) {
                        // ..if a maximum-sized segment can be sent, i.e., if:
                        // min(D,U) >= Eff.snd.MSS;
                        sendData = true;
                    }
                    else if (sndNxt == sndUna && d <= u) {
                        // or if the data is pushed and all queued data can be sent now, i.e., if:
                        // SND.NXT = SND.UNA and D <= U
                        sendData = true;
                    }
                    else if (sndNxt == sndUna && NumberUtil.min(d, u) >= fs * maxSndWnd) {
                        // or if at least a fraction Fs of the maximum window can be sent, i.e., if:
                        // SND.NXT = SND.UNA and min(D,U) >= Fs * Max(SND.WND);
                        sendData = true;
                    }
                    else if (overrideTimeoutOccurred) {
                        // or if the override timeout occurs
                        sendData = true;
                    }
                    else {
                        createOverrideTimer(ctx, state, newFlush);
                        sendData = false;
                    }

                    if (sendData) {
                        cancelOverrideTimer();
                    }
                    else {
                        LOG.trace("{} Sender's SWS avoidance: No send condition met. Delay {} bytes.", ctx.channel(), allowedBytesToFlush);
                        return;
                    }
                }

                // at least one byte is required for Zero-Window Probing
                final long usableWindow;
                if (newFlush && sndWnd() == 0 && flightSize() == 0) {
                    // zero window probing
                    usableWindow = 1;
                }
                else {
                    final long window = NumberUtil.min(sndWnd(), cwnd());
                    usableWindow = NumberUtil.max(0, window - flightSize());
                }

                final long remainingBytes = NumberUtil.min(effSndMss(), usableWindow, sendBuffer.readableBytes(), allowedBytesToFlush);

                if (remainingBytes > 0) {
                    LOG.trace("{}[{}] {} bytes in-flight. SND.WND/CWND of {} bytes allows us to write {} new bytes to network. {} bytes wait to be written. Write {} bytes.", ctx.channel(), state, flightSize(), NumberUtil.min(sndWnd(), cwnd()), usableWindow, allowedBytesToFlush, remainingBytes);
                    writeBytes(sndNxt, remainingBytes, rcvNxt);
                    allowedBytesToFlush -= remainingBytes;
                }
                else {
                    return;
                }
            }
        }
        finally {
            outgoingSegmentQueue.flush(ctx, this);
        }
    }

    void flush(final ChannelHandlerContext ctx,
               final State state,
               final boolean newFlush) {
        flush(ctx, state, newFlush, false);
    }

    private void createOverrideTimer(final ChannelHandlerContext ctx,
                                     final State state,
                                     final boolean newFlush) {
        if (overrideTimer == null) {
            overrideTimer = ctx.executor().schedule(() -> {
                overrideTimer = null;
                LOG.trace("{} Sender's SWS avoidance: Override timeout occurred after {}ms.", ctx.channel(), OVERRIDE_TIMEOUT);
                flush(ctx, state, newFlush, true);
            }, OVERRIDE_TIMEOUT, MILLISECONDS);
        }
    }

    private void cancelOverrideTimer() {
        if (overrideTimer != null) {
            overrideTimer.cancel(false);
            overrideTimer = null;
        }
    }

    long flightSize() {
        return sub(sndNxt, sndUna);
    }

    public void negotiateMss(final ChannelHandlerContext ctx,
                             final Segment seg) {
        final Integer mssOption = (Integer) seg.options().get(MAXIMUM_SEGMENT_SIZE);
        if (mssOption != null && mssOption < mss) {
            LOG.trace("{}[{}] Remote peer sent MSS {}. This is smaller then our MSS {}. Reduce our MSS.", ctx.channel(), mssOption, mss);
            mss = mssOption;
        }
    }

    public long handleAcknowledgement(final ChannelHandlerContext ctx,
                                      final Segment seg) {
        long ackedBytes = 0;
        if (sndUna != seg.ack()) {
            LOG.trace("{} Got `{}`. Advance SND.UNA from {} to {} (+{}).", ctx.channel(), seg, sndUna(), seg.ack(), SerialNumberArithmetic.sub(seg.ack(), sndUna(), SEQ_NO_SPACE));
            ackedBytes = sub(seg.ack(), sndUna);
            sndUna = seg.ack();
        }

        final byte ackedCtl = retransmissionQueue.handleAcknowledgement(ctx, seg, this, ackedBytes);

        // TODO: If the SYN or SYN/ACK is lost, the initial window used by a
        //   sender after a correctly transmitted SYN MUST be one segment
        //   consisting of at most SMSS bytes.
        final boolean synAcked = (ackedCtl & Segment.SYN) != 0;
        // only when new data is acked
        // As specified in [RFC3390], the SYN/ACK and the acknowledgment of the SYN/ACK MUST NOT increase the size of the congestion window.
        if (ackedBytes > 0 && !synAcked) {
            if (doSlowStart()) {
                // During slow start, a TCP increments cwnd by at most SMSS bytes for
                //   each ACK received that cumulatively acknowledges new data.

                // Slow Start -> +1 MSS after each ACK
                final long increment = NumberUtil.min(mss, ackedBytes);
                LOG.trace("{} Congestion Control: Slow Start: {} new bytes has ben ACKed. Increase cwnd by {} from {} to {}.", ctx.channel(), ackedBytes, increment, cwnd, cwnd + increment);
                cwnd += increment;
            }
            else {
                // Congestion Avoidance -> +1 MSS after each RTT
                final long increment = 1;//(long) Math.ceil(((long) mss * mss) / (float) cwnd);
                LOG.trace("{} Congestion Control: Congestion Avoidance: {} new bytes has ben ACKed. Increase cwnd by {} from {} to {}.", ctx.channel(), ackedBytes, increment, cwnd, cwnd + increment);
                cwnd += increment;
            }
        }

        return ackedBytes;
    }

    public void synchronizeState(final Segment seg) {
        rcvNxt = advanceSeq(seg.seq(), seg.len());
        irs = seg.seq();
    }

    public void updateSndWnd(final ChannelHandlerContext ctx, final Segment seg) {
        if (sndWnd != seg.window()) {
            LOG.trace("{} Change SND.WND from {} to {}.", ctx.channel(), sndWnd, seg.window());
            sndWnd = seg.window();
        }
        sndWl1 = seg.seq();
        sndWl2 = seg.ack();
        maxSndWnd = NumberUtil.max(maxSndWnd, sndWnd);
    }

    public SendBuffer sendBuffer() {
        return sendBuffer;
    }

    public long sndWl1() {
        return sndWl1;
    }

    public long sndWl2() {
        return sndWl2;
    }

    // RFC 9293: SEGMENT ARRIVES on other state
    // https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.7.4
    public boolean isAcceptableSeg(final Segment seg) {
        // There are four cases for the acceptability test for an incoming segment:
        if (seg.len() == 0 && rcvWnd == 0) {
            // SEG.SEQ = RCV.NXT
            return seg.seq() == rcvNxt;
        }
        else if (seg.len() == 0 && rcvWnd > 0) {
            // RCV.NXT =< SEG.SEQ < RCV.NXT+RCV.WND
            return lessThanOrEqualTo(rcvNxt, seg.seq()) && lessThan(seg.seq(), add(rcvNxt, rcvWnd));
        }
        else if (seg.len() > 0 && rcvWnd == 0) {
            // not acceptable
            return false;
        }
        else {
            // RCV.NXT =< SEG.SEQ < RCV.NXT+RCV.WND or RCV.NXT =< SEG.SEQ+SEG.LEN-1 < RCV.NXT+RCV.WND
            return (lessThanOrEqualTo(rcvNxt, seg.seq()) && lessThan(seg.seq(), add(rcvNxt, rcvWnd))) ||
                    (lessThanOrEqualTo(rcvNxt, add(seg.seq(), seg.len() - 1)) && greaterThan(add(seg.seq(), seg.len() - 1), add(rcvNxt, rcvWnd)));
        }
    }

    boolean doSlowStart() {
        return cwnd < ssthresh;
    }

    public long cwnd() {
        return cwnd;
    }

    public OutgoingSegmentQueue outgoingSegmentQueue() {
        return outgoingSegmentQueue;
    }

    public RetransmissionQueue retransmissionQueue() {
        return retransmissionQueue;
    }

    public long ssthresh() {
        return ssthresh;
    }

    public void gotDuplicateAckCandidate(final ChannelHandlerContext ctx,
                                         final Segment seg) {
        // An acknowledgment is considered a
        //      "duplicate" in the following algorithms when (a) the receiver of
        //      the ACK has outstanding data, (b) the incoming acknowledgment
        //      carries no data, (c) the SYN and FIN bits are both off, (d) the
        //      acknowledgment number is equal to the greatest acknowledgment
        //      received on the given connection (TCP.UNA from [RFC793]) and (e)
        //      the advertised window in the incoming acknowledgment equals the
        //      advertised window in the last incoming acknowledgment.
        final boolean duplicateAck = sendBuffer.hasOutstandingData() && seg.len() == 0 && !seg.isSyn() && !seg.isFin() && seg.ack() == sndUna && seg.window() == lastAdvertisedWindow;
        if (duplicateAck) {
            // increment counter
            duplicateAcks += 1;
            LOG.error("{} Congestion Control: Fast Retransmit/Fast Recovery: Got duplicate ACK {}#{}. {} unACKed bytes remaining.", ctx.channel(), seg.ack(), duplicateAcks, flightSize());

            // Fast Retransmit/Fast Recovery
            // RFC 5681, Section 3.2
            // https://www.rfc-editor.org/rfc/rfc5681#section-3.2
            if (duplicateAcks < 3) {
                // RFC 5681
                // On the first and second duplicate ACKs received at a sender, a
                //       TCP SHOULD send a segment of previously unsent data per [RFC3042]
                //       provided that the receiver's advertised window allows, the total
                //       FlightSize would remain less than or equal to cwnd plus 2*SMSS,
                //       and that new data is available for transmission.

                // RFC 3042, Section 2.
                // * The receiver's advertised window allows the transmission of the
                //       segment.
                // * The amount of outstanding data would remain less than or equal
                //       to the congestion window plus 2 segments.  In other words, the
                //       sender can only send two segments beyond the congestion window
                //       (cwnd).
                final int smss = effSndMss();
                final boolean doLimitedTransmit = sndWnd() >= smss && (flightSize() + smss) <= (cwnd() + 2 * smss);
                if (doLimitedTransmit) {
                    final Segment retransmission = retransmissionQueue().retransmissionSegment(ctx, this, 0, smss);
                    LOG.error("{} Congestion Control: Fast Retransmit/Fast Recovery: Limited Transmit: Got duplicate ACK. Retransmit `{}`.", ctx.channel(), retransmission);
                    ctx.writeAndFlush(retransmission);
                    // RFC 5681
                    // Further, the
                    //       TCP sender MUST NOT change cwnd to reflect these two segments
                    //       [RFC3042].  Note that a sender using SACK [RFC2018] MUST NOT send
                    //       new data unless the incoming duplicate acknowledgment contains
                    //       new SACK information.
                }
            }
            if (duplicateAcks == 3) {
                // RFC 6582
                // 2)  Three duplicate ACKs:
                //       When the third duplicate ACK is received, the TCP sender first
                //       checks the value of recover to see if the Cumulative
                //       Acknowledgment field covers more than recover.  If so, the value
                //       of recover is incremented to the value of the highest sequence
                //       number transmitted by the TCP so far.  The TCP then enters fast
                //       retransmit (step 2 of Section 3.2 of [RFC5681]).  If not, the TCP
                //       does not enter fast retransmit and does not reset ssthresh.
                if (greaterThan(seg.ack(), recover)) {
                    recover = sub(sndNxt, 1);
                }
                else {
                    return;
                }

                // RFC 5681
                // 2. When the third duplicate ACK is received, a TCP MUST set ssthresh
                //       to no more than the value given in equation (4).
                //      ssthresh = max (FlightSize / 2, 2*SMSS)            (4)
                final int smss = effSndMss();
                final long newSsthresh = NumberUtil.max(flightSize() / 2, 2L * smss);
                if (ssthresh != newSsthresh) {
                    LOG.trace("{} Congestion Control: Fast Retransmit/Fast Recovery: Set ssthresh from {} to {}.", ctx.channel(), ssthresh(), newSsthresh);
                    ssthresh = newSsthresh;
                }

                // RFC 5681
                // 3. The lost segment starting at SND.UNA MUST be retransmitted and
                //       cwnd set to ssthresh plus 3*SMSS.  This artificially "inflates"
                //       the congestion window by the number of segments (three) that have
                //       left the network and which the receiver has buffered.
                final Segment retransmission = retransmissionQueue().retransmissionSegment(ctx, this, 0, smss);
                LOG.error("{} Congestion Control: Fast Retransmit/Fast Recovery: Got 3 duplicate ACKs in a row. Retransmit `{}`.", ctx.channel(), retransmission);
                ctx.writeAndFlush(retransmission);

                // increase congestion window as we know that at least three segments have left the network
                long newCwnd = ssthresh() + 3L * mss();
                if (newCwnd != cwnd()) {
                    LOG.error("{} Congestion Control: Fast Retransmit/Fast Recovery: Set cwnd from {} to {}.", ctx.channel(), cwnd(), newCwnd);
                    this.cwnd = newCwnd;
                }
            }
            else if (duplicateAcks > 3) {
                // RFC 5681
                // 4. For each additional duplicate ACK received (after the third),
                //       cwnd MUST be incremented by SMSS.  This artificially inflates the
                //       congestion window in order to reflect the additional segment that
                //       has left the network.

                // increase congestion window as we know another segment has left the network
                final int smss = effSndMss();
                long newCwnd = cwnd() + smss;
                if (newCwnd != cwnd()) {
                    LOG.trace("{} Congestion Control: Fast Retransmit/Fast Recovery: Set cwnd from {} to {}.", ctx.channel(), cwnd(), newCwnd);
                    this.cwnd = newCwnd;
                }

                // When previously unsent data is available and the new value of
                //       cwnd and the receiver's advertised window allow, a TCP SHOULD
                //       send 1*SMSS bytes of previously unsent data.
//                final int offset = (-3 + duplicateAcks) * smss;
//                final Segment retransmission = retransmissionQueue().retransmissionSegment(ctx, this, 0, effSndMss());
//                LOG.error("{} Congestion Control: Fast Retransmit/Fast Recovery: Got {}th duplicate ACKs. Retransmit `{}`.", ctx.channel(), duplicateAcks, retransmission);
//                ctx.writeAndFlush(retransmission);
            }
        }
        else if (duplicateAcks != 0) {
            // reset counter
            duplicateAcks = 0;
        }
    }

    public void resetDuplicateAcks(final ChannelHandlerContext ctx, final Segment seg,
                                   final long ackedBytes) {
        if (duplicateAcks != 0) {
            // RFC 6582
            //    3)  Response to newly acknowledged data:
            //       Step 6 of [RFC5681] specifies the response to the next ACK that
            //       acknowledges previously unacknowledged data.  When an ACK arrives
            //       that acknowledges new data, this ACK could be the acknowledgment
            //       elicited by the initial retransmission from fast retransmit, or
            //       elicited by a later retransmission.  There are two cases:
            final boolean fullAcknowledgement = greaterThanOrEqualTo(seg.ack(), recover);

            if (fullAcknowledgement) {
                // RFC 6582
                //  Full acknowledgments:
                //       If this ACK acknowledges all of the data up to and including
                //       recover, then the ACK acknowledges all the intermediate segments
                //       sent between the original transmission of the lost segment and
                //       the receipt of the third duplicate ACK.  Set cwnd to either (1)
                //       min (ssthresh, max(FlightSize, SMSS) + SMSS) or (2) ssthresh,
                //       where ssthresh is the value set when fast retransmit was entered,
                //       and where FlightSize in (1) is the amount of data presently
                //       outstanding.  This is termed "deflating" the window.  If the
                //       second option is selected, the implementation is encouraged to
                //       take measures to avoid a possible burst of data, in case the
                //       amount of data outstanding in the network is much less than the
                //       new congestion window allows.  A simple mechanism is to limit the
                //       number of data packets that can be sent in response to a single
                //       acknowledgment.  Exit the fast recovery procedure.

                // RFC 5681
                // 6. When the next ACK arrives that acknowledges previously
                //       unacknowledged data, a TCP MUST set cwnd to ssthresh (the value
                //       set in step 2).  This is termed "deflating" the window.
                final int smss = effSndMss();
                final long newCwnd = NumberUtil.min(ssthresh(), NumberUtil.max(flightSize(), smss) + smss);
                if (cwnd != newCwnd) {
                    LOG.error("{} Congestion Control: Fast Retransmit/Fast Recovery: Set cwnd from {} to {}.", ctx.channel(), cwnd(), newCwnd);
                    this.cwnd = newCwnd;
                }
                //
                //       This ACK should be the acknowledgment elicited by the
                //       retransmission from step 3, one RTT after the retransmission
                //       (though it may arrive sooner in the presence of significant out-
                //       of-order delivery of data segments at the receiver).
                //       Additionally, this ACK should acknowledge all the intermediate
                //       segments sent between the lost segment and the receipt of the
                //       third duplicate ACK, if none of these were lost.

                LOG.error("{} Congestion Control: Fast Retransmit/Fast Recovery: Got intervening ACK `{}` (full ACK). Reset duplicate ACKs counter. {} unACKed bytes remaining.", ctx.channel(), seg, flightSize());
                duplicateAcks = 0;
            }
            else {
                // RFC 6582
                // Partial acknowledgments:
                //       If this ACK does *not* acknowledge all of the data up to and
                //       including recover, then this is a partial ACK.  In this case,
                //       retransmit the first unacknowledged segment.
                final Segment retransmission = retransmissionQueue().retransmissionSegment(ctx, this, 0, effSndMss());
                LOG.error("{} Congestion Control: Fast Retransmit/Fast Recovery: Got intervening ACK `{}` (partial ACK). Retransmit `{}`. {} unACKed bytes remaining.", ctx.channel(), seg, retransmission, flightSize());
                // Deflate the
                //       congestion window by the amount of new data acknowledged by the
                //       Cumulative Acknowledgment field.
                long newCwnd = NumberUtil.max(0, cwnd - ackedBytes);
                //       If the partial ACK acknowledges
                //       at least one SMSS of new data, then add back SMSS bytes to the
                //       congestion window.
                //       This artificially inflates the congestion
                //       window in order to reflect the additional segment that has left
                //       the network.
                if (ackedBytes >= effSndMss()) {
                    newCwnd += effSndMss();
                }
                if (cwnd != newCwnd) {
                    LOG.error("{} Congestion Control: Fast Retransmit/Fast Recovery: Set cwnd from {} to {}.", ctx.channel(), cwnd(), newCwnd);
                    this.cwnd = newCwnd;
                }
                //       Send a new segment if permitted by the new value of
                //       cwnd.  This "partial window deflation" attempts to ensure that,
                //       when fast recovery eventually ends, approximately ssthresh amount
                //       of data will be outstanding in the network.  Do not exit the fast
                //       recovery procedure (i.e., if any duplicate ACKs subsequently
                //       arrive, execute step 4 of Section 3.2 of [RFC5681]).
                //
                //     FIXME: For the first partial ACK that arrives during fast recovery, also
                //       reset the retransmit timer.  Timer management is discussed in
                //       more detail in Section 4.

                ctx.writeAndFlush(retransmission);
            }
        }
    }

    public void advanceRcvNxt(final ChannelHandlerContext ctx, final int advancement) {
        rcvNxt = advanceSeq(rcvNxt, advancement);
        LOG.trace("{} Advance RCV.NXT to {}.", ctx.channel(), rcvNxt);
    }

    public void decrementRcvWnd(final int decrement) {
        rcvWnd -= decrement;
        assert rcvWnd >= 0 : "RCV.WND must be non-negative";
    }

    public void incrementRcvWnd(final ChannelHandlerContext ctx) {
        // receiver's SWS avoidance algorithms
        // RFC 9293, Section 3.8.6.2.2
        // https://www.rfc-editor.org/rfc/rfc9293.html#section-3.8.6.2.2

        // total receive buffer space is RCV.BUFF
        // RCV.USER octets of this total may be tied up with data that has been received and acknowledged but that the user process has not yet consumed
        int rcvUser = receiveBuffer.readableBytes();
        final double fr = 0.5; // Fr is a fraction whose recommended value is 1/2

        if (rcvBuff() - rcvUser - rcvWnd >= NumberUtil.min(fr * rcvBuff(), (long) effSndMss())) {
            final int newRcvWind = rcvBuff() - rcvUser;
            LOG.trace("{} Receiver's SWS avoidance: Advance RCV.WND from {} to {} (+{}).", ctx.channel(), rcvWnd, newRcvWind, newRcvWind - rcvWnd);
            rcvWnd = newRcvWind;
            assert rcvWnd >= 0 : "RCV.WND must be non-negative";
        }
    }

    int rcvBuff() {
        return rcvBuff;
    }

    long rcvUser() {
        return receiveBuffer.readableBytes();
    }

    public long maxSndWnd() {
        return maxSndWnd;
    }

    public int effSndMss() {
        return effSndMss(mss());
    }
}
