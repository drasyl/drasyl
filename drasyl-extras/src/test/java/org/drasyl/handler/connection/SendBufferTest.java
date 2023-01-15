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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CoalescingBufferQueue;
import io.netty.channel.DefaultChannelPromise;
import org.junit.jupiter.api.Disabled;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendBufferTest {
    @Test
    @Disabled
    void spielwiese(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
        final SendBuffer buffer = new SendBuffer(channel);

        final ByteBuf buf = Unpooled.buffer(30).writeBytes(randomBytes(30));

        // enqueue 10 bytes
        ChannelPromise promise1 = mock(ChannelPromise.class);
        buffer.enqueue(buf.slice(0, 10), promise1);

        // enqueue 10 bytes
        promise1 = mock(ChannelPromise.class);
        buffer.enqueue(buf.slice(10, 10), promise1);

        // enqueue 10 bytes
        promise1 = mock(ChannelPromise.class);
        buffer.enqueue(buf.slice(20, 10), promise1);

        // read 5 bytes
        assertEquals(buf.slice(0, 5), buffer.read(5));

        // ack 5 bytes
        buffer.acknowledge(4);

        // read 2 bytes
        assertEquals(buf.slice(5, 2), buffer.read(2));

        // try to ack 99 bytes
        buffer.acknowledge(99);
    }

    @Nested
    class Enqueue {
        @Test
        void shouldAddGivenBytesToTheEndOfTheBuffer(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            when(channel.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

            final SendBuffer buffer = new SendBuffer(channel);

            final ByteBuf buf = Unpooled.buffer(15).writeBytes(randomBytes(15));

            // enqueue 10 bytes
            final ChannelPromise promise1 = mock(ChannelPromise.class);
            buffer.enqueue(buf.slice(0, 10), promise1);
            assertEquals(10, buffer.readableBytes());
            assertEquals(0, buffer.acknowledgeableBytes());

            // enqueue another 5 bytes
            final ChannelPromise promise2 = mock(ChannelPromise.class);
            buffer.enqueue(buf.slice(10, 5), promise2);
            assertEquals(15, buffer.readableBytes());
            assertEquals(0, buffer.acknowledgeableBytes());

            assertEquals(buf, buffer.read(999));
            assertEquals(0, buffer.readableBytes());
            assertEquals(15, buffer.acknowledgeableBytes());
        }
    }

    @Nested
    class Read {
        @Test
        void shouldReadGivenBytesFromBeginOfTheBuffer(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            when(channel.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

            final ByteBuf addBuf = Unpooled.buffer(15).writeBytes(randomBytes(15));

            // fill buffer
            final SendBuffer buffer = new SendBuffer(channel);
            final ChannelPromise promise1 = new DefaultChannelPromise(channel);
            buffer.enqueue(addBuf.slice(0, 10), promise1);
            final ChannelPromise promise2 = new DefaultChannelPromise(channel);
            buffer.enqueue(addBuf.slice(10, 5), promise2);

            // read part of first buf
            final ByteBuf readBuf1 = buffer.read(5);
            assertEquals(addBuf.slice(0, 5), readBuf1);
            assertEquals(10, buffer.readableBytes());
            assertEquals(5, buffer.acknowledgeableBytes());

            // read remaining part of first buf and part of second buf
            final ByteBuf readBuf2 = buffer.read(6);
            assertEquals(addBuf.slice(5, 6), readBuf2);
            assertEquals(4, buffer.readableBytes());
            assertEquals(11, buffer.acknowledgeableBytes());

            // read remainder
            final ByteBuf readBuf3 = buffer.read(10);
            assertEquals(addBuf.slice(11, 4), readBuf3);
            assertEquals(0, buffer.readableBytes());
            assertEquals(15, buffer.acknowledgeableBytes());

            // nothing remain
            assertEquals(Unpooled.EMPTY_BUFFER, buffer.read(99));
            assertEquals(0, buffer.readableBytes());
            assertEquals(15, buffer.acknowledgeableBytes());
        }
    }

    @Nested
    class Unacknowledged {
        @Test
        void shouldReadGivenBytesFromBeginOfTheBuffer(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            when(channel.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

            final ByteBuf addBuf = Unpooled.buffer(15).writeBytes(randomBytes(15));

            // fill buffer
            final SendBuffer buffer = new SendBuffer(channel);
            final ChannelPromise promise1 = new DefaultChannelPromise(channel);
            buffer.enqueue(addBuf.slice(0, 10), promise1);
            final ChannelPromise promise2 = new DefaultChannelPromise(channel);
            buffer.enqueue(addBuf.slice(10, 5), promise2);
            buffer.read(13);

            // read a few bytes
            assertEquals(addBuf.slice(0, 4), buffer.unacknowledged(4));

            // read all bytes
            assertEquals(addBuf.slice(0, 13), buffer.unacknowledged(13));

            // read too many bytes
            assertEquals(addBuf.slice(0, 13), buffer.unacknowledged(20));

            // ACKing should shift further
            buffer.acknowledge(1);

            // read too many bytes
            assertEquals(addBuf.slice(1, 12), buffer.unacknowledged(20));
        }

        @Test
        void shouldReadNothing(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            final SendBuffer buffer = new SendBuffer(channel);

            assertEquals(Unpooled.EMPTY_BUFFER, buffer.unacknowledged(1));
        }
    }

    @Nested
    class Acknowledge {
        @Test
        void shouldAcknowledgeGivenBytesFromBeginOfTheBuffer(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            when(channel.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

            final ByteBuf addBuf = Unpooled.buffer(15).writeBytes(randomBytes(15));

            final SendBuffer buffer = new SendBuffer(channel);
            final ChannelPromise promise1 = new DefaultChannelPromise(channel);
            buffer.enqueue(addBuf.slice(0, 10), promise1);
            final ChannelPromise promise2 = new DefaultChannelPromise(channel);
            buffer.enqueue(addBuf.slice(10, 5), promise2);
            buffer.read(13);

            // ack 5 bytes (as no buf is completely acked, not promise should be done)
            buffer.acknowledge(5);
            assertFalse(promise1.isDone());
            assertFalse(promise2.isDone());
            assertEquals(8, buffer.acknowledgeableBytes());

            // ack another 6 bytes (first buf done)
            buffer.acknowledge(6);
            assertTrue(promise1.isDone());
            assertFalse(promise2.isDone());
            assertEquals(2, buffer.acknowledgeableBytes());

            // ack another 10 bytes (but only 2 are allowed)
            buffer.acknowledge(10);
            assertTrue(promise1.isDone());
            assertFalse(promise2.isDone());
            assertEquals(0, buffer.acknowledgeableBytes());

            // ack more (0 allowed)
            buffer.acknowledge(99);
            assertEquals(0, buffer.acknowledgeableBytes());

            buffer.read(2);
            buffer.acknowledge(2);
            assertTrue(promise2.isDone());
        }
    }

    @Nested
    class IsEmpty {
        @Test
        @Disabled
        void shouldReturnTrueIfBufferContainsNoBytes(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            final CoalescingBufferQueue queue = new CoalescingBufferQueue(channel, 4, false);
            final SendBuffer buffer = null;//new SendBuffer(channel, queue, bufAndListenerPairs);

            assertTrue(buffer.isEmpty());
        }

        @Test
        @Disabled
        void shouldReturnFalseIfBufferContainsBytes(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            final CoalescingBufferQueue queue = new CoalescingBufferQueue(channel, 4, false);
            final SendBuffer buffer = null;//new SendBuffer(channel, queue, bufAndListenerPairs);
            final ByteBuf buf = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final ChannelPromise promise = mock(ChannelPromise.class);
            buffer.enqueue(buf, promise);

            assertFalse(buffer.isEmpty());

            buf.release();
        }
    }

    @Nested
    class ReleaseAndFailAll {
        @Test
        @Disabled
        void shouldReleaseAllSegmentsAndFailAllFutures(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel,
                                                       @Mock final Throwable cause) {
            final CoalescingBufferQueue queue = new CoalescingBufferQueue(channel, 4, false);
            final SendBuffer buffer = null;//new SendBuffer(channel, queue, bufAndListenerPairs);
            final ByteBuf buf = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final ChannelPromise promise = mock(ChannelPromise.class);
            buffer.enqueue(buf, promise);

            buffer.releaseAndFailAll(cause);

            assertEquals(0, buf.refCnt());
            verify(promise).tryFailure(any());
        }
    }
}
