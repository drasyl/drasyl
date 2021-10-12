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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class MessageChunkDecoderTest {
    @Test
    void shouldDecodeToContentChunk() {
        final ChannelHandler handler = new MessageChunkDecoder();
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final ByteBuf msg = Unpooled.buffer()
                .writeInt(MessageChunkEncoder.MAGIC_NUMBER_CONTENT) // magic number
                .writeByte(42) // id
                .writeByte(13) // chunk no
                .writeBytes(new byte[]{1, 2, 3}); // payload
        channel.writeInbound(msg);

        final MessageChunk actual = channel.readInbound();

        assertEquals(42, actual.msgId());
        assertEquals(13, actual.chunkNo());
        actual.release();
    }

    @Test
    void shouldDecodeToLastChunk() {
        final ChannelHandler handler = new MessageChunkDecoder();
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final ByteBuf msg = Unpooled.buffer()
                .writeInt(MessageChunkEncoder.MAGIC_NUMBER_LAST) // magic number
                .writeByte(23) // id
                .writeByte(2) // total chunks
                .writeBytes(new byte[]{1, 2, 3}); // payload
        channel.writeInbound(msg);

        final LastMessageChunk actual = channel.readInbound();

        assertEquals(23, actual.msgId());
        assertEquals(2, actual.chunkNo());
        actual.release();
    }

    @Test
    void shouldPassThroughTooSmallByteBufs() {
        final ChannelHandler handler = new MessageChunkDecoder();
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 });
        channel.writeInbound(msg);

        final ByteBuf actual = channel.readInbound();

        assertEquals(actual, msg);
        actual.release();
    }

    @Test
    void shouldPassThroughOnWrongMagicNumber() {
        final ChannelHandler handler = new MessageChunkDecoder();
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{ 1, 2, 3 });
        channel.writeInbound(msg);

        final ByteBuf actual = channel.readInbound();

        assertEquals(actual, msg);
        actual.release();
    }
}
