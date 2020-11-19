/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.pipeline;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.MessageId;
import org.drasyl.peer.connection.message.SignedMessage;
import org.drasyl.peer.connection.message.UserAgent;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignatureHandlerTest {
    @Mock
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private TypeValidator inboundValidator;
    @Mock
    private TypeValidator outboundValidator;
    @Mock
    private CompressedPublicKey sender;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock
    private CompressedPublicKey recipient;

    @Test
    void shouldSignUnsignedOutgoingMessages(@Mock final CompressedPublicKey recipient) throws CryptoException {
        when(config.getNetworkId()).thenReturn(1);
        when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
        when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
        when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
        final Message message = new MyMessage(identity.getPublicKey(), proofOfWork, recipient);

        final SignatureHandler handler = SignatureHandler.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Object> outboundMessages = pipeline.outboundOnlyMessages().test();

        pipeline.processOutbound(recipient, message);

        outboundMessages.awaitCount(1).assertValueCount(1);
        outboundMessages.assertValue(m -> m instanceof SignedMessage);
    }

    @Test
    void shouldCompleteFutureExceptionallyAndNotPassMessageIfSigningFailed() throws CryptoException, InterruptedException {
        when(config.getNetworkId()).thenReturn(1);
        when(identity.getPrivateKey().toUncompressedKey()).thenThrow(CryptoException.class);
        final Message message = new MyMessage(identity.getPublicKey(), proofOfWork, recipient);

        final SignatureHandler handler = SignatureHandler.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Object> outboundMessages = pipeline.outboundOnlyMessages().test();

        assertThrows(ExecutionException.class, () -> pipeline.processOutbound(recipient, message).get());
        outboundMessages.await(1, SECONDS);
        outboundMessages.assertNoValues();
    }

    @Test
    void shouldPassthroughOutgoingMessagesFromOtherSender(@Mock final CompressedPublicKey recipient,
                                                          @Mock final Message message) {
        final SignatureHandler handler = SignatureHandler.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Object> outboundMessages = pipeline.outboundOnlyMessages().test();

        pipeline.processOutbound(recipient, message);

        outboundMessages.awaitCount(1).assertValueCount(1);
        outboundMessages.assertValue(message);
    }

    @Test
    void shouldPassSignedIncomingMessage() throws CryptoException {
        when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
        when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
        when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
        final Message message = new MyMessage(identity.getPublicKey(), proofOfWork, identity.getPublicKey());
        final SignedMessage signedMessage = new SignedMessage(message.getNetworkId(), message.getSender(), message.getProofOfWork(), message.getRecipient(), message);
        Crypto.sign(identity.getPrivateKey().toUncompressedKey(), signedMessage);

        final SignatureHandler handler = SignatureHandler.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

        pipeline.processInbound(signedMessage);

        inboundMessages.awaitCount(1).assertValueCount(1);
        inboundMessages.assertValue(Pair.of(identity.getPublicKey(), message));
    }

    @Test
    void shouldNotPassIncomingMessageWithInvalidSignatureAndCompleteFutureExceptionally() throws CryptoException, InterruptedException {
        when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
        when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
        when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
        final Message message = new MyMessage(CompressedPublicKey.of("0248b7221b49775dcae85b02fdc9df41fbed6236c72c5c0356b59961190d3f8a13"), proofOfWork, identity.getPublicKey());
        final SignedMessage signedMessage = new SignedMessage(message.getNetworkId(), message.getSender(), message.getProofOfWork(), message.getRecipient(), message);
        Crypto.sign(identity.getPrivateKey().toUncompressedKey(), signedMessage);

        final SignatureHandler handler = SignatureHandler.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

        assertThrows(ExecutionException.class, () -> pipeline.processInbound(signedMessage).get());
        inboundMessages.await(1, SECONDS);
        inboundMessages.assertNoValues();
    }

    @Test
    void shouldNotPassIncomingMessageAndCompleteFutureExceptionallyWhenPublicKeyCantBeExtracted(@Mock final CompressedPublicKey sender) throws CryptoException, InterruptedException {
        when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
        when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
        when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
        when(sender.toUncompressedKey()).thenThrow(CryptoException.class);
        final Message message = new MyMessage(sender, proofOfWork, identity.getPublicKey());
        final SignedMessage signedMessage = new SignedMessage(message.getNetworkId(), message.getSender(), message.getProofOfWork(), message.getRecipient(), message);
        Crypto.sign(identity.getPrivateKey().toUncompressedKey(), signedMessage);

        final SignatureHandler handler = SignatureHandler.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

        assertThrows(ExecutionException.class, () -> pipeline.processInbound(signedMessage).get());
        inboundMessages.await(1, SECONDS);
        inboundMessages.assertNoValues();
    }

    @Test
    void shouldPassthroughIncomingMessagesForOtherRecipient(@Mock(answer = RETURNS_DEEP_STUBS) final Message message) {
        final SignatureHandler handler = SignatureHandler.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

        pipeline.processInbound(message);

        inboundMessages.awaitCount(1).assertValueCount(1);
        inboundMessages.assertValue(Pair.of(message.getSender(), message));
    }

    static class MyMessage implements Message {
        private final CompressedPublicKey sender;
        private final ProofOfWork proofOfWork;
        private final CompressedPublicKey recipient;

        public MyMessage(final CompressedPublicKey sender,
                         final ProofOfWork proofOfWork,
                         final CompressedPublicKey recipient) {
            this.sender = sender;
            this.proofOfWork = proofOfWork;
            this.recipient = recipient;
        }

        public MyMessage() throws CryptoException {
            this(
                    CompressedPublicKey.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a"),
                    ProofOfWork.of(13290399),
                    CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")
            );
        }

        @Override
        public MessageId getId() {
            return MessageId.of("89ba3cd9efb7570eb3126d11");
        }

        @Override
        public UserAgent getUserAgent() {
            return UserAgent.generate();
        }

        @Override
        public int getNetworkId() {
            return 1;
        }

        @Override
        public CompressedPublicKey getSender() {
            return sender;
        }

        @Override
        public ProofOfWork getProofOfWork() {
            return proofOfWork;
        }

        @Override
        public CompressedPublicKey getRecipient() {
            return recipient;
        }

        @Override
        public short getHopCount() {
            return 0;
        }

        @Override
        public void incrementHopCount() {

        }
    }
}