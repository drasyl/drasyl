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
import org.drasyl.DrasylConfig;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.protocol.AcknowledgementMessage;
import org.drasyl.remote.protocol.ApplicationMessage;
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
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
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
        void shouldConvertByteBufToEnvelope(@Mock final InetSocketAddressWrapper sender) throws IOException {
            final RemoteMessage message = AcknowledgementMessage.of(1337, senderPublicKey, proofOfWork, recipientPublicKey, correspondingId);
            final ChannelInboundHandler handler = RemoteMessageToByteBufCodec.INSTANCE;
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
                message.writeTo(byteBuf);
                pipeline.pipeline().fireChannelRead(new AddressedMessage<>((Object) byteBuf, (Address) sender));

                assertThat(((AddressedMessage<Object, Address>) pipeline.readInbound()).message(), instanceOf(PartialReadMessage.class));
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class Encode {
        @Test
        void shouldConvertEnvelopeToByteBuf(@Mock final InetSocketAddressWrapper recipient) throws IOException {
            final ApplicationMessage message = ApplicationMessage.of(1337, IdentityPublicKey.of("18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127"), ProofOfWork.of(3556154), IdentityPublicKey.of("02bfa672181ef9c0a359dc68cc3a4d34f47752c8886a0c5661dc253ff5949f1b"), byte[].class.getName(), ByteString.copyFromUtf8("Hello World"));
            final ChannelInboundHandler handler = RemoteMessageToByteBufCodec.INSTANCE;
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.pipeline().writeAndFlush(new AddressedMessage<>(message, recipient));

                final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
                message.writeTo(byteBuf);

                assertEquals(new AddressedMessage<>(byteBuf, recipient), pipeline.readOutbound());

                byteBuf.release();
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldCompleteFutureExceptionallyWhenConversionFail(@Mock final InetSocketAddressWrapper recipient,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage messageEnvelope) throws IOException {
            doThrow(RuntimeException.class).when(messageEnvelope).writeTo(any());

            final ChannelInboundHandler handler = RemoteMessageToByteBufCodec.INSTANCE;
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final ChannelPromise promise = pipeline.newPromise();
                pipeline.pipeline().writeAndFlush(new AddressedMessage<>((Object) messageEnvelope, (Address) recipient), promise);
                assertFalse(promise.isSuccess());
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }
}
