package org.drasyl.handler.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.handler.connection.ReceiveBuffer.ReceiveBufferBlock;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.util.RandomUtil.randomBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiveBufferTest {
    @Captor
    ArgumentCaptor<ByteBuf> receivedBuf;

    @Nested
    class Receive {
        @Test
        void test(@Mock final Channel channel,
                  @Mock final ChannelHandlerContext ctx,
                  @Mock final SendBuffer sendBuffer) {
            final ByteBuf data = Unpooled.buffer(100_000).writeBytes(randomBytes(100_000));

            ReceiveBufferBlock head = new ReceiveBufferBlock(100, Unpooled.buffer(10).writeBytes(randomBytes(10)));
            head.next = new ReceiveBufferBlock(150, Unpooled.buffer(100).writeBytes(randomBytes(100)));
            final ReceiveBuffer buffer = new ReceiveBuffer(channel, head, null, 0, 60);
            final TransmissionControlBlock tcb = new TransmissionControlBlock(ReliableTransportConfig.newBuilder().build(), 100, 0, 0, 100, 918402327, 0, sendBuffer, new RetransmissionQueue(channel), buffer);

            Segment seg2 = Segment.pshAck(110, 1751431617, data.slice(0, 100));
            buffer.receive(ctx, tcb, seg2);
        }

        // ein segment am linken fensterrand soll bei einem vollen buffer bestehende fragmente ersetzen
        // https://www.rfc-editor.org/rfc/rfc6675.html#section-5.1
        @Test
        @Disabled("kann bei uns gar nicht passieren?")
        void preventMemoryDeadlock(@Mock final Channel channel,
                                   @Mock final ChannelHandlerContext ctx,
                                   @Mock final SendBuffer sendBuffer) {
            final ByteBuf data = Unpooled.buffer(100_000).writeBytes(randomBytes(100_000));

            final ReceiveBuffer buffer = new ReceiveBuffer(channel);
            final TransmissionControlBlock tcb = new TransmissionControlBlock(ReliableTransportConfig.newBuilder().build(), 100, 0, 0, 100, 0, 0, sendBuffer, new RetransmissionQueue(channel), buffer);

            // 100 bytes remaining. receive [10,50)
            // füge 40 bytes irgendwo im fenster ein
            final Segment seg1 = Segment.pshAck(10, 100, data.slice(10, 40));
            buffer.receive(ctx, tcb, seg1);
            assertEquals(60, tcb.rcvWnd());

            // 60 bytes remaining. receive [60,110)
            // füge 40 bytes irgendwo im fenster ein
            Segment seg2 = Segment.pshAck(60, 100, data.slice(60, 50));
            buffer.receive(ctx, tcb, seg2);
            assertEquals(0, tcb.rcvWnd());

            // 0 bytes remaining. receive [0,10)
        }

        @Nested
        class InOrderWithNoOverlappingSegments {
            @Test
            void receiveSegmentsInOrder(@Mock final Channel channel,
                                        @Mock final ChannelHandlerContext ctx,
                                        @Mock final SendBuffer sendBuffer) {
                when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

                final TransmissionControlBlock tcb = new TransmissionControlBlock(ReliableTransportConfig.newBuilder().build(), 100, 100, 0, 100, 0, 0, sendBuffer, new RetransmissionQueue(channel), new ReceiveBuffer(channel));
                final ReceiveBuffer buffer = new ReceiveBuffer(channel);

                final ByteBuf data = Unpooled.buffer(201).writeBytes(randomBytes(201));

                // expected 0, got [0,110)
                final Segment seg1 = Segment.ack(0, 100, data.slice(0, 110));
                buffer.receive(ctx, tcb, seg1);
                assertEquals(63_890, tcb.rcvWnd());
                assertEquals(110, tcb.rcvNxt());
                assertEquals(110, buffer.bytes());
                assertEquals(110, buffer.readableBytes());
                assertNull(buffer.head);

                // expected 110, got [110,200)
                final Segment seg2 = Segment.ack(110, 100, data.slice(110, 90));
                buffer.receive(ctx, tcb, seg2);
                assertEquals(63_800, tcb.rcvWnd());
                assertEquals(200, tcb.rcvNxt());
                assertEquals(200, buffer.bytes());
                assertEquals(200, buffer.readableBytes());
                assertNull(buffer.head);

                // expected 200, got [200,201)
                final Segment seg3 = Segment.ack(200, 100, data.slice(200, 1));
                buffer.receive(ctx, tcb, seg3);
                assertEquals(63_799, tcb.rcvWnd());
                assertEquals(201, tcb.rcvNxt());
                assertEquals(201, buffer.bytes());
                assertEquals(201, buffer.readableBytes());
                assertNull(buffer.head);

                buffer.fireRead(ctx, tcb);
                verify(ctx).fireChannelRead(receivedBuf.capture());
                assertEquals(data, receivedBuf.getValue());
                assertEquals(64_000, tcb.rcvWnd());
                assertEquals(0, buffer.bytes());
                assertEquals(0, buffer.readableBytes());
            }

            @Test
            void receiveSegmentsInOrdnerWithGaps(@Mock final Channel channel,
                                                 @Mock final ChannelHandlerContext ctx,
                                                 @Mock final SendBuffer sendBuffer) {
                final TransmissionControlBlock tcb = new TransmissionControlBlock(ReliableTransportConfig.newBuilder().build(), 100, 100, 0, 100, 0, 0, sendBuffer, new RetransmissionQueue(channel), new ReceiveBuffer(channel));
                final ReceiveBuffer buffer = new ReceiveBuffer(channel);

                final ByteBuf data = Unpooled.buffer(230).writeBytes(randomBytes(230));

                // expected 0, got [30,130)
                final Segment seg1 = Segment.ack(30, 100, data.slice(30, 100));
                buffer.receive(ctx, tcb, seg1);
                assertEquals(63_900, tcb.rcvWnd());
                assertEquals(0, tcb.rcvNxt());
                assertEquals(100, buffer.bytes());
                assertEquals(0, buffer.readableBytes());
                assertNotNull(buffer.head);

                // expected 0, got [130,230)
                final Segment seg2 = Segment.ack(130, 100, data.slice(130, 100));
                buffer.receive(ctx, tcb, seg2);
                assertEquals(63_800, tcb.rcvWnd());
                assertEquals(0, tcb.rcvNxt());
                assertEquals(200, buffer.bytes());
                assertEquals(0, buffer.readableBytes());
                assertNotNull(buffer.head);
            }
        }

        @Nested
        class InOrdnerButOverlappingSegments {
            @Test
            void receiveOverlappingSegmentsInOrdner(@Mock final Channel channel,
                                                    @Mock final ChannelHandlerContext ctx,
                                                    @Mock final SendBuffer sendBuffer) {
                final TransmissionControlBlock tcb = new TransmissionControlBlock(ReliableTransportConfig.newBuilder().build(), 100, 100, 0, 100, 0, 0, sendBuffer, new RetransmissionQueue(channel), new ReceiveBuffer(channel));
                final ReceiveBuffer buffer = new ReceiveBuffer(channel);

                final ByteBuf data = Unpooled.buffer(230).writeBytes(randomBytes(230));

                // expected 0, got [30,130)
                final Segment seg1 = Segment.ack(30, 100, data.slice(30, 100));
                buffer.receive(ctx, tcb, seg1);
                assertEquals(63_900, tcb.rcvWnd());
                assertEquals(0, tcb.rcvNxt());
                assertEquals(100, buffer.bytes());
                assertEquals(0, buffer.readableBytes());
                assertNotNull(buffer.head);

                // expected 0, got [50,150)
                final Segment seg2 = Segment.ack(50, 100, data.slice(50, 100));
                buffer.receive(ctx, tcb, seg2);
                assertEquals(63_880, tcb.rcvWnd());
                assertEquals(0, tcb.rcvNxt());
                assertEquals(120, buffer.bytes());
                assertEquals(0, buffer.readableBytes());
                assertNotNull(buffer.head);
            }

            @Test
            void receiveSegmentWithTailBeingDuplicate(@Mock final Channel channel,
                                                      @Mock final ChannelHandlerContext ctx,
                                                      @Mock final SendBuffer sendBuffer) {
                when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

                final ByteBuf data = Unpooled.buffer(160).writeBytes(randomBytes(160));

                final ReceiveBufferBlock head = new ReceiveBufferBlock(60, data.slice(60, 100));
                final ReceiveBuffer buffer = new ReceiveBuffer(channel, head, null, 0, 100);
                final TransmissionControlBlock tcb = new TransmissionControlBlock(ReliableTransportConfig.newBuilder().build(), 100, 100, 0, 100, 0, 0, sendBuffer, new RetransmissionQueue(channel), buffer);

                // expected [0,60), got [0,100)
                Segment seg1 = Segment.ack(0, 100, data.slice(0, 100));
                buffer.receive(ctx, tcb, seg1);
                assertEquals(63_840, tcb.rcvWnd());
                assertEquals(160, tcb.rcvNxt());
                assertEquals(160, buffer.bytes());
                assertEquals(160, buffer.readableBytes());
                assertNull(buffer.head);

                buffer.fireRead(ctx, tcb);
                verify(ctx).fireChannelRead(receivedBuf.capture());
                assertEquals(data, receivedBuf.getValue());
                assertEquals(64_000, tcb.rcvWnd());
                assertEquals(0, buffer.bytes());
                assertEquals(0, buffer.readableBytes());
            }
        }

        @Nested
        class OutOfOrderWithNoOverlappingSegments {
            // FIXME
        }

        @Nested
        class OutOfOrderWithOverlappingSegments {
            @Test
            void receiveOverlappingSegmentsOutOfOrder(@Mock final Channel channel,
                                                      @Mock final ChannelHandlerContext ctx,
                                                      @Mock final SendBuffer sendBuffer) {
                when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

                final ReceiveBuffer buffer = new ReceiveBuffer(channel);
                final TransmissionControlBlock tcb = new TransmissionControlBlock(ReliableTransportConfig.newBuilder().build(), 100, 100, 0, 100, 0, 0, sendBuffer, new RetransmissionQueue(channel), buffer);

                final ByteBuf data = Unpooled.buffer(500).writeBytes(randomBytes(500));

                // expected 0, got [120,200)
                final Segment seg2 = Segment.ack(120, 100, data.slice(120, 80));
                buffer.receive(ctx, tcb, seg2);
                assertEquals(63_920, tcb.rcvWnd());
                assertEquals(0, tcb.rcvNxt());
                assertEquals(80, buffer.bytes());
                assertEquals(0, buffer.readableBytes());
                assertNotNull(buffer.head);

                // expected 0, got [0,120)
                final Segment seg1 = Segment.ack(0, 100, data.slice(0, 120));
                buffer.receive(ctx, tcb, seg1);
                assertEquals(63_800, tcb.rcvWnd());
                assertEquals(200, tcb.rcvNxt());
                assertEquals(200, buffer.bytes());
                assertEquals(200, buffer.readableBytes());
                assertNull(buffer.head);

                // expected 200, got [410,500)
                final Segment seg5 = Segment.ack(410, 100, data.slice(410, 90));
                buffer.receive(ctx, tcb, seg5);
                assertEquals(63_710, tcb.rcvWnd());
                assertEquals(200, tcb.rcvNxt());
                assertEquals(290, buffer.bytes());
                assertEquals(200, buffer.readableBytes());
                assertNotNull(buffer.head);

                // expected 200, got [300,400)
                final Segment seg4 = Segment.ack(300, 100, data.slice(300, 110));
                buffer.receive(ctx, tcb, seg4);
                assertEquals(63_600, tcb.rcvWnd());
                assertEquals(200, tcb.rcvNxt());
                assertEquals(400, buffer.bytes());
                assertEquals(200, buffer.readableBytes());
                assertNotNull(buffer.head);

                // expected 200, got [200,300)
                Segment seg3 = Segment.ack(200, 100, data.slice(200, 100));
                buffer.receive(ctx, tcb, seg3);
                assertEquals(63_500, tcb.rcvWnd());
                assertEquals(500, tcb.rcvNxt());
                assertEquals(500, buffer.bytes());
                assertEquals(500, buffer.readableBytes());
                assertNull(buffer.head);

                buffer.fireRead(ctx, tcb);
                verify(ctx).fireChannelRead(receivedBuf.capture());
                assertEquals(data, receivedBuf.getValue());
                assertEquals(64_000, tcb.rcvWnd());
                assertEquals(0, buffer.bytes());
                assertEquals(0, buffer.readableBytes());
            }

            @Test
            void receiveSegmentWithCenterBeingDuplicate(@Mock final Channel channel,
                                                        @Mock final ChannelHandlerContext ctx,
                                                        @Mock final SendBuffer sendBuffer) {
                when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

                final ByteBuf data = Unpooled.buffer(200).writeBytes(randomBytes(200));

                final ReceiveBufferBlock head = new ReceiveBufferBlock(70, data.slice(60, 60));
                final ReceiveBuffer buffer = new ReceiveBuffer(channel, head, null, 0, 60);
                final TransmissionControlBlock tcb = new TransmissionControlBlock(ReliableTransportConfig.newBuilder().build(), 100, 0, 0, 100, 10, 0, sendBuffer, new RetransmissionQueue(channel), buffer);

                // expected [10,70) and [130,210), got [10,210)
                Segment seg1 = Segment.ack(10, 100, data);
                buffer.receive(ctx, tcb, seg1);
                assertEquals(63_800, tcb.rcvWnd());
                assertEquals(210, tcb.rcvNxt());
                assertEquals(200, buffer.bytes());
                assertEquals(200, buffer.readableBytes());
                assertNull(buffer.head);

                buffer.fireRead(ctx, tcb);
                verify(ctx).fireChannelRead(receivedBuf.capture());
                assertEquals(data, receivedBuf.getValue());
                assertEquals(64_000, tcb.rcvWnd());
                assertEquals(0, buffer.bytes());
                assertEquals(0, buffer.readableBytes());
            }

            @Test
            void receiveDuplicates(@Mock final Channel channel,
                                   @Mock final ChannelHandlerContext ctx,
                                   @Mock final SendBuffer sendBuffer) {
                when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

                final ReceiveBuffer buffer = new ReceiveBuffer(channel);
                final TransmissionControlBlock tcb = new TransmissionControlBlock(ReliableTransportConfig.newBuilder().build(), 100, 100, 0, 100, 0, 0, sendBuffer, new RetransmissionQueue(channel), buffer);

                final ByteBuf data = Unpooled.buffer(300).writeBytes(randomBytes(300));

                // neues SEG (0-99)
                final Segment seg1 = Segment.ack(0, 100, data.slice(0, 100));
                buffer.receive(ctx, tcb, seg1);
                assertEquals(63_900, tcb.rcvWnd());
                assertEquals(100, tcb.rcvNxt());
                assertEquals(100, buffer.bytes());
                assertEquals(100, buffer.readableBytes());
                assertNull(buffer.head);

                // identisches SEG (0-99)
                final Segment seg1copy = Segment.ack(0, 100, data.slice(0, 100));
                buffer.receive(ctx, tcb, seg1copy);
                assertEquals(63_900, tcb.rcvWnd());
                assertEquals(100, tcb.rcvNxt());
                assertEquals(100, buffer.bytes());
                assertEquals(100, buffer.readableBytes());
                assertNull(buffer.head);

                // erste 100 bytes doppelt (0-149)
                final Segment seg2 = Segment.ack(0, 100, data.slice(0, 150));
                buffer.receive(ctx, tcb, seg2);
                assertEquals(63_850, tcb.rcvWnd());
                assertEquals(150, tcb.rcvNxt());
                assertEquals(150, buffer.bytes());
                assertEquals(150, buffer.readableBytes());
                assertNull(buffer.head);

                // vorbereitung für nächstes SEG, landet als fragment (250-299)
                final Segment seg3 = Segment.ack(250, 100, data.slice(250, 50));
                buffer.receive(ctx, tcb, seg3);
                assertEquals(63_800, tcb.rcvWnd());
                assertEquals(150, tcb.rcvNxt());
                assertEquals(200, buffer.bytes());
                assertEquals(150, buffer.readableBytes());
                assertNotNull(buffer.head);

                // letze 50 bytes doppelt (200-299)
                final Segment seg4 = Segment.ack(200, 100, data.slice(200, 100));
                buffer.receive(ctx, tcb, seg4);
                assertEquals(63_750, tcb.rcvWnd());
                assertEquals(150, tcb.rcvNxt());
                assertEquals(250, buffer.bytes());
                assertEquals(150, buffer.readableBytes());
                assertNotNull(buffer.head);

                // vorne und hinten doppelt (100-249)
                final Segment seg5 = Segment.ack(100, 100, data.slice(100, 150));
                buffer.receive(ctx, tcb, seg5);
                assertEquals(63_700, tcb.rcvWnd());
                assertEquals(300, tcb.rcvNxt());
                assertEquals(300, buffer.bytes());
                assertEquals(300, buffer.readableBytes());
                assertNull(buffer.head);

                buffer.fireRead(ctx, tcb);
                verify(ctx).fireChannelRead(receivedBuf.capture());
                assertEquals(data, receivedBuf.getValue());
                assertEquals(64_000, tcb.rcvWnd());
                assertEquals(0, buffer.bytes());
                assertEquals(0, buffer.readableBytes());
            }
        }

        @Nested
        class OutOfWindowSegments {
            @Test
            void receiveSegmentThatIsPartiallyBeforeTheReceiveWindow(@Mock final Channel channel,
                                                                     @Mock final ChannelHandlerContext ctx,
                                                                     @Mock final SendBuffer sendBuffer) {
                final ReceiveBuffer buffer = new ReceiveBuffer(channel);
                final TransmissionControlBlock tcb = new TransmissionControlBlock(ReliableTransportConfig.newBuilder().build(), 100, 100, 0, 100, 60, 0, sendBuffer, new RetransmissionQueue(channel), buffer);

                final ByteBuf data = Unpooled.buffer(100).writeBytes(randomBytes(100));

                // expected 60, got [0,100)
                final Segment seg1 = Segment.ack(0, 100, data);
                buffer.receive(ctx, tcb, seg1);
                assertEquals(63_960, tcb.rcvWnd());
                assertEquals(100, tcb.rcvNxt());
                assertEquals(40, buffer.bytes());
                assertEquals(40, buffer.readableBytes());
                assertNull(buffer.head);

                buffer.fireRead(ctx, tcb);
                verify(ctx).fireChannelRead(receivedBuf.capture());
                assertEquals(data.slice(60, 40), receivedBuf.getValue());
                assertEquals(64_000, tcb.rcvWnd());
                assertEquals(0, buffer.bytes());
                assertEquals(0, buffer.readableBytes());
            }

            @Test
            void receiveSegmentThatIsFullyBeforeTheReceiveWindow(@Mock final Channel channel,
                                                                 @Mock final ChannelHandlerContext ctx,
                                                                 @Mock final SendBuffer sendBuffer) {
                final ReceiveBuffer buffer = new ReceiveBuffer(channel, null, null, 0, 0);
                final TransmissionControlBlock tcb = new TransmissionControlBlock(ReliableTransportConfig.newBuilder().build(), 100, 0, 0, 100, 100, 0, sendBuffer, new RetransmissionQueue(channel), buffer);

                final ByteBuf data = Unpooled.buffer(90).writeBytes(randomBytes(90));

                // expected [100,64100), got [10,100)
                Segment seg1 = Segment.ack(10, 100, data);
                buffer.receive(ctx, tcb, seg1);
                assertEquals(64000, tcb.rcvWnd());
                assertEquals(100, tcb.rcvNxt());
                assertEquals(0, buffer.bytes());
                assertEquals(0, buffer.readableBytes());
                assertNull(buffer.head);
            }

            @Test
            void receiveSegmentThatIsPartiallyBehindTheReceiveWindow(@Mock final Channel channel,
                                                                     @Mock final ChannelHandlerContext ctx,
                                                                     @Mock final SendBuffer sendBuffer) {
                final ReceiveBuffer buffer = new ReceiveBuffer(channel, null, null, 0, 0);
                final TransmissionControlBlock tcb = new TransmissionControlBlock(ReliableTransportConfig.newBuilder().build(), 100, 0, 0, 100, 100, 0, sendBuffer, new RetransmissionQueue(channel), buffer);

                final ByteBuf data = Unpooled.buffer(100).writeBytes(randomBytes(100));

                // expected [100,150), got [100,200)
                Segment seg1 = Segment.ack(100, 100, data);
                buffer.receive(ctx, tcb, seg1);
                assertEquals(0, tcb.rcvWnd());
                assertEquals(150, tcb.rcvNxt());
                assertEquals(50, buffer.bytes());
                assertEquals(50, buffer.readableBytes());
                assertNull(buffer.head);
            }

            @Test
            void receiveSegmentThatIsFullyBehindTheReceiveWindow(@Mock final Channel channel,
                                                                 @Mock final ChannelHandlerContext ctx,
                                                                 @Mock final SendBuffer sendBuffer) {
                final ReceiveBuffer buffer = new ReceiveBuffer(channel, null, null, 0, 0);
                final TransmissionControlBlock tcb = new TransmissionControlBlock(ReliableTransportConfig.newBuilder().build(), 100, 0, 0, 100, 100, 0, sendBuffer, new RetransmissionQueue(channel), buffer);

                final ByteBuf data = Unpooled.buffer(100).writeBytes(randomBytes(100));

                // expected [100,64100), got [64100,64200)
                Segment seg1 = Segment.ack(64100, 100, data);
                buffer.receive(ctx, tcb, seg1);
                assertEquals(64000, tcb.rcvWnd());
                assertEquals(100, tcb.rcvNxt());
                assertEquals(0, buffer.bytes());
                assertEquals(0, buffer.readableBytes());
                assertNull(buffer.head);
            }
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
