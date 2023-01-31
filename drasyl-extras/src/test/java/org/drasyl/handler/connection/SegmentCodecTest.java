package org.drasyl.handler.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import org.drasyl.identity.DrasylAddress;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.drasyl.handler.connection.Segment.ACK;
import static org.drasyl.handler.connection.Segment.SEG_HDR_SIZE;
import static org.drasyl.handler.connection.SegmentOption.END_OF_OPTION_LIST;
import static org.drasyl.handler.connection.SegmentOption.MAXIMUM_SEGMENT_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class SegmentCodecTest {
    private final long seq = 1_234_567_890;
    private final ByteBuf encodedSeq = Unpooled.buffer(Integer.BYTES).writeInt((int) seq);
    private final long ack = 987_654_321;
    private final ByteBuf encodedAck = Unpooled.buffer(Integer.BYTES).writeInt((int) ack);
    private final byte ctl = ACK;
    private final ByteBuf encodedCtl = Unpooled.buffer(1).writeByte(ctl);
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

            channel.writeOutbound(Segment.ack(seq, ack, options, content.retain()));

            final ByteBuf actual = channel.readOutbound();
            final ByteBuf expected = Unpooled.wrappedBuffer(encodedSeq, encodedAck, encodedCtl, encodedOptions, content.resetReaderIndex());
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
        void shouldDecodeSegment(@Mock final DrasylAddress sender) {
            final EmbeddedChannel channel = new EmbeddedChannel(new SegmentCodec());

            channel.writeInbound(Unpooled.wrappedBuffer(encodedSeq, encodedAck, encodedCtl, encodedOptions, content));

            final Segment actual = channel.readInbound();
            assertEquals(Segment.ack(seq, ack, options, content.retain()), actual);

            actual.release();
        }

        @Test
        void shouldPassThroughTooSmallByteBufs() {
            final EmbeddedChannel channel = new EmbeddedChannel(new SegmentCodec());

            final ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{ 0, 1, 2 });
            channel.writeInbound(msg);

            final ByteBuf actual = channel.readInbound();

            assertEquals(actual, msg);

            actual.release();
        }

        @Test
        void shouldPassThroughOnWrongMagicNumber() {
            final EmbeddedChannel channel = new EmbeddedChannel(new SegmentCodec());

            final ByteBuf msg = Unpooled.buffer(SEG_HDR_SIZE).writerIndex(SEG_HDR_SIZE);
            channel.writeInbound(msg);

            final ByteBuf actual = channel.readInbound();

            assertEquals(actual, msg);

            actual.release();
        }
    }
}
