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

import io.netty.channel.Channel;

import java.util.Objects;

import static org.drasyl.handler.connection.ConnectionHandshakeHandler.SEQ_NO_SPACE;
import static org.drasyl.util.SerialNumberArithmetic.add;
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
    // Send Sequence Variables
    long sndUna; // oldest unacknowledged sequence number
    long sndNxt; // next sequence number to be sent
    int sndWnd; // send window
    long iss; // initial send sequence number
    // Receive Sequence Variables
    long rcvNxt; // next sequence number expected on an incoming segments, and is the left or lower edge of the receive window
    int rcvWnd; // receive window
    long irs; // initial receive sequence number
    SendBuffer sendBuffer;
    RetransmissionQueue retransmissionQueue;
    ReceiveBuffer receiveBuffer;
    OutgoingSegmentQueue outgoingSegmentQueue;
    RttMeasurement rttMeasurement;

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
                             final OutgoingSegmentQueue outgoingSegmentQueue,
                             final RttMeasurement rttMeasurement) {
        this.sndUna = sndUna;
        this.sndNxt = sndNxt;
        this.sndWnd = sndWnd;
        this.iss = iss;
        this.rcvNxt = rcvNxt;
        this.rcvWnd = rcvWnd;
        this.irs = irs;
        this.sendBuffer = sendBuffer;
        this.retransmissionQueue = retransmissionQueue;
        this.receiveBuffer = receiveBuffer;
        this.outgoingSegmentQueue = outgoingSegmentQueue;
        this.rttMeasurement = rttMeasurement;
    }

    private TransmissionControlBlock(final Channel channel,
                                     final long sndUna,
                                     final long sndNxt,
                                     final int sndWnd,
                                     final long iss,
                                     final long rcvNxt,
                                     final int rcvWnd,
                                     final long irs,
                                     final SendBuffer sendBuffer,
                                     final RetransmissionQueue retransmissionQueue,
                                     final ReceiveBuffer receiveBuffer,
                                     final RttMeasurement rttMeasurement) {
        this(sndUna, sndNxt, sndWnd, iss, rcvNxt, rcvWnd, irs, sendBuffer, retransmissionQueue, receiveBuffer, new OutgoingSegmentQueue(channel, retransmissionQueue, rttMeasurement), rttMeasurement);
    }

    public TransmissionControlBlock(final Channel channel,
                                    final long sndUna,
                                    final long iss,
                                    final long irs,
                                    final int windowSize) {
        this(channel, sndUna, add(iss, 1, SEQ_NO_SPACE), windowSize, iss, irs, windowSize, irs, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement());
    }

    public TransmissionControlBlock(final Channel channel, final long sndUna, final long iss, final long irs) {
        this(channel, sndUna, iss, irs, 3_000);
    }

    public TransmissionControlBlock(final Channel channel, final long iss, final long irs) {
        this(channel, iss, iss, irs);
    }

    public TransmissionControlBlock(final Channel channel, final long iss) {
        this(channel, iss, 0);
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

    boolean isAcceptableAck(final ConnectionHandshakeSegment seg) {
        return seg.isAck() && lessThan(sndUna, seg.ack(), SEQ_NO_SPACE) && lessThanOrEqualTo(seg.ack(), sndNxt, SEQ_NO_SPACE);
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

    public SendBuffer sendBuffer() {
        return sendBuffer;
    }

    public RetransmissionQueue retransmissionQueue() {
        return retransmissionQueue;
    }

    public ReceiveBuffer receiveBuffer() {
        return receiveBuffer;
    }

    public OutgoingSegmentQueue outgoingSegmentQueue() {
        return outgoingSegmentQueue;
    }

    public RttMeasurement rttMeasurement() {
        return rttMeasurement;
    }
}
