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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class LargeByteBufToChunkedMessageEncoderTest {
    @Test
    void shouldPassThroughSmallByteBufs() {
        final ChannelHandler handler = new LargeByteBufToChunkedMessageEncoder(10, 100);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final ByteBuf msg = Unpooled.wrappedBuffer(new byte[5]);
        channel.writeOutbound(msg);

        assertEquals(msg, channel.readOutbound());
        msg.release();
    }

    @Test
    void shouldEncodeByteBufsExceedingMaxChunkLength() throws Exception {
        final ChannelHandler handler = new LargeByteBufToChunkedMessageEncoder(10, 100);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final ByteBuf msg = Unpooled.wrappedBuffer(new byte[15]);
        channel.writeOutbound(msg);

        final Object actual = channel.readOutbound();
        assertThat(actual, instanceOf(ChunkedMessageInput.class));

        ((ChunkedMessageInput) actual).close();
        assertEquals(0, msg.refCnt());
    }

    @Test
    void shouldRejectByteBufsExceedingMaxLength() {
        final ChannelHandler handler = new LargeByteBufToChunkedMessageEncoder(10, 100);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final ByteBuf msg = Unpooled.wrappedBuffer(new byte[150]);

        assertThrows(EncoderException.class, () -> channel.writeOutbound(msg));
    }
}
