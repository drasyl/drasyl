package org.drasyl.handler.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

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
    private TransmissionControlBlock tcb;

    @Nested
    class Add {
        @Test
        void shouldAddSegmentToEndOfQueue(@Mock final Segment seg) {
            final OutgoingSegmentQueue queue = new OutgoingSegmentQueue();

            final long seq1 = seg.seq();
            final int readableBytes = seg.content().readableBytes();
            final long ack1 = seg.ack();
            final byte ctl1 = seg.ctl();
            Map<SegmentOption, Object> options = seg.options();
            queue.addBytes(seq1, readableBytes, ack1, ctl1, options);
        }
    }

    @Nested
    class AddAndFlush {
        @Test
        void shouldAddSegmentToEndOfQueueAndFlushItToChannel(@Mock(answer = RETURNS_DEEP_STUBS) final Segment seg,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final OutgoingSegmentQueue queue = new OutgoingSegmentQueue();

            try {
                final long seq1 = seg.seq();
                final int readableBytes = seg.content().readableBytes();
                final long ack1 = seg.ack();
                final byte ctl1 = seg.ctl();
                Map<SegmentOption, Object> options = seg.options();
                queue.addBytes(seq1, readableBytes, ack1, ctl1, options);
            }
            finally {
                queue.flush(ctx, tcb);
            }

            verify(ctx).write(seg.copy());
            verify(ctx).flush();
        }
    }

    @Nested
    class Flush {
        @Test
        void shouldFlushSegmentsToChannel(@Mock(answer = RETURNS_DEEP_STUBS) final Segment seg,
                                          @Mock final ChannelPromise writePromise,
                                          @Mock final ChannelPromise ackPromise,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final OutgoingSegmentQueue queue = new OutgoingSegmentQueue();

            queue.flush(ctx, tcb);

            verify(ctx).write(seg.copy(), writePromise);
            verify(ctx).flush();
        }

        @Test
        void shouldCumulateAcknowledgements(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(channel.eventLoop().inEventLoop()).thenReturn(true);
            final ByteBuf buf1 = UnpooledByteBufAllocator.DEFAULT.buffer();
            final Segment seg1 = Segment.ack(100, 200, buf1);
            final OutgoingSegmentQueue queue = new OutgoingSegmentQueue();
            final ChannelPromise writePromise1 = new DefaultChannelPromise(channel);
            final Segment seg2 = Segment.ack(100, 250);
            final ChannelPromise writePromise2 = new DefaultChannelPromise(channel);
            final long seq11 = seg1.seq();
            final int readableBytes1 = seg1.content().readableBytes();
            final long ack11 = seg1.ack();
            final byte ctl11 = seg1.ctl();
            Map<SegmentOption, Object> options11 = seg1.options();
            queue.addBytes(seq11, readableBytes1, ack11, ctl11, options11);
            final long seq1 = seg2.seq();
            final int readableBytes = seg2.content().readableBytes();
            final long ack1 = seg2.ack();
            final byte ctl1 = seg2.ctl();
            Map<SegmentOption, Object> options1 = seg2.options();
            queue.addBytes(seq1, readableBytes, ack1, ctl1, options1);

            queue.flush(ctx, tcb);

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
                                             @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(channel.eventLoop().inEventLoop()).thenReturn(true);
            final ByteBuf buf1 = UnpooledByteBufAllocator.DEFAULT.buffer();
            final Segment seg1 = Segment.ack(100, 200, buf1);
            final OutgoingSegmentQueue queue = new OutgoingSegmentQueue();
            final ChannelPromise writePromise1 = new DefaultChannelPromise(channel);
            final Segment seg2 = Segment.fin(100);
            final ChannelPromise writePromise2 = new DefaultChannelPromise(channel);
            final long seq11 = seg1.seq();
            final int readableBytes1 = seg1.content().readableBytes();
            final long ack11 = seg1.ack();
            final byte ctl11 = seg1.ctl();
            Map<SegmentOption, Object> options11 = seg1.options();
            queue.addBytes(seq11, readableBytes1, ack11, ctl11, options11);
            final long seq1 = seg2.seq();
            final int readableBytes = seg2.content().readableBytes();
            final long ack1 = seg2.ack();
            final byte ctl1 = seg2.ctl();
            Map<SegmentOption, Object> options1 = seg2.options();
            queue.addBytes(seq1, readableBytes, ack1, ctl1, options1);

            queue.flush(ctx, tcb);

            // seg1 should have been piggybacked by seg2
            verify(ctx).write(eq(Segment.finAck(100, 200)), eq(writePromise2));
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
