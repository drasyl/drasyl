/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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

import static org.drasyl.handler.connection.Segment.ACK;
import static org.drasyl.handler.connection.Segment.FIN;
import static org.drasyl.handler.connection.Segment.MAX_SEQ_NO;
import static org.drasyl.handler.connection.Segment.SYN;
import static org.drasyl.handler.connection.Segment.add;
import static org.drasyl.handler.connection.Segment.sub;
import static org.drasyl.util.RandomUtil.randomBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class SegmentTest {
    @Nested
    class Len {
        @Test
        void shouldReturnLengthOfTheSegment() {
            final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final Segment seg = new Segment(1234, 5678, 100, 0, ACK, data);

            assertEquals(10, seg.len());

            seg.release();
        }

        @Test
        void shouldNotCountSyn() {
            final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final Segment seg = new Segment(1234, 5678, 100, 0, SYN, data);

            assertEquals(10, seg.len());

            seg.release();
        }

        @Test
        void shouldNotCountFin() {
            final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final Segment seg = new Segment(1234, 5678, 100, 0, FIN, data);

            assertEquals(10, seg.len());

            seg.release();
        }
    }

    @Nested
    class NxtSeq {
        @Test
        void shouldReturnNextSegment() {
            final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final Segment seg = new Segment(1234, 5678, 100, 0, ACK, data);

            assertEquals(110, seg.nxtSeq());

            seg.release();
        }

        @Test
        void shouldCountSyn() {
            final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final Segment seg = new Segment(1234, 5678, 100, 0, SYN, data);

            assertEquals(111, seg.nxtSeq());

            seg.release();
        }

        @Test
        void shouldCountFin() {
            final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final Segment seg = new Segment(1234, 5678, 100, 0, FIN, data);

            assertEquals(111, seg.nxtSeq());

            seg.release();
        }
    }

    @Nested
    class LastSeq {
        @Test
        void shouldReturnLastSegmentOfTheSegment() {
            final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final Segment seg = new Segment(1234, 5678, 100, 0, ACK, data);

            assertEquals(109, seg.lastSeq());

            seg.release();
        }

        @Test
        void shouldReturnLastSegmentOfTheSegmentDespiteOverflow() {
            final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final Segment seg = new Segment(1234, 5678, MAX_SEQ_NO - 9, 0, ACK, data);

            assertEquals(MAX_SEQ_NO, seg.lastSeq());

            seg.release();
        }

        @Test
        void shouldReturnLastSegmentOfZeroLengthSegment() {
            final Segment seg = new Segment(1234, 5678, 100, 0, ACK);

            assertEquals(100, seg.lastSeq());

            seg.release();
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

    @Nested
    class MustBeAcked {
        @Test
        void shouldReturnFalseForSegmentsThatMustNotBeAcknowledged() {
            final Segment seg = new Segment(1234, 5678, 100, 0, ACK);
            assertFalse(seg.mustBeAcked());
        }

        @Test
        void shouldReturnTrueForSegmentsThatMustBeAcknowledged() {
            final Segment seg2 = new Segment(1234, 5678, 100, 0, ACK, Unpooled.buffer(4).writeInt(1));
            assertTrue(seg2.mustBeAcked());
        }
    }

    @Nested
    class Add {
        @Test
        void shouldReturnTheAddedResult() {
            assertEquals(1, add(1, 0));
            assertEquals(1, add(0, 1));
            assertEquals(2, add(1, 1));
            assertEquals(0, add(MAX_SEQ_NO, 1));
            assertEquals(0, add(1, MAX_SEQ_NO));
        }
    }

    @Nested
    class Sub {
        @Test
        void shouldReturnTheSubtractedResult() {
            assertEquals(1, sub(1, 0));
            assertEquals(MAX_SEQ_NO, sub(0, 1));
            assertEquals(0, sub(1, 1));
            assertEquals(1, sub(0, MAX_SEQ_NO));
        }
    }
}
