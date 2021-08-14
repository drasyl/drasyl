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
package org.drasyl.remote.handler;

import com.google.protobuf.ByteString;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.AcknowledgementMessage;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.remote.protocol.InvalidMessageFormatException;
import org.drasyl.remote.protocol.Nonce;
import org.drasyl.remote.protocol.PartialReadMessage;
import org.drasyl.remote.protocol.RemoteMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.SocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class RemoteMessageToByteBufCodecTest {
    private final Nonce correspondingId = Nonce.of("ea0f284eef1567c505b126671f4293924b81b4b9d20a2be7");
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
        void shouldConvertByteBufToEnvelope(@Mock final SocketAddress sender) throws IOException {
            final RemoteMessage message = AcknowledgementMessage.of(1337, senderPublicKey, proofOfWork, recipientPublicKey, correspondingId);
            final ChannelInboundHandler handler = RemoteMessageToByteBufCodec.INSTANCE;
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
                message.writeTo(byteBuf);
                channel.pipeline().fireChannelRead(new AddressedMessage<>(byteBuf, sender));

                final AddressedMessage<Object, SocketAddress> actual = channel.readInbound();
                assertThat(actual.message(), instanceOf(PartialReadMessage.class));

                actual.release();
            }
            finally {
                channel.close();
            }
        }
    }

    @Nested
    class Encode {
        @Test
        void shouldConvertEnvelopeToByteBuf(@Mock final SocketAddress recipient) throws IOException {
            final ApplicationMessage message = ApplicationMessage.of(1337, IdentityPublicKey.of("18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127"), ProofOfWork.of(3556154), IdentityPublicKey.of("02bfa672181ef9c0a359dc68cc3a4d34f47752c8886a0c5661dc253ff5949f1b"), byte[].class.getName(), ByteString.copyFromUtf8("Hello World"));
            final ChannelInboundHandler handler = RemoteMessageToByteBufCodec.INSTANCE;
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.writeAndFlush(new AddressedMessage<>(message, recipient));

                final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
                message.writeTo(byteBuf);

                final ReferenceCounted actual = channel.readOutbound();
                assertEquals(new AddressedMessage<>(byteBuf, recipient), actual);

                byteBuf.release();
                actual.release();
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldCompleteFutureExceptionallyWhenConversionFail(@Mock final SocketAddress recipient,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage messageEnvelope) throws IOException {
            doThrow(InvalidMessageFormatException.class).when(messageEnvelope).writeTo(any());

            final ChannelInboundHandler handler = RemoteMessageToByteBufCodec.INSTANCE;
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final ChannelPromise promise = channel.newPromise();
                channel.writeAndFlush(new AddressedMessage<>(messageEnvelope, recipient), promise);
                assertFalse(promise.isSuccess());
            }
            finally {
                channel.close();
            }
        }
    }
}
