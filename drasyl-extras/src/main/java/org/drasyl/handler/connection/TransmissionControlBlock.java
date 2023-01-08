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
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

import static org.drasyl.handler.connection.ConnectionHandshakeSegment.ACK;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.Option.MAXIMUM_SEGMENT_SIZE;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.SEQ_NO_SPACE;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.advanceSeq;
import static org.drasyl.handler.connection.State.ESTABLISHED;
import static org.drasyl.util.SerialNumberArithmetic.add;
import static org.drasyl.util.SerialNumberArithmetic.greaterThan;
import static org.drasyl.util.SerialNumberArithmetic.lessThan;
import static org.drasyl.util.SerialNumberArithmetic.lessThanOrEqualTo;

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
    final SendBuffer sendBuffer;
    private final OutgoingSegmentQueue outgoingSegmentQueue;
    private final RetransmissionQueue retransmissionQueue;
    private final ReceiveBuffer receiveBuffer;
    private final RttMeasurement rttMeasurement;
    // Receive Sequence Variables
    long rcvNxt; // next sequence number expected on an incoming segments, and is the left or lower edge of the receive window
    long rcvWnd; // receive window
    private long allowedBytesToFlush = -1;
    // Send Sequence Variables
    private long sndUna; // oldest unacknowledged sequence number
    private long sndNxt; // next sequence number to be sent
    private long sndWnd; // send window
    private long sndWl1; // segment sequence number used for last window update
    private long sndWl2; // segment acknowledgment number used for last window update
    private long iss; // initial send sequence number
    private long irs; // initial receive sequence number
    private int mss; // maximum segment size
    private long cwnd; // congestion window
    private long ssthresh; // slow start threshold
    private int duplicateAcks;

    @SuppressWarnings("java:S107")
    TransmissionControlBlock(final long sndUna,
                             final long sndNxt,
                             final int sndWnd,
                             final long iss,
                             final long rcvNxt,
                             final long rcvWnd,
                             final long irs,
                             final SendBuffer sendBuffer,
                             final OutgoingSegmentQueue outgoingSegmentQueue,
                             final RetransmissionQueue retransmissionQueue,
                             final ReceiveBuffer receiveBuffer,
                             final RttMeasurement rttMeasurement,
                             final int mss,
                             final long cwnd,
                             final long ssthresh) {
        this.sndUna = sndUna;
        this.sndNxt = sndNxt;
        this.sndWnd = sndWnd;
        this.iss = iss;
        this.rcvNxt = rcvNxt;
        this.rcvWnd = rcvWnd;
        this.irs = irs;
        this.sendBuffer = sendBuffer;
        this.outgoingSegmentQueue = outgoingSegmentQueue;
        this.retransmissionQueue = retransmissionQueue;
        this.receiveBuffer = receiveBuffer;
        this.rttMeasurement = rttMeasurement;
        this.mss = mss;
        this.cwnd = cwnd;
        this.ssthresh = ssthresh;
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
                             final RttMeasurement rttMeasurement,
                             final int mss) {
        // IW, the initial value of cwnd, MUST be set using the following guidelines as an upper bound.
        //
        // If SMSS > 2190 bytes:
        //  IW = 2 * SMSS bytes and MUST NOT be more than 2 segments
        // If (SMSS > 1095 bytes) and (SMSS <= 2190 bytes):
        //  IW = 3 * SMSS bytes and MUST NOT be more than 3 segments
        // if SMSS <= 1095 bytes:
        //  IW = 4 * SMSS bytes and MUST NOT be more than 4 segments
        this(sndUna, sndNxt, sndWnd, iss, rcvNxt, rcvWnd, irs, sendBuffer, new OutgoingSegmentQueue(retransmissionQueue, rttMeasurement), retransmissionQueue, receiveBuffer, rttMeasurement, mss, 3 * mss, rcvWnd);
    }

    public TransmissionControlBlock(final Channel channel,
                                    final long sndUna,
                                    final long sndNxt,
                                    final int sndWnd,
                                    final long iss,
                                    final int rcvWnd,
                                    final long irs,
                                    final int mss) {
        this(sndUna, sndNxt, sndWnd, iss, irs, rcvWnd, irs, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), mss);
    }

    public TransmissionControlBlock(final Channel channel,
                                    final long sndUna,
                                    final long sndNxt,
                                    final long iss,
                                    final long irs,
                                    final int windowSize,
                                    final int mss) {
        this(sndUna, sndNxt, windowSize, iss, irs, windowSize, irs, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), mss);
    }

    public TransmissionControlBlock(final Channel channel,
                                    final long iss,
                                    final int rcvWnd,
                                    final int mss) {
        this(iss, iss, 0, iss, 0, rcvWnd, 0, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), mss);
    }

    public TransmissionControlBlock(final Channel channel,
                                    final long iss,
                                    final long irs,
                                    final int windowSize,
                                    final int mss) {
        this(iss, iss, windowSize, iss, irs, windowSize, irs, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), mss);
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
                ", SND.BUF=" + sendBuffer +
                ", OG.SEG.Q=" + outgoingSegmentQueue +
                ", RTNS.Q=" + retransmissionQueue +
                ", RCV.BUF=" + receiveBuffer +
                ", MSS=" + mss +
                ", CWND=" + cwnd +
                ", SSTHRESH=" + ssthresh +
                '}';
    }

    public void delete(final Throwable cause) {
        sendBuffer.releaseAndFailAll(cause);
        retransmissionQueue.releaseAndFailAll();
        receiveBuffer.release();
    }

    public ReceiveBuffer receiveBuffer() {
        return receiveBuffer;
    }

    public RttMeasurement rttMeasurement() {
        return rttMeasurement;
    }

    public boolean isDuplicateAck(final ConnectionHandshakeSegment seg) {
        return seg.isOnlyAck() && lessThanOrEqualTo(seg.ack(), sndUna, SEQ_NO_SPACE) && seg.len() == 0;
    }

    public boolean isAckSomethingNotYetSent(final ConnectionHandshakeSegment seg) {
        return seg.isAck() && greaterThan(seg.ack(), sndNxt, SEQ_NO_SPACE);
    }

    public boolean isFullyAcknowledged(final ConnectionHandshakeSegment seg) {
        return lessThanOrEqualTo(seg.lastSeq(), sndUna, SEQ_NO_SPACE);
    }

    public boolean isAcceptableAck(final ConnectionHandshakeSegment seg) {
        return seg.isAck() && lessThan(sndUna, seg.ack(), SEQ_NO_SPACE) && lessThanOrEqualTo(seg.ack(), sndNxt, SEQ_NO_SPACE);
    }

    public boolean synHasBeenAcknowledged() {
        return greaterThan(sndUna, iss, SEQ_NO_SPACE);
    }

    public boolean isAckOurSynOrFin(final ConnectionHandshakeSegment seg) {
        return seg.isAck() && lessThan(sndUna, seg.ack(), SEQ_NO_SPACE) && lessThanOrEqualTo(seg.ack(), sndNxt, SEQ_NO_SPACE);
    }

    public boolean isAckSomethingNeverSent(final ConnectionHandshakeSegment seg) {
        return seg.isAck() && (lessThanOrEqualTo(seg.ack(), iss, SEQ_NO_SPACE) || greaterThan(seg.ack(), sndNxt, SEQ_NO_SPACE));
    }

    private void writeBytes(final long seg,
                            final long readableBytes,
                            final long ack) {
        if (readableBytes > 0) {
            sndNxt = advanceSeq(sndNxt, readableBytes);
            writeWithout(seg, readableBytes, ack, ACK);
        }
    }

    void write(final ConnectionHandshakeSegment seg) {
        final int len = seg.len();
        if (len > 0) {
            sndNxt = advanceSeq(sndNxt, len);
        }
        writeWithout(seg);
    }

    void writeWithout(final ConnectionHandshakeSegment seg) {
        writeWithout(seg.seq(), seg.content().readableBytes(), seg.ack(), seg.ctl());
    }

    private void writeWithout(final long seq,
                              final long readableBytes,
                              final long ack,
                              final int ctl) {
        outgoingSegmentQueue.addBytes(seq, readableBytes, ack, ctl);
    }

    void writeAndFlush(final ChannelHandlerContext ctx,
                       final ConnectionHandshakeSegment seg) {
        write(seg);
        outgoingSegmentQueue.flush(ctx, this);
    }

    void addToSendBuffer(final ByteBuf data, final ChannelPromise promise) {
        sendBuffer.add(data, promise);
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

            final long flightSize = retransmissionQueue.bytes();
            final long sendWindow = sndWnd();
            final long congestionWindow = cwnd();
            LOG.trace("{}[{}] Flush of SND.BUF was triggered: SND.WND={}; SND.BUF={}; FLIGHT SIZE={}; CWND={}; {} flushable bytes.", ctx.channel(), state, sendWindow, sendBuffer.readableBytes(), flightSize, congestionWindow, allowedBytesToFlush);

            // at least one byte is required for Zero-Window Probing
            final long max = Math.max(newFlush ? 1 : 0, Math.min(sendWindow, congestionWindow) - flightSize);
            long remainingBytes = Math.min(Math.min(max, sendBuffer.readableBytes()), allowedBytesToFlush);

            LOG.trace("{}[{}] Write {} bytes to network.", ctx.channel(), state, remainingBytes);

            writeBytes(sndNxt, remainingBytes, rcvNxt);
            allowedBytesToFlush -= remainingBytes;
        }
        finally {
            outgoingSegmentQueue.flush(ctx, this);
        }
    }

    public void negotiateMss(final ChannelHandlerContext ctx,
                             final ConnectionHandshakeSegment seg) {
        final Object mssOption = seg.options().get(MAXIMUM_SEGMENT_SIZE);
        if (mssOption != null && (int) mssOption < mss) {
            LOG.trace("{}[{}] Remote peer sent MSS {}. This is smaller then our MSS {}. Reduce our MSS.", ctx.channel(), mssOption, mss);
            mss = (int) mssOption;
        }
    }

    public void handleAcknowledgement(final ChannelHandlerContext ctx,
                                      final ConnectionHandshakeSegment seg) {
        if (sndUna != seg.ack()) {
            LOG.trace("{} Got `{}`. Advance SND.UNA from {} to {}.", ctx.channel(), seg, sndUna(), seg.ack());
            sndUna = seg.ack();
        }

        final long bytes1 = retransmissionQueue.bytes();
        retransmissionQueue.handleAcknowledgement(ctx, seg, this, rttMeasurement);
        final long bytes2 = retransmissionQueue.bytes();
        final long ackedBytes = bytes1 - bytes2;

        if (ackedBytes > 0) {
            if (doSlowStart()) {
                // Slow Start -> +1 MSS after each ACK
                cwnd += Math.min(mss, ackedBytes);
            }
            else {
                // Congestion Avoidance -> +1 MSS after each RTT
                cwnd += Math.ceil(((long) mss * mss) / (float) cwnd);
            }

            if (duplicateAcks != 0) {
                duplicateAcks = 0;
                cwnd = ssthresh;
                LOG.error("{} ACKed new data. Reset duplicate ACKs counter. Set CWND to SSTHRESH.", ctx.channel());
            }
        }
    }

    public void synchronizeState(final ConnectionHandshakeSegment seg) {
        rcvNxt = advanceSeq(seg.seq(), seg.len());
        irs = seg.seq();
    }

    public void updateSndWnd(final ConnectionHandshakeSegment seg) {
        sndWnd = seg.window();
        sndWl1 = seg.seq();
        sndWl2 = seg.ack();
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

    public boolean isAcceptableAck2(final ConnectionHandshakeSegment seg) {
        if (!seg.isAck()) {
            return false;
        }

        if (seg.len() == 0 && rcvWnd == 0) {
            // SEG.SEQ = RCV.NXT
            return seg.seq() == rcvNxt;
        }
        else if (seg.len() == 0 && rcvWnd > 0) {
            // RCV.NXT =< SEG.SEQ < RCV.NXT+RCV.WND
            return lessThanOrEqualTo(rcvNxt, seg.seq(), SEQ_NO_SPACE) && lessThan(seg.seq(), add(rcvNxt, rcvWnd, SEQ_NO_SPACE), SEQ_NO_SPACE);
        }
        else if (seg.len() > 0 && rcvWnd == 0) {
            // not acceptable
            return false;
        }
        else {
            // RCV.NXT =< SEG.SEQ < RCV.NXT+RCV.WND or RCV.NXT =< SEG.SEQ+SEG.LEN-1 < RCV.NXT+RCV.WND
            return (lessThanOrEqualTo(rcvNxt, seg.seq(), SEQ_NO_SPACE) && lessThan(seg.seq(), add(rcvNxt, rcvWnd, SEQ_NO_SPACE), SEQ_NO_SPACE)) ||
                    (lessThanOrEqualTo(rcvNxt, add(seg.seq(), seg.len() - 1, SEQ_NO_SPACE), SEQ_NO_SPACE) && greaterThan(add(seg.seq(), seg.len() - 1, SEQ_NO_SPACE), add(rcvNxt, rcvWnd, SEQ_NO_SPACE), SEQ_NO_SPACE));
        }
    }

    private boolean doSlowStart() {
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

    public void gotDuplicateAck(final ChannelHandlerContext ctx) {
        duplicateAcks += 1;
        if (duplicateAcks == 3) {
            LOG.error("{} Got {} duplicate ACKs (in a row?).", ctx.channel(), duplicateAcks);

            // When the third duplicate ACK is received, a TCP MUST set ssthresh
            //       to no more than the value given in equation (4)
            ssthresh = Math.max(retransmissionQueue.bytes() / 2, 2L * mss);
            LOG.error("{} Set SSTHRESH to {}.", ctx.channel(), ssthresh);

            // The lost segment starting at SND.UNA MUST be retransmitted...
            // FIXME
            final ConnectionHandshakeSegment current = retransmissionQueue.current();
            LOG.error("{} Retransmit SEG `{}`.", ctx.channel(), current);
            ctx.writeAndFlush(current.copy());

            // ... and cwnd set to ssthresh plus 3*SMSS.
            cwnd = ssthresh + 3L * mss;
            LOG.error("{} Set CWND to {}.", ctx.channel(), cwnd);
        }
        else if (duplicateAcks > 3) {
            // For each additional duplicate ACK received (after the third),
            //       cwnd MUST be incremented by SMSS.
            cwnd += mss;
        }
    }
}
