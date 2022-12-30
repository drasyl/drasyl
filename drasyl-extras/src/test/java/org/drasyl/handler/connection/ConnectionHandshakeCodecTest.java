package org.drasyl.handler.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import org.drasyl.handler.connection.ConnectionHandshakeSegment.Option;
import org.drasyl.identity.DrasylAddress;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.drasyl.handler.connection.ConnectionHandshakeCodec.MIN_MESSAGE_LENGTH;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.ACK;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.Option.END_OF_OPTION_LIST;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.Option.MAXIMUM_SEGMENT_SIZE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class ConnectionHandshakeCodecTest {
    private final ByteBuf encodedSegment = Unpooled.buffer(Integer.BYTES).writeInt(ConnectionHandshakeCodec.MAGIC_NUMBER);
    private final long seq = 1_234_567_890;
    private final ByteBuf encodedSeq = Unpooled.buffer(Integer.BYTES).writeInt((int) seq);
    private final long ack = 987_654_321;
    private final ByteBuf encodedAck = Unpooled.buffer(Integer.BYTES).writeInt((int) ack);
    private final byte ctl = ACK;
    private final ByteBuf encodedCtl = Unpooled.buffer(1).writeByte(ctl);
    private final Map<Option, Object> options = Map.of(
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
            final EmbeddedChannel channel = new EmbeddedChannel(new ConnectionHandshakeCodec());

            channel.writeOutbound(ConnectionHandshakeSegment.ack(seq, ack, options, content.retain()));

            final ByteBuf actual = channel.readOutbound();
            final ByteBuf expected = Unpooled.wrappedBuffer(encodedSegment, encodedSeq, encodedAck, encodedCtl, encodedOptions, content.resetReaderIndex());
            assertEquals(expected, actual);

            expected.release();
            actual.release();
        }

        @Test
        void shouldRejectAllOther(@Mock final ByteBuf msg) {
            final EmbeddedChannel channel = new EmbeddedChannel(new ConnectionHandshakeCodec());

            assertThrows(EncoderException.class, () -> channel.writeOutbound(msg));
        }
    }

    @Nested
    class Decode {
        @Test
        void shouldDecodeSegment(@Mock final DrasylAddress sender) {
            final EmbeddedChannel channel = new EmbeddedChannel(new ConnectionHandshakeCodec());

            channel.writeInbound(Unpooled.wrappedBuffer(encodedSegment, encodedSeq, encodedAck, encodedCtl, encodedOptions, content));

            final ConnectionHandshakeSegment actual = channel.readInbound();
            assertEquals(ConnectionHandshakeSegment.ack(seq, ack, options, content.retain()), actual);

            actual.release();
        }

        @Test
        void shouldPassThroughTooSmallByteBufs() {
            final EmbeddedChannel channel = new EmbeddedChannel(new ConnectionHandshakeCodec());

            final ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{ 0, 1, 2 });
            channel.writeInbound(msg);

            final ByteBuf actual = channel.readInbound();

            assertEquals(actual, msg);

            actual.release();
        }

        @Test
        void shouldPassThroughOnWrongMagicNumber() {
            final EmbeddedChannel channel = new EmbeddedChannel(new ConnectionHandshakeCodec());

            final ByteBuf msg = Unpooled.buffer(MIN_MESSAGE_LENGTH).writerIndex(MIN_MESSAGE_LENGTH);
            channel.writeInbound(msg);

            final ByteBuf actual = channel.readInbound();

            assertEquals(actual, msg);

            actual.release();
        }
    }
}
