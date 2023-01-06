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

import io.netty.channel.Channel;
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
    private ChannelHandlerContext ctx;
    private TransmissionControlBlock tcb;
    private RttMeasurement rttMeasurement;

    @Nested
    class Add {
        @Test
        void shouldAddGivenSegmentsToTheEndOfTheQueu(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            when(channel.eventLoop().inEventLoop()).thenReturn(true);
            final PendingWriteQueue pendingWrites = new PendingWriteQueue(channel);
            final RetransmissionQueue buffer = new RetransmissionQueue(channel, pendingWrites);
            final ConnectionHandshakeSegment seg1 = mock(ConnectionHandshakeSegment.class);
            final ChannelPromise promise1 = mock(ChannelPromise.class);
            final ConnectionHandshakeSegment seg2 = mock(ConnectionHandshakeSegment.class);
            final ChannelPromise promise2 = mock(ChannelPromise.class);

            buffer.add(ctx, seg1, promise1, rttMeasurement);
            buffer.add(ctx, seg2, promise2, rttMeasurement);

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
        void shouldReleaseAllSegmentsAndFailAllFutures(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel,
                                                       @Mock final Throwable cause) {
            when(channel.eventLoop().inEventLoop()).thenReturn(true);
            final PendingWriteQueue pendingWrites = new PendingWriteQueue(channel);
            final RetransmissionQueue buffer = new RetransmissionQueue(channel, pendingWrites);
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
        void shouldReturnCurrentSegment(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            when(channel.eventLoop().inEventLoop()).thenReturn(true);
            final PendingWriteQueue pendingWrites = new PendingWriteQueue(channel);
            final RetransmissionQueue buffer = new RetransmissionQueue(channel, pendingWrites);
            final ConnectionHandshakeSegment seg = mock(ConnectionHandshakeSegment.class);
            final ChannelPromise promise = mock(ChannelPromise.class);
            pendingWrites.add(seg, promise);

            assertEquals(seg, buffer.current());
        }
    }

    @Nested
    class RemoveAndSucceedCurrent {
        @Test
        void shouldRemoveSegmentAndSucceedTheFuture(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            when(channel.eventLoop().inEventLoop()).thenReturn(true);
            final PendingWriteQueue pendingWrites = new PendingWriteQueue(channel);
            final RetransmissionQueue buffer = new RetransmissionQueue(channel, pendingWrites);
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
        void shouldReturnNumberOfSegmentsInBuffer(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            when(channel.eventLoop().inEventLoop()).thenReturn(true);
            final PendingWriteQueue pendingWrites = new PendingWriteQueue(channel);
            final RetransmissionQueue buffer = new RetransmissionQueue(channel, pendingWrites);
            final ConnectionHandshakeSegment seg = mock(ConnectionHandshakeSegment.class);
            final ChannelPromise promise = mock(ChannelPromise.class);
            pendingWrites.add(seg, promise);

            assertEquals(1, buffer.size());
        }
    }
}
