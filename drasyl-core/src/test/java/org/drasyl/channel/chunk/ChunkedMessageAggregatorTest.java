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
package org.drasyl.channel.chunk;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ChunkedMessageAggregatorTest {
    @Test
    void shouldAggregate() {
        final ChannelHandler handler = new ChunkedMessageAggregator(1024);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final MessageChunk chunk1 = new MessageChunk((byte) 42, (byte) 0, Unpooled.copiedBuffer("chunk1", UTF_8));
        final MessageChunk chunk2 = new MessageChunk((byte) 42, (byte) 1, Unpooled.copiedBuffer("chunk2", UTF_8));
        final MessageChunk chunk3 = new LastMessageChunk((byte) 42, (byte) 2, Unpooled.copiedBuffer("chunk3", UTF_8));

        assertFalse(channel.writeInbound(chunk1));
        assertFalse(channel.writeInbound(chunk2));
        assertTrue(channel.writeInbound(chunk3));
        assertTrue(channel.finish());

        final ReassembledMessage reassembledMessage = channel.readInbound();
        assertNotNull(reassembledMessage);

        assertEquals(Unpooled.copiedBuffer("chunk1chunk2chunk3", UTF_8), reassembledMessage.content());
        reassembledMessage.release();
    }

    @Test
    void shouldRejectTooLargeContent() {
        final ChannelHandler handler = new ChunkedMessageAggregator(10);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final MessageChunk chunk1 = new MessageChunk((byte) 42, (byte) 0, Unpooled.copiedBuffer("chunk1", UTF_8));
        final MessageChunk chunk2 = new MessageChunk((byte) 42, (byte) 1, Unpooled.copiedBuffer("chunk2", UTF_8));

        assertFalse(channel.writeInbound(chunk1));
        assertThrows(TooLongFrameException.class, () -> channel.writeInbound(chunk2));
        assertNull(channel.readInbound());
    }
}
