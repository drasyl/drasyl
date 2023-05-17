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
import io.netty.handler.codec.EncoderException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class StopAndWaitArqCodecTest {
    @Nested
    class Encode {
        @Test
        void shouldEncodeData() {
            final ChannelHandler handler = new StopAndWaitArqCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final StopAndWaitArqData msg = new StopAndWaitArqData(true, Unpooled.copiedBuffer("Hallo", UTF_8));
            channel.writeOutbound(msg);

            final ByteBuf expected = Unpooled.wrappedBuffer(new byte[]{
                    31, 50, 0, -44, // magic number
                    1, // sequence no
                    72, 97, 108, 108, 111 // payload
            });
            final ByteBuf actual = channel.readOutbound();
            assertEquals(expected, actual);

            expected.release();
            actual.release();
        }

        @Test
        void shouldEncodeAck() {
            final ChannelHandler handler = new StopAndWaitArqCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final StopAndWaitArqAck msg = StopAndWaitArqAck.STOP_AND_WAIT_ACK_0;
            channel.writeOutbound(msg);

            final ByteBuf expected = Unpooled.wrappedBuffer(new byte[]{
                    31, 50, 0, -43, // magic number
                    0, // sequence no
            });
            final ByteBuf actual = channel.readOutbound();
            assertEquals(expected, actual);

            expected.release();
            actual.release();
        }

        @Test
        void shouldRejectAllOther(@Mock final StopAndWaitArqMessage msg) {
            final ChannelHandler handler = new StopAndWaitArqCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            assertThrows(EncoderException.class, () -> channel.writeOutbound(msg));
        }
    }

    @Nested
    class Decode {
        @Test
        void shouldDecodeData() {
            final ChannelHandler handler = new StopAndWaitArqCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
                    31, 50, 0, -44, // magic number
                    1, // sequence no
                    72, 97, 108, 108, 111 // payload
            });
            channel.writeInbound(msg);

            final StopAndWaitArqData actual = channel.readInbound();
            assertThat(actual, instanceOf(StopAndWaitArqData.class));

            actual.release();
        }

        @Test
        void shouldDecodeAck() {
            final ChannelHandler handler = new StopAndWaitArqCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
                    31, 50, 0, -43, // magic number
                    0, // sequence no
            });
            channel.writeInbound(msg);

            final StopAndWaitArqAck actual = channel.readInbound();
            assertThat(actual, instanceOf(StopAndWaitArqAck.class));
        }

        @Test
        void shouldPassThroughTooSmallByteBufs() {
            final ChannelHandler handler = new StopAndWaitArqCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{ 0, 1, 2 });
            channel.writeInbound(msg);

            final ByteBuf actual = channel.readInbound();

            assertEquals(actual, msg);
            actual.release();
        }

        @Test
        void shouldPassThroughOnWrongMagicNumber() {
            final ChannelHandler handler = new StopAndWaitArqCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{ 1, 2, 3, 4, 5 });
            channel.writeInbound(msg);

            final ByteBuf actual = channel.readInbound();

            assertEquals(actual, msg);
            actual.release();
        }
    }
}
