package org.drasyl.handler.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CoalescingBufferQueue;
import io.netty.channel.DefaultChannelPromise;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.util.RandomUtil.randomBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendBufferTest {
    @Nested
    class Add {
        @Test
        void shouldAddGivenBytesToTheEndOfTheBuffer(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(ctx.channel().alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
            final CoalescingBufferQueue queue = new CoalescingBufferQueue(ctx.channel(), 4, false);
            final SendBuffer buffer = new SendBuffer(queue);
            final ByteBuf buf1 = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final ChannelPromise promise1 = mock(ChannelPromise.class);
            final ByteBuf buf2 = Unpooled.buffer(5).writeBytes(randomBytes(5));
            final ChannelPromise promise2 = mock(ChannelPromise.class);

            buffer.add(buf1, promise1);
            buffer.add(buf2, promise2);

            final ByteBuf removed = queue.remove(15, ctx.newPromise());
            assertEquals(15, removed.readableBytes());
            final CompositeByteBuf expectedBuf = Unpooled.compositeBuffer(2).addComponents(true, buf1, buf2);
            assertEquals(expectedBuf, removed);

            expectedBuf.release();
        }

        @Test
        void shouldUpdateChannelWritability() {
            // TODO
        }
    }

    @Nested
    class IsEmpty {
        @Test
        void shouldReturnTrueIfBufferContainsNoBytes(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final CoalescingBufferQueue queue = new CoalescingBufferQueue(ctx.channel(), 4, false);
            final SendBuffer buffer = new SendBuffer(queue);

            assertTrue(buffer.isEmpty());
        }

        @Test
        void shouldReturnFalseIfBufferContainsBytes(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final CoalescingBufferQueue queue = new CoalescingBufferQueue(ctx.channel(), 4, false);
            final SendBuffer buffer = new SendBuffer(queue);
            final ByteBuf buf = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final ChannelPromise promise = mock(ChannelPromise.class);
            buffer.add(buf, promise);

            assertFalse(buffer.isEmpty());

            buf.release();
        }
    }

    @Nested
    class Remove {
        @Test
        void shouldRemoveGivenAmountOfBytesFromBuffer(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(ctx.channel().alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
            when(ctx.channel().eventLoop().inEventLoop()).thenReturn(true);
            final CoalescingBufferQueue queue = new CoalescingBufferQueue(ctx.channel(), 4, false);
            final SendBuffer buffer = new SendBuffer(queue);
            final ByteBuf buf1 = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final ChannelPromise promise1 = mock(ChannelPromise.class);
            final ByteBuf buf2 = Unpooled.buffer(5).writeBytes(randomBytes(5));
            final ChannelPromise promise2 = mock(ChannelPromise.class);
            buffer.add(buf1, promise1);
            buffer.add(buf2, promise2);

            final ChannelPromise aggregatePromise = new DefaultChannelPromise(ctx.channel());
            final ByteBuf removed = buffer.remove(13, aggregatePromise);
            assertEquals(13, removed.readableBytes());
            assertEquals(2, buffer.readableBytes());

            final CompositeByteBuf expectedBuf = Unpooled.compositeBuffer(2).addComponents(true, buf1, buf2.slice(0, 3));
            assertEquals(expectedBuf, removed);

            aggregatePromise.setSuccess();
            verify(promise1).trySuccess(null);
            verify(promise2, never()).trySuccess(null);

            expectedBuf.release();
            buf2.release();
        }
    }

    @Nested
    class ReleaseAndFailAll {
        @Test
        void shouldReleaseAllSegmentsAndFailAllFutures(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                       @Mock final Throwable cause) {
            final CoalescingBufferQueue queue = new CoalescingBufferQueue(ctx.channel(), 4, false);
            final SendBuffer buffer = new SendBuffer(queue);
            final ByteBuf buf = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final ChannelPromise promise = mock(ChannelPromise.class);
            buffer.add(buf, promise);

            buffer.releaseAndFailAll(cause);

            assertEquals(0, buf.refCnt());
            verify(promise).tryFailure(any());
        }
    }
}
