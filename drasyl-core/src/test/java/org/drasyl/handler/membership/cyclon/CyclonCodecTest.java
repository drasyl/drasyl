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
package org.drasyl.handler.membership.cyclon;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static test.util.IdentityTestUtil.ID_1;

@ExtendWith(MockitoExtension.class)
class CyclonCodecTest {
    @Nested
    class Encode {
        @SuppressWarnings("unchecked")
        @Test
        void shouldEncodeRequest(@Mock final DrasylAddress recipient) {
            final ChannelHandler handler = new CyclonCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final OverlayAddressedMessage<CyclonShuffleRequest> msg = new OverlayAddressedMessage<>(CyclonShuffleRequest.of(CyclonNeighbor.of(ID_1.getAddress())), recipient);
            channel.writeOutbound(msg);

            final ByteBuf expected = Unpooled.wrappedBuffer(new byte[]{
                    -6,
                    -29,
                    79,
                    -87,
                    // magic number
                    24,
                    -51,
                    -78,
                    -126,
                    -66,
                    -115,
                    18,
                    -109,
                    -11,
                    4,
                    12,
                    -42,
                    32,
                    -87,
                    26,
                    -54,
                    -122,
                    -92,
                    117,
                    104,
                    46,
                    77,
                    -36,
                    57,
                    125,
                    -22,
                    -66,
                    48,
                    10,
                    -83,
                    -111,
                    39,
                    0,
                    0
                    // neighbor #1
            });
            final ByteBuf actual = ((OverlayAddressedMessage<ByteBuf>) channel.readOutbound()).content();
            assertEquals(expected, actual);

            expected.release();
            actual.release();
        }

        @SuppressWarnings("unchecked")
        @Test
        void shouldEncodeResponse(@Mock final DrasylAddress recipient) {
            final ChannelHandler handler = new CyclonCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final OverlayAddressedMessage<CyclonShuffleResponse> msg = new OverlayAddressedMessage<>(CyclonShuffleResponse.of(CyclonNeighbor.of(ID_1.getAddress())), recipient);
            channel.writeOutbound(msg);

            final ByteBuf expected = Unpooled.wrappedBuffer(new byte[]{
                    -6,
                    -29,
                    79,
                    -86,
                    // magic number
                    24,
                    -51,
                    -78,
                    -126,
                    -66,
                    -115,
                    18,
                    -109,
                    -11,
                    4,
                    12,
                    -42,
                    32,
                    -87,
                    26,
                    -54,
                    -122,
                    -92,
                    117,
                    104,
                    46,
                    77,
                    -36,
                    57,
                    125,
                    -22,
                    -66,
                    48,
                    10,
                    -83,
                    -111,
                    39,
                    0,
                    0
                    // neighbor #1
            });
            final ByteBuf actual = ((OverlayAddressedMessage<ByteBuf>) channel.readOutbound()).content();
            assertEquals(expected, actual);

            expected.release();
            actual.release();
        }

        @Test
        void shouldRejectAllOther(@Mock final OverlayAddressedMessage<?> msg) {
            final ChannelHandler handler = new CyclonCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            assertThrows(EncoderException.class, () -> channel.writeOutbound(msg));
        }
    }

    @Nested
    class Decode {
        @SuppressWarnings("unchecked")
        @Test
        void shouldDecodeRequest(@Mock final DrasylAddress sender) {
            final ChannelHandler handler = new CyclonCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final OverlayAddressedMessage<ByteBuf> msg = new OverlayAddressedMessage<>(Unpooled.wrappedBuffer(new byte[]{
                    -6,
                    -29,
                    79,
                    -87,
                    // magic number
                    24,
                    -51,
                    -78,
                    -126,
                    -66,
                    -115,
                    18,
                    -109,
                    -11,
                    4,
                    12,
                    -42,
                    32,
                    -87,
                    26,
                    -54,
                    -122,
                    -92,
                    117,
                    104,
                    46,
                    77,
                    -36,
                    57,
                    125,
                    -22,
                    -66,
                    48,
                    10,
                    -83,
                    -111,
                    39,
                    0,
                    0
                    // neighbor #1
            }), null, sender);
            channel.writeInbound(msg);

            final CyclonShuffleRequest actual = ((OverlayAddressedMessage<CyclonShuffleRequest>) channel.readInbound()).content();
            assertThat(actual, instanceOf(CyclonShuffleRequest.class));
        }

        @SuppressWarnings("unchecked")
        @Test
        void shouldDecodeResponse(@Mock final DrasylAddress sender) {
            final ChannelHandler handler = new CyclonCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final OverlayAddressedMessage<ByteBuf> msg = new OverlayAddressedMessage<>(Unpooled.wrappedBuffer(new byte[]{
                    -6,
                    -29,
                    79,
                    -86,
                    // magic number
                    24,
                    -51,
                    -78,
                    -126,
                    -66,
                    -115,
                    18,
                    -109,
                    -11,
                    4,
                    12,
                    -42,
                    32,
                    -87,
                    26,
                    -54,
                    -122,
                    -92,
                    117,
                    104,
                    46,
                    77,
                    -36,
                    57,
                    125,
                    -22,
                    -66,
                    48,
                    10,
                    -83,
                    -111,
                    39,
                    0,
                    0
                    // neighbor #1
            }), null, sender);
            channel.writeInbound(msg);

            final CyclonShuffleResponse actual = ((OverlayAddressedMessage<CyclonShuffleResponse>) channel.readInbound()).content();
            assertThat(actual, instanceOf(CyclonShuffleResponse.class));
        }

        @Test
        void shouldPassThroughTooSmallByteBufs() {
            final ChannelHandler handler = new CyclonCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{ 0, 1, 2 });
            channel.writeInbound(msg);

            final ByteBuf actual = channel.readInbound();

            assertEquals(actual, msg);
            actual.release();
        }

        @Test
        void shouldPassThroughOnWrongMagicNumber() {
            final ChannelHandler handler = new CyclonCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{ 1, 2, 3, 4, 5 });
            channel.writeInbound(msg);

            final ByteBuf actual = channel.readInbound();

            assertEquals(actual, msg);
            actual.release();
        }
    }
}
