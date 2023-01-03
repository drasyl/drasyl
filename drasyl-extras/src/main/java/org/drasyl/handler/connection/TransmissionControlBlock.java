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
import static org.drasyl.handler.connection.RetransmissionTimeoutApplier.ALPHA;
import static org.drasyl.handler.connection.RetransmissionTimeoutApplier.BETA;
import static org.drasyl.handler.connection.RetransmissionTimeoutApplier.LOWER_BOUND;
import static org.drasyl.handler.connection.RetransmissionTimeoutApplier.UPPER_BOUND;
import static org.drasyl.handler.connection.State.ESTABLISHED;
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
class TransmissionControlBlock {
    private static final Logger LOG = LoggerFactory.getLogger(TransmissionControlBlock.class);
    final SendBuffer sendBuffer;
    private final OutgoingSegmentQueue outgoingSegmentQueue;
    private final RetransmissionQueue retransmissionQueue;
    private final ReceiveBuffer receiveBuffer;
    private final RttMeasurement rttMeasurement;
    private int allowedBytesToFlush = -1;
    private long rtt = -1;
    private long srtt;
    private long rto;
    // Send Sequence Variables
    private long sndUna; // oldest unacknowledged sequence number
    private long sndNxt; // next sequence number to be sent
    private int sndWnd; // send window
    private long iss; // initial send sequence number
    // Receive Sequence Variables
    private long rcvNxt; // next sequence number expected on an incoming segments, and is the left or lower edge of the receive window
    private int rcvWnd; // receive window
    private long irs; // initial receive sequence number
    private int mss; // maximum segment size

    @SuppressWarnings("java:S107")
    TransmissionControlBlock(final long sndUna,
                             final long sndNxt,
                             final int sndWnd,
                             final long iss,
                             final long rcvNxt,
                             final int rcvWnd,
                             final long irs,
                             final SendBuffer sendBuffer,
                             final OutgoingSegmentQueue outgoingSegmentQueue,
                             final RetransmissionQueue retransmissionQueue,
                             final ReceiveBuffer receiveBuffer,
                             final RttMeasurement rttMeasurement,
                             final int mss) {
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
    }

    @SuppressWarnings("java:S107")
    private TransmissionControlBlock(final long sndUna,
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
        this(sndUna, sndNxt, sndWnd, iss, rcvNxt, rcvWnd, irs, sendBuffer, new OutgoingSegmentQueue(retransmissionQueue, rttMeasurement), retransmissionQueue, receiveBuffer, rttMeasurement, mss);
    }

