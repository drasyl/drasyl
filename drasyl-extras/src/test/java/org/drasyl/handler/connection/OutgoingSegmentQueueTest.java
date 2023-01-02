package org.drasyl.handler.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import org.drasyl.handler.connection.OutgoingSegmentQueue.OutgoingSegmentEntry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutgoingSegmentQueueTest {
    private SendBuffer sendBuffer;
    private int mss;

    @Nested
    class Add {
        @Test
        void shouldAddSegmentToEndOfQueue(@Mock final ConnectionHandshakeSegment seg,
                                          @Mock final RetransmissionQueue retransmissionQueue,
                                          @Mock final RttMeasurement rttMeasurement) {
            final ArrayDeque<OutgoingSegmentEntry> deque = new ArrayDeque<>();
            final OutgoingSegmentQueue queue = new OutgoingSegmentQueue(retransmissionQueue, rttMeasurement);

            queue.add(seg);

            final OutgoingSegmentEntry entry = deque.poll();
            assertEquals(seg, entry.seg());
        }
    }

    @Nested
    class AddAndFlush {
        @Test
        void shouldAddSegmentToEndOfQueueAndFlushItToChannel(@Mock(answer = RETURNS_DEEP_STUBS) final ConnectionHandshakeSegment seg,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                             @Mock final RetransmissionQueue retransmissionQueue,
                                                             @Mock final RttMeasurement rttMeasurement) {
            final ArrayDeque<OutgoingSegmentEntry> deque = new ArrayDeque<>();
            final OutgoingSegmentQueue queue = new OutgoingSegmentQueue(retransmissionQueue, rttMeasurement);

            try {
                queue.add(seg);
            }
            finally {
                queue.flush(ctx, sendBuffer, mss);
            }

            verify(ctx).write(seg.copy());
            verify(ctx).flush();
        }
    }

    @Nested
    class Flush {
        @Test
        void shouldFlushSegmentsToChannel(@Mock(answer = RETURNS_DEEP_STUBS) final ConnectionHandshakeSegment seg,
                                          @Mock final ChannelPromise writePromise,
                                          @Mock final ChannelPromise ackPromise,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                          @Mock final RetransmissionQueue retransmissionQueue,
                                          @Mock final RttMeasurement rttMeasurement) {
            final ArrayDeque<OutgoingSegmentEntry> deque = new ArrayDeque<>();
            final OutgoingSegmentQueue queue = new OutgoingSegmentQueue(retransmissionQueue, rttMeasurement);
            deque.add(new OutgoingSegmentEntry(seg, ackPromise));

            queue.flush(ctx, sendBuffer, mss);

            verify(ctx).write(seg.copy(), writePromise);
            verify(ctx).flush();
        }

        @Test
        void shouldCumulateAcknowledgements(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                            @Mock final RetransmissionQueue retransmissionQueue,
                                            @Mock final RttMeasurement rttMeasurement) {
            when(channel.eventLoop().inEventLoop()).thenReturn(true);
            final ByteBuf buf1 = UnpooledByteBufAllocator.DEFAULT.buffer();
            final ConnectionHandshakeSegment seg1 = ConnectionHandshakeSegment.ack(100, 200, buf1);
            final OutgoingSegmentQueue queue = new OutgoingSegmentQueue(retransmissionQueue, rttMeasurement);
            final ChannelPromise writePromise1 = new DefaultChannelPromise(channel);
            final ConnectionHandshakeSegment seg2 = ConnectionHandshakeSegment.ack(100, 250);
            final ChannelPromise writePromise2 = new DefaultChannelPromise(channel);
            queue.add(seg1);
            queue.add(seg2);

            queue.flush(ctx, sendBuffer, mss);

            // seg1 should have been superseded by seg2
            verify(ctx).write(seg2, writePromise2);
            assertEquals(0, seg1.refCnt());

            // promises of seg2 should notify seg1
            assertFalse(writePromise1.isSuccess());
            writePromise2.setSuccess();
            assertTrue(writePromise1.isSuccess());
        }

        @Test
        void shouldPiggybackAcknowledgements(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel,
                                             @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                             @Mock final RetransmissionQueue retransmissionQueue,
                                             @Mock final RttMeasurement rttMeasurement) {
            when(channel.eventLoop().inEventLoop()).thenReturn(true);
            final ByteBuf buf1 = UnpooledByteBufAllocator.DEFAULT.buffer();
            final ConnectionHandshakeSegment seg1 = ConnectionHandshakeSegment.ack(100, 200, buf1);
            final OutgoingSegmentQueue queue = new OutgoingSegmentQueue(retransmissionQueue, rttMeasurement);
            final ChannelPromise writePromise1 = new DefaultChannelPromise(channel);
            final ConnectionHandshakeSegment seg2 = ConnectionHandshakeSegment.fin(100);
            final ChannelPromise writePromise2 = new DefaultChannelPromise(channel);
            queue.add(seg1);
            queue.add(seg2);

            queue.flush(ctx, sendBuffer, mss);

            // seg1 should have been piggybacked by seg2
            verify(ctx).write(eq(ConnectionHandshakeSegment.finAck(100, 200)), eq(writePromise2));
            assertEquals(0, seg1.refCnt());

            // promises of seg2 should notify seg1
            assertFalse(writePromise1.isSuccess());
            writePromise2.setSuccess();
            assertTrue(writePromise1.isSuccess());
        }

        @Test
        void shouldApplyRetransmissionTimeout() {
            // TODO
        }
    }
}
