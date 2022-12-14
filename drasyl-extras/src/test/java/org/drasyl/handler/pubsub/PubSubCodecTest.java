/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.pubsub;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class PubSubCodecTest {
    private final ByteBuf encodedPublish = Unpooled.buffer(Integer.BYTES).writeInt(PubSubCodec.MAGIC_NUMBER_PUBLISH);
    private final ByteBuf encodedPublished = Unpooled.buffer(Integer.BYTES).writeInt(PubSubCodec.MAGIC_NUMBER_PUBLISHED);
    private final ByteBuf encodedSubscribe = Unpooled.buffer(Integer.BYTES).writeInt(PubSubCodec.MAGIC_NUMBER_SUBSCRIBE);
    private final ByteBuf encodedSubscribed = Unpooled.buffer(Integer.BYTES).writeInt(PubSubCodec.MAGIC_NUMBER_SUBSCRIBED);
    private final ByteBuf encodedUnsubscribe = Unpooled.buffer(Integer.BYTES).writeInt(PubSubCodec.MAGIC_NUMBER_UNSUBSCRIBE);
    private final ByteBuf encodedUnsubscribed = Unpooled.buffer(Integer.BYTES).writeInt(PubSubCodec.MAGIC_NUMBER_UNSUBSCRIBED);
    private final UUID id = new UUID(-5_473_769_416_544_107_185L, 6_439_925_875_238_784_627L);
    private final ByteBuf encodedId = Unpooled.buffer(Long.BYTES + Long.BYTES).writeLong(id.getMostSignificantBits()).writeLong(id.getLeastSignificantBits());
    private final String topic = "myTopic";
    private final ByteBuf encodedTopic = Unpooled.copiedBuffer(topic, UTF_8);
    private final ByteBuf encodedTopicWithLength = Unpooled.buffer().writeInt(topic.length()).writeBytes(topic.getBytes(UTF_8));
    private final ByteBuf content = Unpooled.copiedBuffer("Hello World", UTF_8);

    @Nested
    class Encode {
        @Test
        void shouldEncodePublish(@Mock final DrasylAddress recipient) {
            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubCodec());

            channel.writeOutbound(new OverlayAddressedMessage<>(PubSubPublish.of(id, topic, content.retain().resetReaderIndex()), recipient));

            final OverlayAddressedMessage<ByteBuf> actual = channel.readOutbound();
            assertNull(actual.sender());
            assertEquals(recipient, actual.recipient());
            final ByteBuf expected = Unpooled.wrappedBuffer(encodedPublish, encodedId, encodedTopicWithLength, content.resetReaderIndex());
            assertEquals(expected, actual.content());

            expected.release();
            actual.release();
        }

        @Test
        void shouldEncodePublished(@Mock final DrasylAddress recipient) {
            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubCodec());

            channel.writeOutbound(new OverlayAddressedMessage<>(PubSubPublished.of(id), recipient));

            final OverlayAddressedMessage<ByteBuf> actual = channel.readOutbound();
            assertNull(actual.sender());
            assertEquals(recipient, actual.recipient());
            final ByteBuf expected = Unpooled.wrappedBuffer(encodedPublished, encodedId);
            assertEquals(expected, actual.content());

            expected.release();
            actual.release();
        }

        @Test
        void shouldEncodeSubscribe(@Mock final DrasylAddress recipient) {
            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubCodec());

            channel.writeOutbound(new OverlayAddressedMessage<>(PubSubSubscribe.of(id, topic), recipient));

            final OverlayAddressedMessage<ByteBuf> actual = channel.readOutbound();
            assertNull(actual.sender());
            assertEquals(recipient, actual.recipient());
            final ByteBuf expected = Unpooled.wrappedBuffer(encodedSubscribe, encodedId, encodedTopic);
            assertEquals(expected, actual.content());

            expected.release();
            actual.release();
        }

        @Test
        void shouldEncodeSubscribed(@Mock final DrasylAddress recipient) {
            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubCodec());

            channel.writeOutbound(new OverlayAddressedMessage<>(PubSubSubscribed.of(id), recipient));

            final OverlayAddressedMessage<ByteBuf> actual = channel.readOutbound();
            assertNull(actual.sender());
            assertEquals(recipient, actual.recipient());
            final ByteBuf expected = Unpooled.wrappedBuffer(encodedSubscribed, encodedId);
            assertEquals(expected, actual.content());

            expected.release();
            actual.release();
        }

        @Test
        void shouldEncodeUnsubscribe(@Mock final DrasylAddress recipient) {
            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubCodec());

            channel.writeOutbound(new OverlayAddressedMessage<>(PubSubUnsubscribe.of(id, topic), recipient));

            final OverlayAddressedMessage<ByteBuf> actual = channel.readOutbound();
            assertNull(actual.sender());
            assertEquals(recipient, actual.recipient());
            final ByteBuf expected = Unpooled.wrappedBuffer(encodedUnsubscribe, encodedId, encodedTopic);
            assertEquals(expected, actual.content());

            expected.release();
            actual.release();
        }

        @Test
        void shouldEncodeUnsubscribed(@Mock final DrasylAddress recipient) {
            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubCodec());

            channel.writeOutbound(new OverlayAddressedMessage<>(PubSubUnsubscribed.of(id), recipient));

            final OverlayAddressedMessage<ByteBuf> actual = channel.readOutbound();
            assertNull(actual.sender());
            assertEquals(recipient, actual.recipient());
            final ByteBuf expected = Unpooled.wrappedBuffer(encodedUnsubscribed, encodedId);
            assertEquals(expected, actual.content());

            expected.release();
            actual.release();
        }

        @Test
        void shouldRejectAllOther(@Mock final OverlayAddressedMessage<?> msg) {
            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubCodec());

            assertThrows(EncoderException.class, () -> channel.writeOutbound(msg));
        }
    }

    @Nested
    class Decode {
        @Test
        void shouldDecodePublish(@Mock final DrasylAddress sender) {
            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubCodec());

            channel.writeInbound(new OverlayAddressedMessage<>(Unpooled.wrappedBuffer(encodedPublish, encodedId, encodedTopicWithLength, content), null, sender));

            final OverlayAddressedMessage<?> actual = channel.readInbound();
            assertThat(actual.content(), instanceOf(PubSubPublish.class));

            actual.release();
        }

        @Test
        void shouldDecodePublished(@Mock final DrasylAddress sender) {
            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubCodec());

            channel.writeInbound(new OverlayAddressedMessage<>(Unpooled.wrappedBuffer(encodedPublished, encodedId), null, sender));

            final OverlayAddressedMessage<?> actual = channel.readInbound();
            assertThat(actual.content(), instanceOf(PubSubPublished.class));

            actual.release();
        }

        @Test
        void shouldDecodeSubscribe(@Mock final DrasylAddress sender) {
            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubCodec());

            channel.writeInbound(new OverlayAddressedMessage<>(Unpooled.wrappedBuffer(encodedSubscribe, encodedId, encodedTopicWithLength), null, sender));

            final OverlayAddressedMessage<?> actual = channel.readInbound();
            assertThat(actual.content(), instanceOf(PubSubSubscribe.class));

            actual.release();
        }

        @Test
        void shouldDecodeSubscribed(@Mock final DrasylAddress sender) {
            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubCodec());

            channel.writeInbound(new OverlayAddressedMessage<>(Unpooled.wrappedBuffer(encodedSubscribed, encodedId), null, sender));

            final OverlayAddressedMessage<?> actual = channel.readInbound();
            assertThat(actual.content(), instanceOf(PubSubSubscribed.class));

            actual.release();
        }

        @Test
        void shouldDecodeUnsubscribe(@Mock final DrasylAddress sender) {
            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubCodec());

            channel.writeInbound(new OverlayAddressedMessage<>(Unpooled.wrappedBuffer(encodedUnsubscribe, encodedId, encodedTopicWithLength), null, sender));

            final OverlayAddressedMessage<?> actual = channel.readInbound();
            assertThat(actual.content(), instanceOf(PubSubUnsubscribe.class));
        }

        @Test
        void shouldDecodeUnsubscribed(@Mock final DrasylAddress sender) {
            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubCodec());

            channel.writeInbound(new OverlayAddressedMessage<>(Unpooled.wrappedBuffer(encodedUnsubscribed, encodedId), null, sender));

            final OverlayAddressedMessage<?> actual = channel.readInbound();
            assertThat(actual.content(), instanceOf(PubSubUnsubscribed.class));

            actual.release();
        }

        @Test
        void shouldPassThroughTooSmallByteBufs() {
            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubCodec());

            final ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{ 0, 1, 2 });
            channel.writeInbound(msg);

            final ByteBuf actual = channel.readInbound();

            assertEquals(actual, msg);

            actual.release();
        }

        @Test
        void shouldPassThroughOnWrongMagicNumber() {
            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubCodec());

            final ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{ 1, 2, 3, 4, 5 });
            channel.writeInbound(msg);

            final ByteBuf actual = channel.readInbound();

            assertEquals(actual, msg);

            actual.release();
        }
    }
}
