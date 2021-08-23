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
package org.drasyl.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class ApplicationMessageCodecTest {
    private final int networkId = 0;
    @Mock
    private IdentityPublicKey myPublicKey;
    @Mock
    private ProofOfWork myProofOfWork;

    @Nested
    class Encode {
        @Test
        void shouldConvertByteBufWithIdentityPublicKeyToApplicationMessage(@Mock final IdentityPublicKey address) {
            final ChannelHandler handler = new ApplicationMessageCodec(networkId, myPublicKey, myProofOfWork);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final ByteBuf byteBuf = Unpooled.copiedBuffer("Hello World", UTF_8);
            channel.writeAndFlush(new AddressedMessage<>(byteBuf, address));

            assertThat(((AddressedMessage<?, ?>) channel.readOutbound()).message(), instanceOf(ApplicationMessage.class));
            byteBuf.release();
        }

        @Test
        void shouldPassTroughNonMatchingMessages(@Mock final IdentityPublicKey address) {
            final ChannelHandler handler = new ApplicationMessageCodec(networkId, myPublicKey, myProofOfWork);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            channel.writeAndFlush(new AddressedMessage<>("Hello World", address));

            assertEquals(new AddressedMessage<>("Hello World", address), channel.readOutbound());
        }
    }

    @Nested
    class Decode {
        @Test
        void shouldConvertApplicationMessageToByteStringWithIdentityPublicKey(@Mock final IdentityPublicKey sender) {
            final ChannelHandler handler = new ApplicationMessageCodec(networkId, myPublicKey, myProofOfWork);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final ByteBuf byteBuf = Unpooled.buffer();
            channel.pipeline().fireChannelRead(new AddressedMessage<>(ApplicationMessage.of(networkId, myPublicKey, myProofOfWork, sender, byteBuf), sender));

            assertThat(((AddressedMessage<?, ?>) channel.readInbound()).message(), instanceOf(ByteBuf.class));
            byteBuf.release();
        }

        @Test
        void shouldPassTroughNonMatchingMessages(@Mock final IdentityPublicKey address) {
            final ChannelHandler handler = new ApplicationMessageCodec(networkId, myPublicKey, myProofOfWork);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            channel.pipeline().fireChannelRead(new AddressedMessage<>("Hello World", address));

            assertEquals(new AddressedMessage<>("Hello World", address), channel.readInbound());
        }
    }
}
