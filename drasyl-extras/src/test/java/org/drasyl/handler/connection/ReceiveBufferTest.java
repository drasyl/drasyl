package org.drasyl.handler.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
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
            final ReceiveBuffer buffer = new ReceiveBuffer(channel, queue);
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
