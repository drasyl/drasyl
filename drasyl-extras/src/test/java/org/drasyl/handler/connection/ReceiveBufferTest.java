package org.drasyl.handler.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.handler.connection.ReceiveBuffer.ReceiveBufferEntry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.util.RandomUtil.randomBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiveBufferTest {
    @Captor
    ArgumentCaptor<ByteBuf> receivedBuf;

    @Nested
    class Receive {
        @Test
        void receiveInOrder(@Mock final Channel channel,
                            @Mock final ChannelHandlerContext ctx,
                            @Mock final SendBuffer sendBuffer) {
            when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

            final TransmissionControlBlock tcb = new TransmissionControlBlock(100, 100, 0, 100, 0, 64_000, 0, sendBuffer, new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1000);
            final ReceiveBuffer buffer = new ReceiveBuffer(channel);

            final ByteBuf data = Unpooled.buffer(200).writeBytes(randomBytes(200));

            // expected 0, got [0,110)
            final ConnectionHandshakeSegment seg1 = ConnectionHandshakeSegment.ack(0, 100, data.slice(0, 110));
            buffer.receive(ctx, tcb, seg1);
            assertEquals(63_890, tcb.rcvWnd());
            assertEquals(110, tcb.rcvNxt());
            assertEquals(110, buffer.bytes());
            assertEquals(110, buffer.readableBytes());

            // expected 110, got [110,200)
            final ConnectionHandshakeSegment seg2 = ConnectionHandshakeSegment.ack(110, 100, data.slice(110, 90));
            buffer.receive(ctx, tcb, seg2);
            assertEquals(63_800, tcb.rcvWnd());
            assertEquals(200, tcb.rcvNxt());
            assertEquals(200, buffer.bytes());
            assertEquals(200, buffer.readableBytes());

            buffer.fireRead(ctx, tcb);
            verify(ctx).fireChannelRead(receivedBuf.capture());
            assertEquals(data, receivedBuf.getValue());
            assertEquals(64_000, tcb.rcvWnd());
            assertEquals(0, buffer.bytes());
            assertEquals(0, buffer.readableBytes());
        }

        @Test
        void receiveOutOfOrder(@Mock final Channel channel,
                               @Mock final ChannelHandlerContext ctx,
                               @Mock final SendBuffer sendBuffer) {
            when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

            final ReceiveBuffer buffer = new ReceiveBuffer(channel);
            final TransmissionControlBlock tcb = new TransmissionControlBlock(100, 100, 0, 100, 0, 64_000, 0, sendBuffer, new RetransmissionQueue(channel), buffer, new RttMeasurement(), 1000);

            final ByteBuf data = Unpooled.buffer(500).writeBytes(randomBytes(500));

            // expected 0, got [120,200)
            final ConnectionHandshakeSegment seg2 = ConnectionHandshakeSegment.ack(120, 100, data.slice(120, 80));
            buffer.receive(ctx, tcb, seg2);
            assertEquals(63_920, tcb.rcvWnd());
            assertEquals(0, tcb.rcvNxt());
            assertEquals(80, buffer.bytes());
            assertEquals(0, buffer.readableBytes());

            // expected 0, got [0,120)
            final ConnectionHandshakeSegment seg1 = ConnectionHandshakeSegment.ack(0, 100, data.slice(0, 120));
            buffer.receive(ctx, tcb, seg1);
            assertEquals(63_800, tcb.rcvWnd());
            assertEquals(200, tcb.rcvNxt());
            assertEquals(200, buffer.bytes());
            assertEquals(200, buffer.readableBytes());

            // expected 200, got [410,500)
            final ConnectionHandshakeSegment seg5 = ConnectionHandshakeSegment.ack(410, 100, data.slice(410, 90));
            buffer.receive(ctx, tcb, seg5);
            assertEquals(63_710, tcb.rcvWnd());
            assertEquals(200, tcb.rcvNxt());
            assertEquals(290, buffer.bytes());
            assertEquals(200, buffer.readableBytes());

            // expected 200, got [300,400)
            final ConnectionHandshakeSegment seg4 = ConnectionHandshakeSegment.ack(300, 100, data.slice(300, 110));
            buffer.receive(ctx, tcb, seg4);
            assertEquals(63_600, tcb.rcvWnd());
            assertEquals(200, tcb.rcvNxt());
            assertEquals(400, buffer.bytes());
            assertEquals(200, buffer.readableBytes());

            // expected 200, got [200,300)
            ConnectionHandshakeSegment seg3 = ConnectionHandshakeSegment.ack(200, 100, data.slice(200, 100));
            buffer.receive(ctx, tcb, seg3);
            assertEquals(63_500, tcb.rcvWnd());
            assertEquals(500, tcb.rcvNxt());
            assertEquals(500, buffer.bytes());
            assertEquals(500, buffer.readableBytes());

            buffer.fireRead(ctx, tcb);
            verify(ctx).fireChannelRead(receivedBuf.capture());
            assertEquals(data, receivedBuf.getValue());
            assertEquals(64_000, tcb.rcvWnd());
            assertEquals(0, buffer.bytes());
            assertEquals(0, buffer.readableBytes());
        }

        // FIXME: erster teil des segments ist VOR RCV.NXT
        @Test
        void receiveX1(@Mock final Channel channel,
                      @Mock final ChannelHandlerContext ctx,
                      @Mock final SendBuffer sendBuffer) {
            final ReceiveBuffer buffer = new ReceiveBuffer(channel);
            final TransmissionControlBlock tcb = new TransmissionControlBlock(100, 100, 0, 100, 60, 64_000, 0, sendBuffer, new RetransmissionQueue(channel), buffer, new RttMeasurement(), 1000);

            final ByteBuf data = Unpooled.buffer(100).writeBytes(randomBytes(100));

            // expected 60, got [0,100)
            final ConnectionHandshakeSegment seg1 = ConnectionHandshakeSegment.ack(0, 100, data);
            buffer.receive(ctx, tcb, seg1);
            assertEquals(63_960, tcb.rcvWnd());
            assertEquals(100, tcb.rcvNxt());
            assertEquals(40, buffer.bytes());
            assertEquals(40, buffer.readableBytes());

            buffer.fireRead(ctx, tcb);
            verify(ctx).fireChannelRead(receivedBuf.capture());
            assertEquals(data.slice(60, 40), receivedBuf.getValue());
            assertEquals(64_000, tcb.rcvWnd());
            assertEquals(0, buffer.bytes());
            assertEquals(0, buffer.readableBytes());
        }

        // FIXME: letzter teil des segments ist schon als fragment vorhanden
        @Test
        void receiveX2(@Mock final Channel channel,
                       @Mock final ChannelHandlerContext ctx,
                       @Mock final SendBuffer sendBuffer) {
            when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

            final ByteBuf data = Unpooled.buffer(160).writeBytes(randomBytes(160));

            final ReceiveBufferEntry head = new ReceiveBufferEntry(60, data.slice(60, 100));
            final ReceiveBuffer buffer = new ReceiveBuffer(channel, null, head, 100);
            final TransmissionControlBlock tcb = new TransmissionControlBlock(100, 100, 0, 100, 0, 64_000 - 100, 0, sendBuffer, new RetransmissionQueue(channel), buffer, new RttMeasurement(), 1000);

            // expected [0,60), got [0,100)
            ConnectionHandshakeSegment seg1 = ConnectionHandshakeSegment.ack(0, 100, data.slice(0, 100));
            buffer.receive(ctx, tcb, seg1);
            assertEquals(63_840, tcb.rcvWnd());
            assertEquals(160, tcb.rcvNxt());
            assertEquals(160, buffer.bytes());
            assertEquals(160, buffer.readableBytes());

            buffer.fireRead(ctx, tcb);
            verify(ctx).fireChannelRead(receivedBuf.capture());
            assertEquals(data, receivedBuf.getValue());
            assertEquals(64_000, tcb.rcvWnd());
            assertEquals(0, buffer.bytes());
            assertEquals(0, buffer.readableBytes());
        }

        // FIXME: doppelt in der Mitte
        @Test
        void receiveX3(@Mock final Channel channel,
                       @Mock final ChannelHandlerContext ctx,
                       @Mock final SendBuffer sendBuffer) {
            when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

            final ByteBuf data = Unpooled.buffer(200).writeBytes(randomBytes(200));

            final ReceiveBufferEntry head = new ReceiveBufferEntry(70, data.slice(60, 60));
            final ReceiveBuffer buffer = new ReceiveBuffer(channel, null, head, 60);
            final TransmissionControlBlock tcb = new TransmissionControlBlock(100, 0, 0, 100, 10, 64_000 - 60, 0, sendBuffer, new RetransmissionQueue(channel), buffer, new RttMeasurement(), 1000);

            // expected [10,70) and [130,210), got [10,210)
            ConnectionHandshakeSegment seg1 = ConnectionHandshakeSegment.ack(10, 100, data);
            buffer.receive(ctx, tcb, seg1);
            assertEquals(63_800, tcb.rcvWnd());
            assertEquals(210, tcb.rcvNxt());
            assertEquals(200, buffer.bytes());
            assertEquals(200, buffer.readableBytes());

            buffer.fireRead(ctx, tcb);
            verify(ctx).fireChannelRead(receivedBuf.capture());
            assertEquals(data, receivedBuf.getValue());
            assertEquals(64_000, tcb.rcvWnd());
            assertEquals(0, buffer.bytes());
            assertEquals(0, buffer.readableBytes());
        }

        // FIXME: komplett vor dem receive window
        @Test
        void receiveX4(@Mock final Channel channel,
                       @Mock final ChannelHandlerContext ctx,
                       @Mock final SendBuffer sendBuffer) {
            final ReceiveBuffer buffer = new ReceiveBuffer(channel, null, null, 0);
            final TransmissionControlBlock tcb = new TransmissionControlBlock(100, 0, 0, 100, 100, 64_000, 0, sendBuffer, new RetransmissionQueue(channel), buffer, new RttMeasurement(), 1000);

            final ByteBuf data = Unpooled.buffer(90).writeBytes(randomBytes(90));

            // expected [100,64100), got [10,100)
            ConnectionHandshakeSegment seg1 = ConnectionHandshakeSegment.ack(10, 100, data);
            buffer.receive(ctx, tcb, seg1);
            assertEquals(64000, tcb.rcvWnd());
            assertEquals(100, tcb.rcvNxt());
            assertEquals(0, buffer.bytes());
            assertEquals(0, buffer.readableBytes());
        }

        // FIXME: komplett hinter dem receive window
        @Test
        void receiveX5(@Mock final Channel channel,
                       @Mock final ChannelHandlerContext ctx,
                       @Mock final SendBuffer sendBuffer) {
            final ReceiveBuffer buffer = new ReceiveBuffer(channel, null, null, 0);
            final TransmissionControlBlock tcb = new TransmissionControlBlock(100, 0, 0, 100, 100, 64_000, 0, sendBuffer, new RetransmissionQueue(channel), buffer, new RttMeasurement(), 1000);

            final ByteBuf data = Unpooled.buffer(100).writeBytes(randomBytes(100));

            // expected [100,64100), got [64100,64200)
            ConnectionHandshakeSegment seg1 = ConnectionHandshakeSegment.ack(64100, 100, data);
            buffer.receive(ctx, tcb, seg1);
            assertEquals(64000, tcb.rcvWnd());
            assertEquals(100, tcb.rcvNxt());
            assertEquals(0, buffer.bytes());
            assertEquals(0, buffer.readableBytes());
        }

        // FIXME: duplicate/overlapping (same & partial (vorne oder hinten))
        @Test
        void receiveDuplicates(@Mock final Channel channel,
                               @Mock final ChannelHandlerContext ctx,
                               @Mock final SendBuffer sendBuffer) {
            when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

            final ReceiveBuffer buffer = new ReceiveBuffer(channel);
            final TransmissionControlBlock tcb = new TransmissionControlBlock(100, 100, 0, 100, 0, 64_000, 0, sendBuffer, new RetransmissionQueue(channel), buffer, new RttMeasurement(), 1000);

            final ByteBuf data = Unpooled.buffer(300).writeBytes(randomBytes(300));

            // neues SEG (0-99)
            final ConnectionHandshakeSegment seg1 = ConnectionHandshakeSegment.ack(0, 100, data.slice(0, 100));
            buffer.receive(ctx, tcb, seg1);
            assertEquals(63_900, tcb.rcvWnd());
            assertEquals(100, tcb.rcvNxt());
            assertEquals(100, buffer.bytes());
            assertEquals(100, buffer.readableBytes());

            // identisches SEG (0-99)
            final ConnectionHandshakeSegment seg1copy = ConnectionHandshakeSegment.ack(0, 100, data.slice(0, 100));
            buffer.receive(ctx, tcb, seg1copy);
            assertEquals(63_900, tcb.rcvWnd());
            assertEquals(100, tcb.rcvNxt());
            assertEquals(100, buffer.bytes());
            assertEquals(100, buffer.readableBytes());

            // erste 100 bytes doppelt (0-149)
            final ConnectionHandshakeSegment seg2 = ConnectionHandshakeSegment.ack(0, 100, data.slice(0, 150));
            buffer.receive(ctx, tcb, seg2);
            assertEquals(63_850, tcb.rcvWnd());
            assertEquals(150, tcb.rcvNxt());
            assertEquals(150, buffer.bytes());
            assertEquals(150, buffer.readableBytes());

            // vorbereitung für nächstes SEG, landet als fragment (250-299)
            final ConnectionHandshakeSegment seg3 = ConnectionHandshakeSegment.ack(250, 100, data.slice(250, 50));
            buffer.receive(ctx, tcb, seg3);
            assertEquals(63_800, tcb.rcvWnd());
            assertEquals(150, tcb.rcvNxt());
            assertEquals(200, buffer.bytes());
            assertEquals(150, buffer.readableBytes());

            // letze 50 bytes doppelt (200-299)
            final ConnectionHandshakeSegment seg4 = ConnectionHandshakeSegment.ack(200, 100, data.slice(200, 100));
            buffer.receive(ctx, tcb, seg4);
            assertEquals(63_750, tcb.rcvWnd());
            assertEquals(150, tcb.rcvNxt());
            assertEquals(250, buffer.bytes());
            assertEquals(150, buffer.readableBytes());

            // vorne und hinten doppelt (100-249)
            final ConnectionHandshakeSegment seg5 = ConnectionHandshakeSegment.ack(100, 100, data.slice(100, 150));
            buffer.receive(ctx, tcb, seg5);
            assertEquals(63_700, tcb.rcvWnd());
            assertEquals(300, tcb.rcvNxt());
            assertEquals(300, buffer.bytes());
            assertEquals(300, buffer.readableBytes());

            buffer.fireRead(ctx, tcb);
            verify(ctx).fireChannelRead(receivedBuf.capture());
            assertEquals(data, receivedBuf.getValue());
            assertEquals(64_000, tcb.rcvWnd());
            assertEquals(0, buffer.bytes());
            assertEquals(0, buffer.readableBytes());
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
//        @Test
//        void shouldReturnTheNumberOfBytesInBuffer(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
//            when(channel.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
//            final CoalescingBufferQueue queue = new CoalescingBufferQueue(channel, 4, false);
//            final ReceiveBuffer buffer = new ReceiveBuffer(channel);
//            final ByteBuf buf1 = Unpooled.buffer(10).writeBytes(randomBytes(10));
//            final ByteBuf buf2 = Unpooled.buffer(5).writeBytes(randomBytes(5));
//            queue.add(buf1);
//            queue.add(buf2);
//
//            assertEquals(15, buffer.bytes());
//
//            buf1.release();
//            buf2.release();
//        }
    }
}
