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
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.handler.connection.Segment.MAX_SEQ_NO;
import static org.drasyl.util.RandomUtil.randomBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class SegmentTest {
    @Nested
    class Len {
        @Test
        void shouldReturnLengthOfTheSegment() {
            final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final Segment seg = Segment.ack(100, 0, data);

            assertEquals(10, seg.len());

            seg.release();
        }

        @Test
        void shouldCountSyn() {
            final Segment seg = Segment.syn(100);

            assertEquals(1, seg.len());
        }

        @Test
        void shouldCountFin() {
            final Segment seg = Segment.fin(100);

            assertEquals(1, seg.len());
        }
    }

    @Nested
    class LastSeq {
        @Test
        void shouldReturnLastSegmentOfTheSegment() {
            final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final Segment seg = Segment.ack(100, 0, data);

            assertEquals(109, seg.lastSeq());

            seg.release();
        }

        @Test
        void shouldReturnLastSegmentOfTheSegmentDespiteOverflow() {
            final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final Segment seg = Segment.ack(MAX_SEQ_NO - 9, 0, data);

            assertEquals(MAX_SEQ_NO, seg.lastSeq());

            seg.release();
        }

        @Test
        void shouldReturnLastSegmentOfZeroLengthSegment() {
            final Segment seg = Segment.ack(100, 0);

            assertEquals(100, seg.lastSeq());

            seg.release();
        }
    }

    @Nested
    class CanPiggybackAck {
        @Test
        void shouldReturnTrueIfOtherSegmentIsHigherAck() {
            final Segment current = Segment.ack(10, 1);
            final Segment next = Segment.ack(10, 2);

            assertTrue(next.canPiggybackAck(current));
        }

        @Test
        void shouldReturnTrueIfOtherSegmentCanPiggybackAck() {
            final Segment current = Segment.ack(10, 1);
            final Segment next = Segment.fin(10);

            assertTrue(next.canPiggybackAck(current));
        }

        @Test
        void shouldReturnTrueIfOtherSegmentContainsHigherAck() {
            final Segment current = Segment.ack(10, 1);
            final Segment next = Segment.pshAck(10, 1, Unpooled.buffer(10).writerIndex(10));

            assertTrue(next.canPiggybackAck(current));

            next.release();
        }

        @Test
        void shouldReturnFalseIfCurrentSegmentIsNotOnlyAck() {
            final Segment current = Segment.pshAck(10, 1, Unpooled.EMPTY_BUFFER);
            final Segment next = Segment.pshAck(20, 1, Unpooled.EMPTY_BUFFER);

            assertFalse(next.canPiggybackAck(current));
        }
    }

    @Nested
    class PiggybackAck {
        @Test
        void shouldReplaceCurrentAckIfOtherSegmentIsHigherAck() {
            final Segment current = Segment.ack(10, 1);
            final Segment next = Segment.ack(10, 2);

            assertSame(next, next.piggybackAck(current));
        }

        @Test
        void shouldPiggybackAckToOtherSegment() {
            final Segment current = Segment.ack(10, 1);
            final Segment next = Segment.fin(10);

            assertEquals(Segment.finAck(10, 1), next.piggybackAck(current));
        }
    }

    @Nested
    class AdvanceSeq {
        @Test
        void shouldAdvanceSeqByGivenNumber() {
            assertEquals(6, Segment.advanceSeq(1, 5));
            assertEquals(4, Segment.advanceSeq(MAX_SEQ_NO - 5, 10));
        }
    }
}
