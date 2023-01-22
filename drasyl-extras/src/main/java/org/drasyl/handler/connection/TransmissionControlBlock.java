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
import org.drasyl.util.SerialNumberArithmetic;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.Segment.ACK;
import static org.drasyl.handler.connection.Segment.MAX_SEQ_NO;
import static org.drasyl.handler.connection.Segment.MIN_SEQ_NO;
import static org.drasyl.handler.connection.Segment.SEQ_NO_SPACE;
import static org.drasyl.handler.connection.Segment.advanceSeq;
import static org.drasyl.handler.connection.SegmentOption.MAXIMUM_SEGMENT_SIZE;
import static org.drasyl.handler.connection.State.ESTABLISHED;
import static org.drasyl.util.Preconditions.requireInRange;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.Preconditions.requirePositive;
import static org.drasyl.handler.connection.Segment.add;
import static org.drasyl.handler.connection.Segment.greaterThan;
import static org.drasyl.handler.connection.Segment.lessThan;
import static org.drasyl.handler.connection.Segment.lessThanOrEqualTo;
import static org.drasyl.handler.connection.Segment.sub;

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
    private static final Logger LOG = LoggerFactory.getLogger(TransmissionControlBlock.class);
    private static final boolean NODELAY = false; // bypass Nagle Delays by disabling Nagle's algorithm
    final SendBuffer sendBuffer;
    final RetransmissionQueue retransmissionQueue;
    private final OutgoingSegmentQueue outgoingSegmentQueue;
    private final ReceiveBuffer receiveBuffer;
    protected long ssthresh; // slow start threshold
    // congestion control
    long cwnd; // congestion window
    long lastAdvertisedWindow;
    // Receive Sequence Variables
    private long rcvNxt; // next sequence number expected on an incoming segments, and is the left or lower edge of the receive window
    private long rcvWnd; // receive window
    private long allowedBytesToFlush = -1;
    // Send Sequence Variables
    private long sndUna; // oldest unacknowledged sequence number
    private long sndNxt; // next sequence number to be sent
    private long sndWnd; // send window
    private long sndWl1; // segment sequence number used for last window update
    private long sndWl2; // segment acknowledgment number used for last window update
    private long iss; // initial send sequence number
    private final long rcvBuff;
    private long irs; // initial receive sequence number
    private int mss; // maximum segment size
    // sender's silly window syndrome avoidance algorithm (Nagle algorithm)
    private long maxSndWnd;
    private int duplicateAcks;

    @SuppressWarnings("java:S107")
    TransmissionControlBlock(final long sndUna,
                             final long sndNxt,
                             final int sndWnd,
                             final long iss,
                             final long rcvNxt,
                             final long rcvWnd,
                             final long rcvBuff,
                             final long irs,
                             final SendBuffer sendBuffer,
                             final OutgoingSegmentQueue outgoingSegmentQueue,
                             final RetransmissionQueue retransmissionQueue,
                             final ReceiveBuffer receiveBuffer,
                             final int mss,
                             final long cwnd,
                             final long ssthresh,
                             final long maxSndWnd) {
        this.sndUna = requireInRange(sndUna, MIN_SEQ_NO, MAX_SEQ_NO);
        this.sndNxt = requireInRange(sndNxt, MIN_SEQ_NO, MAX_SEQ_NO);
        this.sndWnd = requireNonNegative(sndWnd);
        this.iss = requireInRange(iss, MIN_SEQ_NO, MAX_SEQ_NO);
        this.rcvNxt = requireInRange(rcvNxt, MIN_SEQ_NO, MAX_SEQ_NO);
        this.rcvWnd = requireNonNegative(rcvWnd);
        this.rcvBuff = requirePositive(rcvWnd);
        this.irs = requireInRange(irs, MIN_SEQ_NO, MAX_SEQ_NO);
        this.sendBuffer = requireNonNull(sendBuffer);
        this.outgoingSegmentQueue = requireNonNull(outgoingSegmentQueue);
        this.retransmissionQueue = requireNonNull(retransmissionQueue);
        this.receiveBuffer = requireNonNull(receiveBuffer);
        this.mss = requirePositive(mss);
        this.cwnd = requireNonNegative(cwnd);
        this.ssthresh = requireNonNegative(ssthresh);
        this.maxSndWnd = requireNonNegative(maxSndWnd);
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
        this(sndUna, sndNxt, sndWnd, iss, rcvNxt, rcvWnd, rcvWnd, irs, sendBuffer, new OutgoingSegmentQueue(), retransmissionQueue, receiveBuffer, mss, mss * 3L, rcvWnd, sndWnd);
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

    public long rcvWnd() {
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
        sendBuffer.release();
        receiveBuffer.release();
    }

    public ReceiveBuffer receiveBuffer() {
        return receiveBuffer;
    }

    public boolean isDuplicateAck(final Segment seg) {
        return seg.isOnlyAck() && lessThanOrEqualTo(seg.ack(), sndUna) && seg.len() == 0;
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
               final boolean newFlush) {
        try {
            if (newFlush) {
                // merke dir wie viel byes wir jetzt im buffer haben und verwende auch nur bis dahin
                allowedBytesToFlush = sendBuffer.readableBytes();
            }

            if (state != ESTABLISHED || allowedBytesToFlush < 1) {
                return;
            }

            if (!NODELAY) {
                // apply Nagle algorithm, which aims to coalesce short segments (sender's SWS avoidance algorithm)
                // https://www.rfc-editor.org/rfc/rfc9293.html#section-3.8.6.2.1
                // The "usable window" is: U = SND.UNA + SND.WND - SND.NXT
                final long u = sub(add(sndUna, sndWnd), sndNxt);
                // D is the amount of data queued in the sending TCP endpoint but not yet sent
                final long d = allowedBytesToFlush;
                // effective send MSS: equal to MSS?
                final long effSndMSS = mss;

                // Send data...
                final double fs = 0.5; // Nagle algorithm: Fs is a fraction whose recommended value is 1/2
                final boolean sendData;
                if (Math.min(d, u) >= effSndMSS) {
                    // ..if a maximum-sized segment can be sent, i.e., if:
                    // min(D,U) >= Eff.snd.MSS;
                    sendData = true;
                } else if (sndNxt == sndUna && d <= u) {
                    // or if the data is pushed and all queued data can be sent now, i.e., if:
                    // SND.NXT = SND.UNA and D <= U
                    sendData = true;
                } else if (sndNxt == sndUna && Math.min(d, u) >= fs * maxSndWnd) {
                    // or if at least a fraction Fs of the maximum window can be sent, i.e., if:
                    // SND.NXT = SND.UNA and min(D,U) >= Fs * Max(SND.WND);
                    sendData = true;
                }
                // FIXME: or if the override timeout occurs
                else {
                    sendData = false;
                }

                if (!sendData) {
                    LOG.trace("{} Sender's SWS avoidance: No send condition met. Delay data.", ctx.channel());
                    return;
                }
            }

            final long window = Math.min(sndWnd(), cwnd());
            LOG.trace("{}[{}] Flush of SND.BUF was triggered: SND.WND={}; SND.BUF={}; FLIGHT SIZE={}; CWND={}; {} flushable bytes.", ctx.channel(), state, sndWnd(), sendBuffer.readableBytes(), flightSize(), cwnd(), allowedBytesToFlush);

            // at least one byte is required for Zero-Window Probing
            long unusedSendWindow = Math.max(0, window - flightSize());
            if (newFlush && window == 0 && flightSize() == 0 && unusedSendWindow == 0) {
                // zero window probing
                unusedSendWindow = 1;
            }

            final long remainingBytes = Math.min(Math.min(unusedSendWindow, sendBuffer.readableBytes()), allowedBytesToFlush);

            if (remainingBytes > 0) {
                LOG.error("{}[{}] {} bytes in-flight. Send window of {} bytes allows us to write {} new bytes to network. {} application bytes wait to be written. Write {} bytes.", ctx.channel(), state, flightSize(), window, unusedSendWindow, allowedBytesToFlush, remainingBytes);
            }

            writeBytes(sndNxt, remainingBytes, rcvNxt);
            allowedBytesToFlush -= remainingBytes;
        } finally {
            outgoingSegmentQueue.flush(ctx, this);
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
            mss = (int) mssOption;
        }
    }

    public void handleAcknowledgement(final ChannelHandlerContext ctx,
                                      final Segment seg) {
        long ackedBytes = 0;
        if (sndUna != seg.ack()) {
            LOG.trace("{} Got `{}`. Advance SND.UNA from {} to {} (+{}).", ctx.channel(), seg, sndUna(), seg.ack(), SerialNumberArithmetic.sub(seg.ack(), sndUna(), SEQ_NO_SPACE));
            ackedBytes = sub(seg.ack(), sndUna);
            sndUna = seg.ack();
        }

        final byte ackedCtl = retransmissionQueue.handleAcknowledgement(ctx, seg, this, ackedBytes);

        // FIXME: If the SYN or SYN/ACK is lost, the initial window used by a
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
                final long increment = Math.min(mss, ackedBytes);
                LOG.trace("{} Congestion Control: Slow Start: {} new bytes has ben ACKed. Increase cwnd by {} from {} to {}.", ctx.channel(), ackedBytes, increment, cwnd, cwnd + increment);
                cwnd += increment;
            } else {
                // Congestion Avoidance -> +1 MSS after each RTT
                final long increment = (long) Math.ceil(((long) mss * mss) / (float) cwnd);
                LOG.trace("{} Congestion Control: Congestion Avoidance: {} new bytes has ben ACKed. Increase cwnd by {} from {} to {}.", ctx.channel(), ackedBytes, increment, cwnd, cwnd + increment);
                cwnd += increment;
            }

//            if (duplicateAcks != 0) {
//                duplicateAcks = 0;
//                cwnd = ssthresh;
//                LOG.error("{} ACKed new data (`{}`). Reset duplicate ACKs counter. Set CWND to SSTHRESH.", ctx.channel(), seg);
//            }
        }
    }

    public void synchronizeState(final Segment seg) {
        rcvNxt = advanceSeq(seg.seq(), seg.len());
        irs = seg.seq();
    }

    public void updateSndWnd(final Segment seg) {
        sndWnd = seg.window();
        sndWl1 = seg.seq();
        sndWl2 = seg.ack();
        maxSndWnd = Math.max(maxSndWnd, sndWnd);
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
        } else if (seg.len() == 0 && rcvWnd > 0) {
            // RCV.NXT =< SEG.SEQ < RCV.NXT+RCV.WND
            return lessThanOrEqualTo(rcvNxt, seg.seq()) && lessThan(seg.seq(), add(rcvNxt, rcvWnd));
        } else if (seg.len() > 0 && rcvWnd == 0) {
            // not acceptable
            return false;
        } else {
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
        // FIXME
        if (sendBuffer.hasOutstandingData() && seg.len() == 0 && !seg.isSyn() && !seg.isFin() && seg.ack() == sndUna && seg.window() == lastAdvertisedWindow) {
//            if (doFastRetransmit()) {
//                final long currentWindowSize = Math.min(cwnd, sndWnd);
//                final long newSsthresh = Math.max(currentWindowSize / 2, 2L * mss);
//                if (ssthresh != newSsthresh) {
//                    LOG.error("{} Congestion Control: Duplicate ACK. Set ssthresh from {} to {}.", ctx.channel(), ssthresh, newSsthresh);
//                    ssthresh = newSsthresh;
//                }
//
//                duplicateAcks += 1;
//            }
        } else {
//            if (doFastRetransmit()) {
//                duplicateAcks = 0;
//            }
        }

//        if (doFastRetransmit()) {
//            // Since TCP does not know whether a duplicate ACK is caused by a lost
//            //   segment or just a reordering of segments, it waits for a small number
//            //   of duplicate ACKs to be received.  It is assumed that if there is
//            //   just a reordering of the segments, there will be only one or two
//            //   duplicate ACKs before the reordered segment is processed, which will
//            //   then generate a new ACK.  If three or more duplicate ACKs are
//            //   received in a row, it is a strong indication that a segment has been
//            //   lost.
//            if (duplicateAcks == 3) {
//                LOG.error("{} Congestion Control: Fast Retransmit: Got 3 duplicate ACKs in a row.", ctx.channel(), duplicateAcks);
//
//                // TCP then performs a retransmission of what appears to be the
//                //   missing segment, without waiting for a retransmission timer to
//                //   expire.
//                // The lost segment starting at SND.UNA MUST be retransmitted...
//                final ConnectionHandshakeSegment current = retransmissionQueue.retransmissionSegment(ctx, this);
//                LOG.error("{} Congestion Control: Fast Retransmit: Retransmit SEG `{}`.", ctx.channel(), current);
//                ctx.writeAndFlush(current);
//
//                // Fast recovery
//                final long newSsthresh = Math.max(cwnd / 2, 2L * mss);
//                LOG.error("{} Congestion Control: Fast Recovery: Set ssthresh from {} to {}.", ctx.channel(), ssthresh, newSsthresh);
//                ssthresh = newSsthresh;
//
//                LOG.error("{} Congestion Control: Fast Recovery: Set cwnd from {} to {}.", ctx.channel(), cwnd, ssthresh + 3L * mss);
//                cwnd = ssthresh + 3L * mss;
//
////            // When the third duplicate ACK is received, a TCP MUST set ssthresh
////            //       to no more than the value given in equation (4)
////            ssthresh = Math.max(sendBuffer.acknowledgeableBytes() / 2, 2L * mss);
////            LOG.error("{} Set SSTHRESH to {}.", ctx.channel(), ssthresh);
////
//
////
////            // ... and cwnd set to ssthresh plus 3*SMSS.
////            cwnd = ssthresh + 3L * mss;
////            LOG.error("{} Set CWND to {}.", ctx.channel(), cwnd);
////        }
//            }
//            else {
//                LOG.error("{} Congestion Control: Fast Recovery: Another duplicate ACK. Set cwnd from {} to {}.", ctx.channel(), cwnd, cwnd + mss);
//                cwnd += mss;
//            }
//        }
    }

    private boolean doFastRetransmit() {
        return false;
    }

    public void advanceRcvNxt(final ChannelHandlerContext ctx, final int advancement) {
        rcvNxt = advanceSeq(rcvNxt, advancement);
        LOG.trace("{} Advance RCV.NXT to {}.", ctx.channel(), rcvNxt);
    }

    public void decrementRcvWnd(final int decrement) {
        rcvWnd -= decrement;
    }

    public void incrementRcvWnd(final ChannelHandlerContext ctx) {
        // receiver's SWS avoidance algorithms
        // RFC 9293, Section 3.8.6.2.2
        // https://www.rfc-editor.org/rfc/rfc9293.html#section-3.8.6.2.2

        // total receive buffer space is RCV.BUFF
        // RCV.USER octets of this total may be tied up with data that has been received and acknowledged but that the user process has not yet consumed
        long rcvUser = receiveBuffer.readableBytes();
        // effective send MSS: equal to MSS?
        final long effSndMSS = mss;
        final double fr = 0.5; // Fr is a fraction whose recommended value is 1/2

        if (rcvBuff() - rcvUser - rcvWnd >= Math.min(fr * rcvBuff(), effSndMSS)) {
            final long newRcvWind = rcvBuff() - rcvUser;
            LOG.error("{} Receiver's SWS avoidance: Advance RCV.WND from {} to {} (+{}).", ctx.channel(), rcvWnd, newRcvWind, newRcvWind - rcvWnd);
            rcvWnd = newRcvWind;
        }
    }

    long rcvBuff() {
        return rcvBuff;
    }

    long rcvUser() {
        return receiveBuffer.readableBytes();
    }

    public long maxSndWnd() {
        return maxSndWnd;
    }
}
