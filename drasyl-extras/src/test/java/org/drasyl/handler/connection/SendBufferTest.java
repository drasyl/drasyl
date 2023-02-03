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
import org.drasyl.handler.connection.SendBuffer.ReadMark;
import org.drasyl.handler.connection.SendBuffer.SendBufferEntry;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.util.RandomUtil.randomBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendBufferTest {
    @Nested
    class Enqueue {
        @Test
        void shouldAddGivenBytesToTheEndOfTheBuffer(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            when(channel.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

            final SendBuffer buffer = new SendBuffer(channel);

            // enqueue 10 bytes
            final ByteBuf buf = Unpooled.buffer(15).writeBytes(randomBytes(15));
            final ChannelPromise promise1 = mock(ChannelPromise.class);
            buffer.enqueue(buf.slice(0, 10), promise1);
            assertEquals(buffer.head, new SendBufferEntry(buf.slice(0, 10), promise1));
            assertEquals(buffer.tail, new SendBufferEntry(buf.slice(0, 10), promise1));
            assertEquals(buffer.readMark, new ReadMark(buffer.head));
            assertEquals(buffer.acknowledgementIndex, 0);
            assertEquals(buffer.size(), 1);
            assertEquals(buffer.bytes(), 10);
            assertEquals(10, buffer.readableBytes());
            assertEquals(0, buffer.acknowledgeableBytes());

            // enqueue another 5 bytes
            final ChannelPromise promise2 = mock(ChannelPromise.class);
            buffer.enqueue(buf.slice(10, 5), promise2);
            assertEquals(buffer.head, new SendBufferEntry(buf.slice(0, 10), promise1, new SendBufferEntry(buf.slice(10, 5), promise2)));
            assertEquals(buffer.tail, new SendBufferEntry(buf.slice(10, 5), promise2));
            assertEquals(buffer.readMark, new ReadMark(buffer.head));
            assertEquals(buffer.acknowledgementIndex, 0);
            assertEquals(buffer.size(), 2);
            assertEquals(buffer.bytes(), 15);
            assertEquals(15, buffer.readableBytes());
            assertEquals(0, buffer.acknowledgeableBytes());

            // read everything
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

            final SendBuffer buffer = new SendBuffer(channel);

            // preparation: fill buffer (10 and 5 bytes)
            final ByteBuf buf = Unpooled.buffer(15).writeBytes(randomBytes(15));
            final ChannelPromise promise1 = new DefaultChannelPromise(channel);
            buffer.enqueue(buf.slice(0, 10), promise1);
            final ChannelPromise promise2 = new DefaultChannelPromise(channel);
            buffer.enqueue(buf.slice(10, 5), promise2);

            // read 5 bytes (part of first buf)
            assertEquals(buf.slice(0, 5), buffer.read(5));
            assertEquals(buffer.head, new SendBufferEntry(buf.slice(0, 10), promise1, new SendBufferEntry(buf.slice(10, 5), promise2)));
            assertEquals(buffer.tail, new SendBufferEntry(buf.slice(10, 5), promise2));
            assertEquals(buffer.readMark, new ReadMark(buffer.head, 5));
            assertEquals(buffer.acknowledgementIndex, 0);
            assertEquals(buffer.size(), 2);
            assertEquals(buffer.bytes(), 15);
            assertEquals(10, buffer.readableBytes());
            assertEquals(5, buffer.acknowledgeableBytes());

            // read 6 bytes (remainder of first buf and start of second buf)
            assertEquals(buf.slice(5, 6), buffer.read(6));
            assertEquals(buffer.head, new SendBufferEntry(buf.slice(0, 10), promise1, new SendBufferEntry(buf.slice(10, 5), promise2)));
            assertEquals(buffer.tail, new SendBufferEntry(buf.slice(10, 5), promise2));
            assertEquals(buffer.readMark, new ReadMark(buffer.tail, 1));
            assertEquals(buffer.acknowledgementIndex, 0);
            assertEquals(buffer.size(), 2);
            assertEquals(buffer.bytes(), 15);
            assertEquals(4, buffer.readableBytes());
            assertEquals(11, buffer.acknowledgeableBytes());

            // read 10 bytes (remainder of second buf; only 4 bytes)
            assertEquals(buf.slice(11, 4), buffer.read(10));
            assertEquals(buffer.head, new SendBufferEntry(buf.slice(0, 10), promise1, new SendBufferEntry(buf.slice(10, 5), promise2)));
            assertEquals(buffer.tail, new SendBufferEntry(buf.slice(10, 5), promise2));
            assertEquals(buffer.readMark, new ReadMark(buffer.tail, 5));
            assertEquals(buffer.acknowledgementIndex, 0);
            assertEquals(buffer.size(), 2);
            assertEquals(buffer.bytes(), 15);
            assertEquals(0, buffer.readableBytes());
            assertEquals(15, buffer.acknowledgeableBytes());

            // read 99 bytes (nothing remain)
            assertEquals(Unpooled.EMPTY_BUFFER, buffer.read(99));
            assertEquals(buffer.head, new SendBufferEntry(buf.slice(0, 10), promise1, new SendBufferEntry(buf.slice(10, 5), promise2)));
            assertEquals(buffer.tail, new SendBufferEntry(buf.slice(10, 5), promise2));
            assertEquals(buffer.readMark, new ReadMark(buffer.tail, 5));
            assertEquals(buffer.acknowledgementIndex, 0);
            assertEquals(buffer.size(), 2);
            assertEquals(buffer.bytes(), 15);
            assertEquals(0, buffer.readableBytes());
            assertEquals(15, buffer.acknowledgeableBytes());
        }
    }

    @Nested
    class Unacknowledged {
        @Test
        void shouldReadGivenBytesFromBeginOfTheBuffer(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            when(channel.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

            final SendBuffer buffer = new SendBuffer(channel);

            // preparation: fill buffer (10 and 5 bytes) and read 13 bytes
            final ByteBuf buf = Unpooled.buffer(15).writeBytes(randomBytes(15));
            final ChannelPromise promise1 = new DefaultChannelPromise(channel);
            buffer.enqueue(buf.slice(0, 10), promise1);
            final ChannelPromise promise2 = new DefaultChannelPromise(channel);
            buffer.enqueue(buf.slice(10, 5), promise2);
            buffer.read(13);

            // get a few bytes
            assertEquals(buf.slice(0, 4), buffer.unacknowledged(4));

            // get all bytes
            assertEquals(buf.slice(0, 13), buffer.unacknowledged(13));

            // try to get too many bytes
            assertEquals(buf.slice(0, 13), buffer.unacknowledged(20));
            assertEquals(buffer.acknowledgementIndex, 0);

            // ACKed bytes should be removed
            buffer.acknowledge(1);
            assertEquals(buffer.acknowledgementIndex, 1);
            assertEquals(buf.slice(1, 12), buffer.unacknowledged(20));
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

            final SendBuffer buffer = new SendBuffer(channel);

            // preparation: fill buffer (10 and 5 bytes) and read 13 bytes
            final ByteBuf buf = Unpooled.buffer(15).writeBytes(randomBytes(15));
            final ChannelPromise promise1 = new DefaultChannelPromise(channel);
            buffer.enqueue(buf.slice(0, 10), promise1);
            final ChannelPromise promise2 = new DefaultChannelPromise(channel);
            buffer.enqueue(buf.slice(10, 5), promise2);
            buffer.read(13);

            // ack 5 bytes (as no buf is completely acked, no promise should be done)
            buffer.acknowledge(5);
            assertEquals(buffer.head, new SendBufferEntry(buf.slice(0, 10), promise1, new SendBufferEntry(buf.slice(10, 5), promise2)));
            assertEquals(buffer.tail, new SendBufferEntry(buf.slice(10, 5), promise2));
            assertEquals(buffer.readMark, new ReadMark(buffer.tail, 3));
            assertEquals(buffer.acknowledgementIndex, 5);
            assertEquals(buffer.size(), 2);
            assertEquals(buffer.bytes(), 10);
            assertFalse(promise1.isDone());
            assertFalse(promise2.isDone());
            assertEquals(8, buffer.acknowledgeableBytes());

            // ack another 6 bytes (first buf done)
            buffer.acknowledge(6);
            assertEquals(buffer.head, new SendBufferEntry(buf.slice(10, 5), promise2));
            assertEquals(buffer.tail, new SendBufferEntry(buf.slice(10, 5), promise2));
            assertEquals(buffer.readMark, new ReadMark(buffer.head, 3));
            assertEquals(buffer.acknowledgementIndex, 1);
            assertEquals(buffer.size(), 1);
            assertEquals(buffer.bytes(), 4);
            assertTrue(promise1.isDone());
            assertFalse(promise2.isDone());
            assertEquals(2, buffer.acknowledgeableBytes());

            // ack another 10 bytes (but only 2 are allowed)
            buffer.acknowledge(10);
            assertEquals(buffer.head, new SendBufferEntry(buf.slice(10, 5), promise2));
            assertEquals(buffer.tail, new SendBufferEntry(buf.slice(10, 5), promise2));
            assertEquals(buffer.readMark, new ReadMark(buffer.head, 3));
            assertEquals(buffer.acknowledgementIndex, 3);
            assertEquals(buffer.size(), 1);
            assertEquals(buffer.bytes(), 2);
            assertTrue(promise1.isDone());
            assertFalse(promise2.isDone());
            assertEquals(0, buffer.acknowledgeableBytes());

            // ack more (0 allowed)
            buffer.acknowledge(99);
            assertEquals(0, buffer.acknowledgeableBytes());

            // read remainder to ACK remainder
            buffer.read(2);
            buffer.acknowledge(2);
            assertNull(buffer.head);
            assertNull(buffer.tail);
            assertNull(buffer.readMark);
            assertEquals(buffer.acknowledgementIndex, 0);
            assertEquals(buffer.size(), 0);
            assertEquals(buffer.bytes(), 0);
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
        void shouldReleaseAllSegmentsAndFailAllFutures(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            final CoalescingBufferQueue queue = new CoalescingBufferQueue(channel, 4, false);
            final SendBuffer buffer = null;//new SendBuffer(channel, queue, bufAndListenerPairs);
            final ByteBuf buf = Unpooled.buffer(10).writeBytes(randomBytes(10));
            final ChannelPromise promise = mock(ChannelPromise.class);
            buffer.enqueue(buf, promise);

            buffer.release();

            assertEquals(0, buf.refCnt());
            verify(promise).tryFailure(any());
        }
    }
}