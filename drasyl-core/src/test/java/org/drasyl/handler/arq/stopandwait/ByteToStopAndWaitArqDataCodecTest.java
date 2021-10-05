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
package org.drasyl.handler.arq.stopandwait;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ByteToStopAndWaitArqDataCodecTest {
    @Nested
    class Encode {
        @Test
        void shouldEncodeToData() {
            final ChannelHandler handler = new ByteToStopAndWaitArqDataCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final ByteBuf msg = Unpooled.buffer();
            channel.writeOutbound(msg);

            final StopAndWaitArqData actual = channel.readOutbound();
            assertThat(actual, instanceOf(StopAndWaitArqData.class));

            actual.release();
        }
    }

    @Nested
    class Decode {
        @Test
        void shouldDecodeFromData() {
            final ChannelHandler handler = new ByteToStopAndWaitArqDataCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final StopAndWaitArqData msg = new StopAndWaitArqData(true, Unpooled.buffer());
            channel.writeInbound(msg);

            assertEquals(msg.content(), channel.readInbound());

            msg.release();
        }
    }
}
