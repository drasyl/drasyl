/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.stream;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MessageChunksBufferTest {
    @Test
    void shouldCollectAllChunksAndThenPassThemInCorrectOrder() {
        final ChannelHandler handler = new MessageChunksBuffer(1024, 1000, 65535);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final MessageChunk chunk1 = new MessageChunk((byte) 42, 0, Unpooled.copiedBuffer("chunk1", UTF_8));
        final MessageChunk chunk2 = new MessageChunk((byte) 42, 1, Unpooled.copiedBuffer("chunk2", UTF_8));
        final MessageChunk chunk3 = new LastMessageChunk((byte) 42, 2, Unpooled.copiedBuffer("chunk3", UTF_8));

        assertFalse(channel.writeInbound(chunk2));
        assertFalse(channel.writeInbound(chunk3));
        assertTrue(channel.writeInbound(chunk1));
        assertTrue(channel.finish());

        assertEquals(chunk1, channel.readInbound());
        assertEquals(chunk2, channel.readInbound());
        assertEquals(chunk3, channel.readInbound());

        chunk1.release();
        chunk2.release();
        chunk3.release();

        channel.checkException();
    }

    @Test
    void shouldCollectMaxNumberOfChunks() {
        final ChannelHandler handler = new MessageChunksBuffer(1024, 1000, 65535);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        for (int i = 0; i < 1_000; i++) {
            final MessageChunk chunk = new MessageChunk((byte) 42, i, Unpooled.EMPTY_BUFFER);
            assertFalse(channel.writeInbound(chunk));
        }

        final MessageChunk chunk = new LastMessageChunk((byte) 42, 1_000, Unpooled.EMPTY_BUFFER);
        assertTrue(channel.writeInbound(chunk));

        channel.checkException();
    }

    @Test
    void shouldRejectTooLargeContent() {
        final ChannelHandler handler = new MessageChunksBuffer(10, 1000, 65535);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final MessageChunk chunk2 = new MessageChunk((byte) 42, 1, Unpooled.copiedBuffer("chunk2", UTF_8));
        final MessageChunk chunk3 = new LastMessageChunk((byte) 42, 2, Unpooled.copiedBuffer("chunk3", UTF_8));

        assertFalse(channel.writeInbound(chunk2));
        assertThrows(TooLongFrameException.class, () -> channel.writeInbound(chunk3));
        assertNull(channel.readInbound());

        assertEquals(0, chunk2.refCnt());
        assertEquals(0, chunk3.refCnt());

        channel.checkException();
    }

    @Test
    void shouldRejectInvalidChunks() {
        final ChannelHandler handler = new MessageChunksBuffer(1024, 1000, 65535);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final MessageChunk chunk1 = new MessageChunk((byte) 42, 0, Unpooled.copiedBuffer("chunk1", UTF_8));
        final MessageChunk chunk2 = new MessageChunk((byte) 42, 1, Unpooled.copiedBuffer("chunk2", UTF_8));
        final MessageChunk chunk3 = new LastMessageChunk((byte) 42, 1, Unpooled.copiedBuffer("chunk3", UTF_8));

        assertFalse(channel.writeInbound(chunk1));
        assertFalse(channel.writeInbound(chunk2));
        assertThrows(TooLongFrameException.class, () -> channel.writeInbound(chunk3));
        assertNull(channel.readInbound());

        assertEquals(0, chunk2.refCnt());
        assertEquals(0, chunk3.refCnt());

        channel.checkException();
    }

    @Test
    void shouldDiscardChunksOnTimeout() {
        final ChannelHandler handler = new MessageChunksBuffer(1024, 10, 65535);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final MessageChunk chunk1 = new MessageChunk((byte) 42, 0, Unpooled.copiedBuffer("chunk1", UTF_8));
        final MessageChunk chunk2 = new MessageChunk((byte) 42, 1, Unpooled.copiedBuffer("chunk2", UTF_8));

        assertFalse(channel.writeInbound(chunk1));
        assertFalse(channel.writeInbound(chunk2));
        await().untilAsserted(() -> assertEquals(-1, channel.runScheduledPendingTasks()));
        assertNull(channel.readInbound());

        assertEquals(0, chunk1.refCnt());
        assertEquals(0, chunk2.refCnt());

        channel.checkException();
    }
}