    public TransmissionControlBlock(final Channel channel,
                                    final long sndUna,
                                    final long iss,
                                    final long irs,
                                    final int windowSize,
                                    final int mss) {
        this(sndUna, iss, windowSize, iss, irs, windowSize, irs, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), mss);
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
                                    final long sndUna,
                                    final long iss,
                                    final long irs) {
        // window size sollte ein vielfaches von mss betragen
        this(channel, sndUna, iss, irs, 1220 * 64, 1220);
    }

    public TransmissionControlBlock(final Channel channel,
                                    final long sndUna,
                                    final long sndNxt,
                                    final long iss,
                                    final long irs) {
        // window size sollte ein vielfaches von mss betragen
        this(channel, sndUna, sndNxt, iss, irs, 1220 * 64, 1220);
    }

    public TransmissionControlBlock(final Channel channel, final long iss, final long irs) {
        this(channel, iss, iss, irs);
    }

    public TransmissionControlBlock(final Channel channel, final long iss) {
        this(channel, iss, 0);
    }

    public TransmissionControlBlock(final Channel channel,
                                    final long iss,
                                    final int windowSize,
                                    final int mss) {
        this(channel, iss, iss, 0, windowSize, mss);
    }

    public TransmissionControlBlock(final Channel channel,
                                    final long iss,
                                    final long irs,
                                    final int windowSize,
                                    final int mss) {
        this(channel, iss, iss, irs, windowSize, mss);
    }

    public long sndUna() {
        return sndUna;
    }

    public long sndNxt() {
        return sndNxt;
    }

    public int sndWnd() {
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

    public void mss(final int mss) {
        this.mss = mss;
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
                '}';
    }

    int sequenceNumbersAllowedForNewDataTransmission() {
        // FIXME: overflow?
        return (int) (sndUna + sndWnd - sndNxt);
    }

    public void delete(final Throwable cause) {
        sendBuffer.releaseAndFailAll(cause);
        retransmissionQueue.releaseAndFailAll(cause);
        receiveBuffer.release();
    }

    public ReceiveBuffer receiveBuffer() {
        return receiveBuffer;
    }

    public RttMeasurement rttMeasurement() {
        return rttMeasurement;
    }

    public boolean isDuplicateAck(final ConnectionHandshakeSegment seg) {
        // FIXME: im RFC 9293 steht <= anstelle von <
        return seg.isAck() && lessThan(seg.ack(), sndUna, SEQ_NO_SPACE);
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

    public boolean isAckOurSyn(final ConnectionHandshakeSegment seg) {
        return seg.isAck() && lessThanOrEqualTo(sndUna, seg.ack(), SEQ_NO_SPACE) && lessThanOrEqualTo(seg.ack(), sndNxt, SEQ_NO_SPACE);
    }

    public boolean isAckSomethingNeverSent(final ConnectionHandshakeSegment seg) {
        return seg.isAck() && (lessThanOrEqualTo(seg.ack(), iss, SEQ_NO_SPACE) || greaterThan(seg.ack(), sndNxt, SEQ_NO_SPACE));
    }

    private void writeBytes(long seg,
                            int readableBytes,
                            long ack) {
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

    private void writeWithout(long seq,
                              int readableBytes,
                              long ack,
                              int ctl) {
        outgoingSegmentQueue.addBytes(seq, readableBytes, ack, ctl);
    }

    void writeAndFlush(final ChannelHandlerContext ctx,
                       final ConnectionHandshakeSegment seg) {
        write(seg);
        outgoingSegmentQueue.flush(ctx, sendBuffer, mss);
    }

    void add(final ByteBuf data, final ChannelPromise promise) {
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

            if (state != ESTABLISHED || allowedBytesToFlush == -1) {
                return;
            }

            final int allowedBytesForNewTransmission = sequenceNumbersAllowedForNewDataTransmission();
            LOG.trace("{}[{}] Flush of write buffer was triggered. {} sequence numbers are allowed to write to the network. {} bytes in send buffer. {} bytes allowed to flush. MSS={}", ctx.channel(), state, allowedBytesForNewTransmission, sendBuffer.readableBytes(), allowedBytesToFlush, mss());

            final int readableBytesInBuffer = sendBuffer.readableBytes();
            int remainingBytes = Math.min(Math.min(allowedBytesForNewTransmission, readableBytesInBuffer), allowedBytesToFlush);

            LOG.trace("{}[{}] Write {} bytes to network", ctx.channel(), state, remainingBytes);
            writeBytes(sndNxt, remainingBytes, rcvNxt);
        }
        finally {
            outgoingSegmentQueue.flush(ctx, sendBuffer, mss);
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

    public long rto() {
        return rto;
    }

    public void initRto(final ChannelHandlerContext ctx) {
        srtt = (long) (ALPHA * srtt + (1 - ALPHA) * rtt);
        rto = Math.min(UPPER_BOUND, Math.max(LOWER_BOUND, (long) (BETA * srtt)));
    }

    public void handleAcknowledgement(final ChannelHandlerContext ctx,
                                      final ConnectionHandshakeSegment seg) {
        sndUna = seg.ack();

        ConnectionHandshakeSegment current = retransmissionQueue.current();
        while (current != null && isFullyAcknowledged(current)) {
            LOG.trace("{} Segment `{}` has been fully ACKnowledged. Remove from retransmission queue. {} writes remain in retransmission queue.", ctx.channel(), current, retransmissionQueue.size() - 1);
            retransmissionQueue.removeAndSucceedCurrent();

            current = retransmissionQueue.current();
        }
    }

    public void synchronizeReceiveState(final ConnectionHandshakeSegment seg) {
        rcvNxt = advanceSeq(seg.seq(), seg.len());
        irs = seg.seq();
    }

    public void receive(final ConnectionHandshakeSegment seg) {
        if (seg.content().isReadable()) {
            receiveBuffer.add(seg.content().retain());
        }
        this.rcvNxt = advanceSeq(rcvNxt(), seg.len());
    }
}
