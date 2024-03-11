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
import io.netty.channel.DefaultChannelPromise;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.drasyl.util.RandomUtil.randomBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendBufferTest {
    @Nested
    class Enqueue {
        @Test
        void shouldAddGivenBytesToTheEndOfTheBuffer(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel, @Mock final ChannelPromise channelPromise) {
            when(channel.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

            final SendBuffer buffer = new SendBuffer(channel);

            // enqueue 10 bytes
            final ByteBuf buf = Unpooled.buffer(15).writeBytes(randomBytes(15));
            final ChannelPromise promise1 = mock(ChannelPromise.class);
            buffer.enqueue(buf.copy(0, 10), promise1);
            assertEquals(10, buffer.length());

            // enqueue another 5 bytes
            final ChannelPromise promise2 = mock(ChannelPromise.class);
            buffer.enqueue(buf.copy(10, 5), promise2);
            assertEquals(15, buffer.length());

            // read everything
            final ByteBuf read = buffer.read(999, new AtomicBoolean(), channelPromise);
            assertEquals(buf, read);
            assertEquals(0, buffer.length());

            buffer.release();
            buf.release();
            read.release();
        }
    }

    @Nested
    class Read {
        @Test
        void shouldReadGivenBytesFromBeginOfTheBuffer(@Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            when(channel.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
            when(channel.eventLoop().inEventLoop()).thenReturn(true);

            final SendBuffer buffer = new SendBuffer(channel);

            // preparation: fill buffer (10 and 5 bytes)
            final ByteBuf buf = Unpooled.buffer(15).writeBytes(randomBytes(15));
            final ChannelPromise enqueuePromise1 = new DefaultChannelPromise(channel);
            buffer.enqueue(buf.copy(0, 10), enqueuePromise1);
            final ChannelPromise enqueuePromise2 = new DefaultChannelPromise(channel);
            buffer.enqueue(buf.copy(10, 5), enqueuePromise2);
            assertFalse(enqueuePromise1.isDone());
            assertFalse(enqueuePromise2.isDone());

            // read 5 bytes (part of first buf)
            final ChannelPromise readPromise1 = new DefaultChannelPromise(channel).setSuccess();
            final ByteBuf read1 = buffer.read(5, new AtomicBoolean(), readPromise1);
            assertEquals(buf.copy(0, 5), read1);
            assertEquals(10, buffer.length());
            assertFalse(enqueuePromise1.isDone());
            assertFalse(enqueuePromise2.isDone());

            // read 6 bytes (remainder of first buf and start of second buf)
            final ChannelPromise readPromise2 = new DefaultChannelPromise(channel).setSuccess();
            final ByteBuf read2 = buffer.read(6, new AtomicBoolean(), readPromise2);
            assertEquals(buf.copy(5, 6), read2);
            assertEquals(4, buffer.length());
            assertTrue(enqueuePromise1.isDone());
            assertFalse(enqueuePromise2.isDone());

            // read 10 bytes (remainder of second buf; only 4 bytes)
            final ChannelPromise readPromise3 = new DefaultChannelPromise(channel).setSuccess();
            final ByteBuf read3 = buffer.read(10, new AtomicBoolean(), readPromise3);
            assertEquals(buf.copy(11, 4), read3);
            assertEquals(0, buffer.length());
            assertTrue(enqueuePromise1.isDone());
            assertTrue(enqueuePromise2.isDone());

            // read 99 bytes (nothing remain)
            final ChannelPromise readPromise4 = new DefaultChannelPromise(channel).setSuccess();
            assertEquals(Unpooled.EMPTY_BUFFER, buffer.read(99, new AtomicBoolean(), readPromise4));
            assertEquals(0, buffer.length());

            buffer.release();
            buf.release();
            read1.release();
            read2.release();
            read3.release();
        }
    }
}
