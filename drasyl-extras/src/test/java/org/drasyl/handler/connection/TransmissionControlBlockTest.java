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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.handler.connection.Segment.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TransmissionControlBlockTest {
    @Mock
    private SendBuffer sendBuffer;
    @Mock
    private RetransmissionQueue retransmissionQueue;
    @Mock
    private ReceiveBuffer receiveBuffer;
    @Mock
    private OutgoingSegmentQueue outoingSegmentQueue;
    @Mock
    private RttMeasurement rttMeasurement;
    private int mss = 1_000;
    @Mock
    private ReliableTransportConfig config;

    @Nested
    class IsAcceptableAck {
        @Test
        void shouldReturnFalseIfSegmentIsNoAck() {
            final TransmissionControlBlock tcb = new TransmissionControlBlock(config, 9, 10, 0, 0, 0, 0, 0, 0, sendBuffer, outoingSegmentQueue, retransmissionQueue, receiveBuffer, mss, 0, 0, 0, 0, 0, 0, false, 0, 0, 1000);

            final Segment seg = Segment.syn(1);
            assertFalse(seg.isAck() && lessThan(tcb.sndUna(), seg.ack()) && lessThanOrEqualTo(seg.ack(), tcb.sndNxt()));
        }

        @Test
        void shouldReturnTrueIfAckIsAcceptable() {
            final TransmissionControlBlock tcb1 = new TransmissionControlBlock(config, 9, 10, 0, 0, 0, 0, 0, 0, sendBuffer, outoingSegmentQueue, retransmissionQueue, receiveBuffer, mss, 0, 0, 0, 0, 0, 0, false, 0, 0, 1000);
            final Segment seg3 = Segment.ack(1, 10);
            assertTrue(seg3.isAck() && lessThan(tcb1.sndUna(), seg3.ack()) && lessThanOrEqualTo(seg3.ack(), tcb1.sndNxt()));

            final TransmissionControlBlock tcb2 = new TransmissionControlBlock(config, 9, 11, 0, 0, 0, 0, 0, 0, sendBuffer, outoingSegmentQueue, retransmissionQueue, receiveBuffer, mss, 0, 0, 0, 0, 0, 0, false, 0, 0, 1000);
            final Segment seg2 = Segment.ack(1, 10);
            assertTrue(seg2.isAck() && lessThan(tcb2.sndUna(), seg2.ack()) && lessThanOrEqualTo(seg2.ack(), tcb2.sndNxt()));

            // with overflow
            final TransmissionControlBlock tcb3 = new TransmissionControlBlock(config, MAX_SEQ_NO - 1, MAX_SEQ_NO, 0, 0, 0, 0, 0, 0, sendBuffer, outoingSegmentQueue, retransmissionQueue, receiveBuffer, mss, 0, 0, 0, 0, 0, 0, false, 0, 0, 1000);
            final Segment seg1 = Segment.ack(1, MAX_SEQ_NO);
            assertTrue(seg1.isAck() && lessThan(tcb3.sndUna(), seg1.ack()) && lessThanOrEqualTo(seg1.ack(), tcb3.sndNxt()));

            final TransmissionControlBlock tcb4 = new TransmissionControlBlock(config, MAX_SEQ_NO - 1, 0, 0, 0, 0, 0, 0, 0, sendBuffer, outoingSegmentQueue, retransmissionQueue, receiveBuffer, mss, 0, 0, 0, 0, 0, 0, false, 0, 0, 1000);
            final Segment seg = Segment.ack(1, MAX_SEQ_NO);
            assertTrue(seg.isAck() && lessThan(tcb4.sndUna(), seg.ack()) && lessThanOrEqualTo(seg.ack(), tcb4.sndNxt()));
        }

        @Test
        void shouldReturnFalseIfAckIsNotAcceptable() {
            final TransmissionControlBlock tcb1 = new TransmissionControlBlock(config, 10, 10, 0, 0, 0, 0, 0, 0, sendBuffer, outoingSegmentQueue, retransmissionQueue, receiveBuffer, mss, 0, 0, 0, 0, 0, 0, false, 0, 0, 1000);
            final Segment seg2 = Segment.ack(1, 10);
            assertFalse(seg2.isAck() && lessThan(tcb1.sndUna(), seg2.ack()) && lessThanOrEqualTo(seg2.ack(), tcb1.sndNxt()));

            final TransmissionControlBlock tcb2 = new TransmissionControlBlock(config, 9, 9, 0, 0, 0, 0, 0, 0, sendBuffer, outoingSegmentQueue, retransmissionQueue, receiveBuffer, mss, 0, 0, 0, 0, 0, 0, false, 0, 0, 1000);
            final Segment seg1 = Segment.ack(1, 10);
            assertFalse(seg1.isAck() && lessThan(tcb2.sndUna(), seg1.ack()) && lessThanOrEqualTo(seg1.ack(), tcb2.sndNxt()));

            // with overflow
            final TransmissionControlBlock tcb3 = new TransmissionControlBlock(config, MAX_SEQ_NO, 0, 0, 0, 0, 0, 0, 0, sendBuffer, outoingSegmentQueue, retransmissionQueue, receiveBuffer, mss, 0, 0, 0, 0, 0, 0, false, 0, 0, 1000);
            final Segment seg = Segment.ack(1, MAX_SEQ_NO - 1);
            assertFalse(seg.isAck() && lessThan(tcb3.sndUna(), seg.ack()) && lessThanOrEqualTo(seg.ack(), tcb3.sndNxt()));
        }
    }
}
