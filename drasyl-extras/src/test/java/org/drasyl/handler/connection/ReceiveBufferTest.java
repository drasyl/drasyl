package org.drasyl.handler.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.CoalescingBufferQueue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.util.RandomUtil.randomBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiveBufferTest {
    @Nested
    class Receive {
        @Test
        void receiveInOrder(@Mock final Channel channel,
                            @Mock final ChannelHandlerContext ctx,
                            @Mock final SendBuffer sendBuffer) {
            when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

            final TransmissionControlBlock tcb = new TransmissionControlBlock(100, 100, 0, 100, 0, 64_000, 0, sendBuffer, new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1000);
            final ReceiveBuffer buffer = new ReceiveBuffer(channel);

            ByteBuf data1 = Unpooled.buffer(100).writeBytes(randomBytes(100));
            ConnectionHandshakeSegment seg1 = ConnectionHandshakeSegment.ack(0, 100, data1);
            buffer.receive(ctx, tcb, seg1);
            assertEquals(63_900, tcb.rcvWnd());
            assertEquals(100, tcb.rcvNxt());
            assertEquals(100, buffer.bytes());
            assertEquals(100, buffer.readableBytes());

            ByteBuf data2 = Unpooled.buffer(100).writeBytes(randomBytes(100));
            ConnectionHandshakeSegment seg2 = ConnectionHandshakeSegment.ack(100, 100, data2);
            buffer.receive(ctx, tcb, seg2);
            assertEquals(63_800, tcb.rcvWnd());
            assertEquals(200, tcb.rcvNxt());
            assertEquals(200, buffer.bytes());
            assertEquals(200, buffer.readableBytes());
        }

        @Test
        void receiveOutOfOrder(@Mock final Channel channel,
                               @Mock final ChannelHandlerContext ctx,
                               @Mock final SendBuffer sendBuffer) {
            when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

            final TransmissionControlBlock tcb = new TransmissionControlBlock(100, 100, 0, 100, 0, 64_000, 0, sendBuffer, new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1000);
            final ReceiveBuffer buffer = new ReceiveBuffer(channel);

            ByteBuf data2 = Unpooled.buffer(100).writeBytes(randomBytes(100));
            ConnectionHandshakeSegment seg2 = ConnectionHandshakeSegment.ack(100, 100, data2);
            buffer.receive(ctx, tcb, seg2);
            assertEquals(63_900, tcb.rcvWnd());
            assertEquals(0, tcb.rcvNxt());
            assertEquals(100, buffer.bytes());
            assertEquals(0, buffer.readableBytes());

            ByteBuf data1 = Unpooled.buffer(100).writeBytes(randomBytes(100));
            ConnectionHandshakeSegment seg1 = ConnectionHandshakeSegment.ack(0, 100, data1);
            buffer.receive(ctx, tcb, seg1);
            assertEquals(63_800, tcb.rcvWnd());
            assertEquals(200, tcb.rcvNxt());
            assertEquals(200, buffer.bytes());
            assertEquals(200, buffer.readableBytes());

            ByteBuf data4 = Unpooled.buffer(100).writeBytes(randomBytes(100));
            ConnectionHandshakeSegment seg4 = ConnectionHandshakeSegment.ack(300, 100, data4);
            buffer.receive(ctx, tcb, seg4);
            assertEquals(63_700, tcb.rcvWnd());
            assertEquals(200, tcb.rcvNxt());
            assertEquals(300, buffer.bytes());
            assertEquals(200, buffer.readableBytes());

            ByteBuf data3 = Unpooled.buffer(100).writeBytes(randomBytes(100));
            ConnectionHandshakeSegment seg3 = ConnectionHandshakeSegment.ack(200, 100, data3);
            buffer.receive(ctx, tcb, seg3);
            assertEquals(63_600, tcb.rcvWnd());
            assertEquals(400, tcb.rcvNxt());
            assertEquals(400, buffer.bytes());
            assertEquals(400, buffer.readableBytes());
        }

        // FIXME: duplicate/overlapping (same & partial (vorne oder hinten))
        @Test
        void receiveDuplicates(@Mock final Channel channel,
                               @Mock final ChannelHandlerContext ctx,
                               @Mock final SendBuffer sendBuffer) {
            when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

            final TransmissionControlBlock tcb = new TransmissionControlBlock(100, 100, 0, 100, 0, 64_000, 0, sendBuffer, new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1000);
            final ReceiveBuffer buffer = new ReceiveBuffer(channel);

            // neues SEG (0-99)
            ByteBuf data1 = Unpooled.buffer(100).writeBytes(randomBytes(100));
            ConnectionHandshakeSegment seg1 = ConnectionHandshakeSegment.ack(0, 100, data1);
            buffer.receive(ctx, tcb, seg1);
            assertEquals(63_900, tcb.rcvWnd());
            assertEquals(100, tcb.rcvNxt());
            assertEquals(100, buffer.bytes());
            assertEquals(100, buffer.readableBytes());

            // identisches SEG (0-99)
            ByteBuf data1copy = Unpooled.buffer(100).writeBytes(randomBytes(100));
            ConnectionHandshakeSegment seg1copy = ConnectionHandshakeSegment.ack(0, 100, data1copy);
            buffer.receive(ctx, tcb, seg1copy);
            assertEquals(63_900, tcb.rcvWnd());
            assertEquals(100, tcb.rcvNxt());
            assertEquals(100, buffer.bytes());
            assertEquals(100, buffer.readableBytes());

//            // erste 50 bytes doppelt (0-149)
//            ByteBuf data2 = Unpooled.buffer(150).writeBytes(randomBytes(150));
//            ConnectionHandshakeSegment seg2 = ConnectionHandshakeSegment.ack(0, 100, data2);
//            buffer.receive(ctx, tcb, seg2);
//            assertEquals(63_850, tcb.rcvWnd());
//            assertEquals(150, tcb.rcvNxt());
//            assertEquals(150, buffer.bytes());
//            assertEquals(150, buffer.readableBytes());
//
//            // vorbereitung (250-299)
//            ByteBuf data3 = Unpooled.buffer(50).writeBytes(randomBytes(50));
//            ConnectionHandshakeSegment seg3 = ConnectionHandshakeSegment.ack(250, 100, data3);
//            buffer.receive(ctx, tcb, seg3);
//            assertEquals(63_800, tcb.rcvWnd());
//            assertEquals(150, tcb.rcvNxt());
//            assertEquals(200, buffer.bytes());
//            assertEquals(150, buffer.readableBytes());
//
//            // letze 50 bytes doppelt (200-299)
//            ByteBuf data4 = Unpooled.buffer(100).writeBytes(randomBytes(100));
//            ConnectionHandshakeSegment seg4 = ConnectionHandshakeSegment.ack(200, 100, data4);
//            buffer.receive(ctx, tcb, seg4);
//            assertEquals(63_750, tcb.rcvWnd());
//            assertEquals(150, tcb.rcvNxt());
//            assertEquals(250, buffer.bytes());
//            assertEquals(100, buffer.readableBytes());
//
//            // vorne und hinten doppelt (100-249)
//            ByteBuf data5 = Unpooled.buffer(150).writeBytes(randomBytes(150));
//            ConnectionHandshakeSegment seg5 = ConnectionHandshakeSegment.ack(100, 100, data5);
//            buffer.receive(ctx, tcb, seg5);
//            assertEquals(63_700, tcb.rcvWnd());
//            assertEquals(300, tcb.rcvNxt());
//            assertEquals(300, buffer.bytes());
//            assertEquals(300, buffer.readableBytes());

            // FIXME: in der mitte doppelt
        }
    }

//    @Nested
//    class Add {
//        @Test
//        void shouldAddGivenBytesToTheEndOfTheBuffer(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
//            when(channel.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
//            final CoalescingBufferQueue queue = new CoalescingBufferQueue(channel, 4, false);
//            final ReceiveBuffer buffer = new ReceiveBuffer(channel, queue);
//            final ByteBuf buf1 = Unpooled.buffer(10).writeBytes(randomBytes(10));
//            final ByteBuf buf2 = Unpooled.buffer(5).writeBytes(randomBytes(5));
//
//            buffer.add(buf1);
//            buffer.add(buf2);
//
//            final ByteBuf removed = queue.remove(15, channel.newPromise());
//            assertEquals(15, removed.readableBytes());
//            final CompositeByteBuf expectedBuf = Unpooled.compositeBuffer(2).addComponents(true, buf1, buf2);
//            assertEquals(expectedBuf, removed);
//
//            expectedBuf.release();
//        }
//    }

    @Nested
    class IsEmpty {
//        @Test
//        void shouldReturnTrueIfBufferContainsNoBytes(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
//            final CoalescingBufferQueue queue = new CoalescingBufferQueue(channel, 4, false);
//            final ReceiveBuffer buffer = new ReceiveBuffer(channel, queue);
//
//            assertTrue(buffer.isEmpty());
//        }

//        @Test
//        void shouldReturnFalseIfBufferContainsBytes(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
//            final CoalescingBufferQueue queue = new CoalescingBufferQueue(channel, 4, false);
//            final ReceiveBuffer buffer = new ReceiveBuffer(channel, queue);
//            final ByteBuf buf = Unpooled.buffer(10).writeBytes(randomBytes(10));
//            buffer.add(buf);
//
//            assertFalse(buffer.isEmpty());
//
//            buf.release();
//        }
    }

    @Nested
    class Release {
//        @Test
//        void shouldReleaseAllBytes(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
//            when(channel.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
//            final CoalescingBufferQueue queue = new CoalescingBufferQueue(channel, 4, false);
//            final ReceiveBuffer buffer = new ReceiveBuffer(channel, queue);
//            final ByteBuf buf = Unpooled.buffer(10).writeBytes(randomBytes(10));
//            buffer.add(buf);
//
//            buffer.release();
//
//            assertEquals(0, buf.refCnt());
//        }
    }

    @Nested
    class ReadableBytes {
        @Test
        void shouldReturnTheNumberOfBytesInBuffer(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            when(channel.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
            final CoalescingBufferQueue queue = new CoalescingBufferQueue(channel, 4, false);
            final ReceiveBuffer buffer = new ReceiveBuffer(channel);
            final ByteBuf buf1 = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final ByteBuf buf2 = Unpooled.buffer(5).writeBytes(randomBytes(5));
            queue.add(buf1);
            queue.add(buf2);

            assertEquals(15, buffer.bytes());

            buf1.release();
            buf2.release();
        }
    }
}
