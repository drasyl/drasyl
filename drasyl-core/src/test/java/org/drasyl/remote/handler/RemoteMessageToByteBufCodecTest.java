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
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.DefaultEmbeddedPipeline;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
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
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
            final Handler handler = RemoteMessageToByteBufCodec.INSTANCE;
            try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<PartialReadMessage> inboundMessages = pipeline.inboundMessages(PartialReadMessage.class).test();

                final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
                message.writeTo(byteBuf);
                pipeline.processInbound(sender, byteBuf).join();

                inboundMessages.awaitCount(1)
                        .assertValueCount(1);
            }
        }
    }

    @Nested
    class Encode {
        @Test
        void shouldConvertEnvelopeToByteBuf(@Mock final InetSocketAddressWrapper recipient) throws IOException {
            final ApplicationMessage message = ApplicationMessage.of(1337, IdentityPublicKey.of("18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127"), ProofOfWork.of(3556154), IdentityPublicKey.of("02bfa672181ef9c0a359dc68cc3a4d34f47752c8886a0c5661dc253ff5949f1b"), byte[].class.getName(), ByteString.copyFromUtf8("Hello World"));
            final Handler handler = RemoteMessageToByteBufCodec.INSTANCE;
            try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<AddressedEnvelope<Address, Object>> outboundMessages = pipeline.outboundMessagesWithRecipient().test();
                pipeline.processOutbound(recipient, message).join();

                final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
                message.writeTo(byteBuf);

                outboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new DefaultAddressedEnvelope<>(null, recipient, byteBuf));

                byteBuf.release();
            }
        }

        @Test
        void shouldCompleteFutureExceptionallyWhenConversionFail(@Mock final InetSocketAddressWrapper recipient,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage messageEnvelope) throws IOException {
            doThrow(RuntimeException.class).when(messageEnvelope).writeTo(any());

            final Handler handler = RemoteMessageToByteBufCodec.INSTANCE;
            try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, identity, peersManager, handler)) {
                assertThrows(ExecutionException.class, () -> pipeline.processOutbound(recipient, messageEnvelope).get());
            }
        }
    }
}
