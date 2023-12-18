/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.drasyl.handler.connection.Segment.ACK;
import static org.drasyl.handler.connection.Segment.SEG_HDR_SIZE;
import static org.drasyl.handler.connection.SegmentCodec.MAGIC_NUMBER;
import static org.drasyl.handler.connection.SegmentOption.END_OF_OPTION_LIST;
import static org.drasyl.handler.connection.SegmentOption.MAXIMUM_SEGMENT_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class SegmentCodecTest {
    private final int srcPort = 12321;
    private final int dstPort = 8080;
    private final long seq = 1_234_567_890;
    private final ByteBuf encodedMagicNumber = Unpooled.buffer(Integer.BYTES).writeInt(MAGIC_NUMBER);
    private final ByteBuf encodedSrcPort = Unpooled.buffer(Short.BYTES).writeShort((short) srcPort);
    private final ByteBuf encodedDstPort = Unpooled.buffer(Short.BYTES).writeShort((short) dstPort);
    private final ByteBuf encodedSeq = Unpooled.buffer(Integer.BYTES).writeInt((int) seq);
    private final long ack = 987_654_321;
    private final ByteBuf encodedAck = Unpooled.buffer(Integer.BYTES).writeInt((int) ack);
    private final int cks = 56_308;
    private final ByteBuf encodedCks = Unpooled.buffer(Short.BYTES).writeShort((short) cks);
    private final byte ctl = ACK;
    private final ByteBuf encodedCtl = Unpooled.buffer(1).writeByte(ctl);
    private final long wnd = 64_000;
    private final ByteBuf encodedWnd = Unpooled.buffer(Integer.BYTES).writeInt((int) wnd);
    private final Map<SegmentOption, Object> options = Map.of(
            MAXIMUM_SEGMENT_SIZE, 1300
    );
    private final ByteBuf encodedOptions = Unpooled.buffer()
            .writeByte(MAXIMUM_SEGMENT_SIZE.kind()).writeShort(1300)
            .writeByte(END_OF_OPTION_LIST.kind());
    private final ByteBuf content = Unpooled.copiedBuffer("Hello World", UTF_8);

    @Nested
    class Encode {
        @Test
        void shouldEncodeSegment() {
            final EmbeddedChannel channel = new EmbeddedChannel(new SegmentCodec());

            final ByteBuf data = content.retain();
            channel.writeOutbound(new Segment(srcPort, dstPort, seq, ack, ctl, wnd, cks, options, data));

            final ByteBuf actual = channel.readOutbound();
            final ByteBuf expected = Unpooled.wrappedBuffer(encodedMagicNumber, encodedSrcPort, encodedDstPort, encodedSeq, encodedAck, encodedCks, encodedCtl, encodedWnd, encodedOptions, content.resetReaderIndex());

            System.out.println(ByteBufUtil.hexDump(actual));
            System.out.println(ByteBufUtil.hexDump(expected));
            assertEquals(expected, actual);

            expected.release();
            actual.release();
        }

        @Test
        void shouldRejectAllOther(@Mock final ByteBuf msg) {
            final EmbeddedChannel channel = new EmbeddedChannel(new SegmentCodec());

            assertThrows(EncoderException.class, () -> channel.writeOutbound(msg));
        }
    }

    @Nested
    class Decode {
        @Test
        void shouldDecodeSegment() {
            final EmbeddedChannel channel = new EmbeddedChannel(new SegmentCodec());

            channel.writeInbound(Unpooled.wrappedBuffer(encodedMagicNumber, encodedSrcPort, encodedDstPort, encodedSeq, encodedAck, encodedCks, encodedCtl, encodedWnd, encodedOptions, content));

            final Segment actual = channel.readInbound();
            final ByteBuf data = content.retain();
            assertEquals(new Segment(srcPort, dstPort, seq, ack, ACK, options, data), actual);

            actual.release();
        }

        @Test
        void shouldPassThroughTooSmallByteBufs() {
            final EmbeddedChannel channel = new EmbeddedChannel(new SegmentCodec());

            final ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{ 0, 1, 2 });
            channel.writeInbound(msg);

            final ByteBuf actual = channel.readInbound();

            assertEquals(msg, actual);

            actual.release();
        }

        @Test
        void shouldPassThroughOnWrongMagicNumber() {
            final EmbeddedChannel channel = new EmbeddedChannel(new SegmentCodec());

            final ByteBuf msg = Unpooled.buffer(SEG_HDR_SIZE - 1).writerIndex(SEG_HDR_SIZE - 1);
            channel.writeInbound(msg);

            final ByteBuf actual = channel.readInbound();

            assertEquals(msg, actual);

            actual.release();
        }

        @Test
        void shouldDropSegWithInvalidChecksum() {
            final EmbeddedChannel channel = new EmbeddedChannel(new SegmentCodec());

            channel.writeInbound(Unpooled.wrappedBuffer(encodedMagicNumber, encodedSrcPort, encodedDstPort, Unpooled.buffer(Integer.BYTES).writeInt((int) seq - 1), encodedAck, encodedCks, encodedCtl, encodedWnd, encodedOptions, content));

            assertNull(channel.readInbound());
        }
    }
}
