package org.drasyl.handler.connection;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.PendingWriteQueue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetransmissionQueueTest {
    @Nested
    class Add {
        @Test
        void shouldAddGivenSegmentsToTheEndOfTheQueu(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(ctx.executor().inEventLoop()).thenReturn(true);
            final PendingWriteQueue pendingWrites = new PendingWriteQueue(ctx);
            final RetransmissionQueue buffer = new RetransmissionQueue(ctx.channel(), pendingWrites);
            final ConnectionHandshakeSegment seg1 = mock(ConnectionHandshakeSegment.class);
            final ChannelPromise promise1 = mock(ChannelPromise.class);
            final ConnectionHandshakeSegment seg2 = mock(ConnectionHandshakeSegment.class);
            final ChannelPromise promise2 = mock(ChannelPromise.class);

            buffer.add(seg1, promise1);
            buffer.add(seg2, promise2);

            assertEquals(seg1, pendingWrites.current());
            pendingWrites.remove();
            assertEquals(seg2, pendingWrites.current());
            pendingWrites.remove();
        }

        @Test
        void shouldUpdateChannelWritability() {
            // TODO
        }
    }

    @Nested
    class ReleaseAndFailAll {
        @Test
        void shouldReleaseAllSegmentsAndFailAllFutures(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                       @Mock final Throwable cause) {
            when(ctx.executor().inEventLoop()).thenReturn(true);
            final PendingWriteQueue pendingWrites = new PendingWriteQueue(ctx);
            final RetransmissionQueue buffer = new RetransmissionQueue(ctx.channel(), pendingWrites);
            final ConnectionHandshakeSegment seg = mock(ConnectionHandshakeSegment.class);
            final ChannelPromise promise = mock(ChannelPromise.class);
            pendingWrites.add(seg, promise);

            buffer.releaseAndFailAll(cause);

            verify(seg).release();
            verify(promise).tryFailure(cause);
        }
    }

    @Nested
    class Current {
        @Test
        void shouldReturnCurrentSegment(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(ctx.executor().inEventLoop()).thenReturn(true);
            final PendingWriteQueue pendingWrites = new PendingWriteQueue(ctx);
            final RetransmissionQueue buffer = new RetransmissionQueue(ctx.channel(), pendingWrites);
            final ConnectionHandshakeSegment seg = mock(ConnectionHandshakeSegment.class);
            final ChannelPromise promise = mock(ChannelPromise.class);
            pendingWrites.add(seg, promise);

            assertEquals(seg, buffer.current());
        }
    }

    @Nested
    class RemoveAndSucceedCurrent {
        @Test
        void shouldRemoveSegmentAndSucceedTheFuture(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(ctx.executor().inEventLoop()).thenReturn(true);
            final PendingWriteQueue pendingWrites = new PendingWriteQueue(ctx);
            final RetransmissionQueue buffer = new RetransmissionQueue(ctx.channel(), pendingWrites);
            final ConnectionHandshakeSegment seg = mock(ConnectionHandshakeSegment.class);
            final ChannelPromise promise = mock(ChannelPromise.class);
            pendingWrites.add(seg, promise);

            buffer.removeAndSucceedCurrent();

            assertTrue(pendingWrites.isEmpty());
            verify(promise).setSuccess();
        }

        @Test
        void shouldUpdateChannelWritability() {
            // TODO
        }
    }

    @Nested
    class Size {
        @Test
        void shouldReturnNumberOfSegmentsInBuffer(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(ctx.executor().inEventLoop()).thenReturn(true);
            when(ctx.channel().eventLoop().inEventLoop()).thenReturn(true);
            final PendingWriteQueue pendingWrites = new PendingWriteQueue(ctx);
            final RetransmissionQueue buffer = new RetransmissionQueue(ctx.channel(), pendingWrites);
            final ConnectionHandshakeSegment seg = mock(ConnectionHandshakeSegment.class);
            final ChannelPromise promise = mock(ChannelPromise.class);
            pendingWrites.add(seg, promise);

            assertEquals(1, buffer.size());
        }
    }
}
