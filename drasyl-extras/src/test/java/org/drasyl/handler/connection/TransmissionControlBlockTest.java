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

import static org.drasyl.handler.connection.ConnectionHandshakeSegment.MAX_SEQ_NO;
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

    @Nested
    class IsAcceptableAck {
        @Test
        void shouldReturnFalseIfSegmentIsNoAck() {
            final TransmissionControlBlock tcb = new TransmissionControlBlock(9, 10, 0, 0, 0, 0, 0, sendBuffer, outoingSegmentQueue, retransmissionQueue, receiveBuffer, rttMeasurement, mss);

            assertFalse(tcb.isAcceptableAck(ConnectionHandshakeSegment.syn(1)));
        }

        @Test
        void shouldReturnTrueIfAckIsAcceptable() {
            final TransmissionControlBlock tcb1 = new TransmissionControlBlock(9, 10, 0, 0, 0, 0, 0, sendBuffer, outoingSegmentQueue, retransmissionQueue, receiveBuffer, rttMeasurement, mss);
            assertTrue(tcb1.isAcceptableAck(ConnectionHandshakeSegment.ack(1, 10)));

            final TransmissionControlBlock tcb2 = new TransmissionControlBlock(9, 11, 0, 0, 0, 0, 0, sendBuffer, outoingSegmentQueue, retransmissionQueue, receiveBuffer, rttMeasurement, mss);
            assertTrue(tcb2.isAcceptableAck(ConnectionHandshakeSegment.ack(1, 10)));

            // with overflow
            final TransmissionControlBlock tcb3 = new TransmissionControlBlock(MAX_SEQ_NO - 1, MAX_SEQ_NO, 0, 0, 0, 0, 0, sendBuffer, outoingSegmentQueue, retransmissionQueue, receiveBuffer, rttMeasurement, mss);
            assertTrue(tcb3.isAcceptableAck(ConnectionHandshakeSegment.ack(1, MAX_SEQ_NO)));

            final TransmissionControlBlock tcb4 = new TransmissionControlBlock(MAX_SEQ_NO - 1, 0, 0, 0, 0, 0, 0, sendBuffer, outoingSegmentQueue, retransmissionQueue, receiveBuffer, rttMeasurement, mss);
            assertTrue(tcb4.isAcceptableAck(ConnectionHandshakeSegment.ack(1, MAX_SEQ_NO)));
        }

        @Test
        void shouldReturnFalseIfAckIsNotAcceptable() {
            final TransmissionControlBlock tcb1 = new TransmissionControlBlock(10, 10, 0, 0, 0, 0, 0, sendBuffer, outoingSegmentQueue, retransmissionQueue, receiveBuffer, rttMeasurement, mss);
            assertFalse(tcb1.isAcceptableAck(ConnectionHandshakeSegment.ack(1, 10)));

            final TransmissionControlBlock tcb2 = new TransmissionControlBlock(9, 9, 0, 0, 0, 0, 0, sendBuffer, outoingSegmentQueue, retransmissionQueue, receiveBuffer, rttMeasurement, mss);
            assertFalse(tcb2.isAcceptableAck(ConnectionHandshakeSegment.ack(1, 10)));

            // with overflow
            final TransmissionControlBlock tcb3 = new TransmissionControlBlock(MAX_SEQ_NO, 0, 0, 0, 0, 0, 0, sendBuffer, outoingSegmentQueue, retransmissionQueue, receiveBuffer, rttMeasurement, mss);
            assertFalse(tcb3.isAcceptableAck(ConnectionHandshakeSegment.ack(1, MAX_SEQ_NO - 1)));
        }
    }
}
