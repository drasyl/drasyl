/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.remote;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.PartialReadMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class ByteToRemoteMessageCodecTest {
    private IdentityPublicKey senderPublicKey;
    private ProofOfWork proofOfWork;
    private IdentityPublicKey recipientPublicKey;

    @BeforeEach
    void setUp() {
        senderPublicKey = IdentityPublicKey.of("18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127");
        recipientPublicKey = IdentityPublicKey.of("02bfa672181ef9c0a359dc68cc3a4d34f47752c8886a0c5661dc253ff5949f1b");
        proofOfWork = ProofOfWork.of(1);
    }

    @Nested
    class Decode {
        @Test
        void shouldConvertByteBufToEnvelope(@Mock final InetSocketAddress sender) {
            final RemoteMessage message = AcknowledgementMessage.of(1337, recipientPublicKey, senderPublicKey, proofOfWork, System.currentTimeMillis());
            final ChannelInboundHandler handler = new ByteToRemoteMessageCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final ByteBufAllocator alloc = PooledByteBufAllocator.DEFAULT;
                final ByteBuf byteBuf = message.encodeMessage(alloc);
                channel.pipeline().fireChannelRead(new InetAddressedMessage<>(byteBuf, null, sender));

                final InetAddressedMessage<Object> actual = channel.readInbound();
                assertThat(actual.content(), instanceOf(PartialReadMessage.class));

                actual.release();
            }
            finally {
                channel.checkException();
                channel.close();
            }
        }
    }

    @Nested
    class Encode {
        @Test
        void shouldConvertEnvelopeToByteBuf(@Mock final InetSocketAddress recipient) {
            final ApplicationMessage message = ApplicationMessage.of(1337, IdentityPublicKey.of("02bfa672181ef9c0a359dc68cc3a4d34f47752c8886a0c5661dc253ff5949f1b"), IdentityPublicKey.of("18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127"), ProofOfWork.of(3556154), Unpooled.copiedBuffer("Hello World", UTF_8));
            final ByteBufAllocator alloc = UnpooledByteBufAllocator.DEFAULT;
            final ByteBuf byteBuf = message.encodeMessage(alloc);

            final ChannelInboundHandler handler = new ByteToRemoteMessageCodec();
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.writeAndFlush(new InetAddressedMessage<>(message, recipient));

                final ReferenceCounted actual = channel.readOutbound();
                assertEquals(new InetAddressedMessage<>(byteBuf, recipient), actual);

                actual.release();
            }
            finally {
                byteBuf.release();
                channel.checkException();
                channel.close();
            }
        }
    }
}
