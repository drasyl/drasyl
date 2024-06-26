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
package org.drasyl.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class MaxLengthFrameDecoderTest {
    @Test
    void shouldCreateBufsWithGivenMaxFrameLength() {
        final ChannelHandler handler = new MaxLengthFrameDecoder(2);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        ByteBuf byteBuf = null;
        byteBuf = Unpooled.copiedBuffer("A", UTF_8);
        channel.writeInbound(byteBuf);
        assertEquals(Unpooled.copiedBuffer("A", UTF_8), channel.readInbound());

        byteBuf = Unpooled.copiedBuffer("BC", UTF_8);
        channel.writeInbound(byteBuf);
        assertEquals(Unpooled.copiedBuffer("BC", UTF_8), channel.readInbound());

        byteBuf = Unpooled.copiedBuffer("DEF", UTF_8);
        channel.writeInbound(byteBuf);
        assertEquals(Unpooled.copiedBuffer("DE", UTF_8), channel.readInbound());
        assertEquals(Unpooled.copiedBuffer("F", UTF_8), channel.readInbound());

        byteBuf = Unpooled.copiedBuffer("GH", UTF_8);
        channel.writeInbound(byteBuf);
        assertEquals(Unpooled.copiedBuffer("GH", UTF_8), channel.readInbound());

        assertNull(channel.readInbound());
        channel.checkException();
    }
}
