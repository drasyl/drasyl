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
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.SignedMessage;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
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
        final QuitMessage message = new QuitMessage(1, identity.getPublicKey(), proofOfWork, recipient, REASON_SHUTTING_DOWN);

        final SignatureHandler handler = SignatureHandler.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Message> outboundMessages = pipeline.outboundMessages(Message.class).test();

        pipeline.processOutbound(recipient, message);

        outboundMessages.awaitCount(1).assertValueCount(1);
        outboundMessages.assertValue(m -> m instanceof SignedMessage);
    }

    @Test
    void shouldCompleteFutureExceptionallyAndNotPassMessageIfSigningFailed() throws CryptoException, InterruptedException {
        when(config.getNetworkId()).thenReturn(1);
        when(identity.getPrivateKey().toUncompressedKey()).thenThrow(CryptoException.class);
        final QuitMessage message = new QuitMessage(1, identity.getPublicKey(), proofOfWork, recipient, REASON_SHUTTING_DOWN);

        final SignatureHandler handler = SignatureHandler.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Message> outboundMessages = pipeline.outboundMessages(Message.class).test();

        assertThrows(ExecutionException.class, () -> pipeline.processOutbound(recipient, message).get());
        outboundMessages.await(1, SECONDS);
        outboundMessages.assertNoValues();
    }

    @Test
    void shouldPassthroughOutgoingMessagesFromOtherSender(@Mock final CompressedPublicKey recipient,
                                                          @Mock final QuitMessage message) {
        final SignatureHandler handler = SignatureHandler.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Message> outboundMessages = pipeline.outboundMessages(Message.class).test();

        pipeline.processOutbound(recipient, message);

        outboundMessages.awaitCount(1).assertValueCount(1);
        outboundMessages.assertValue(message);
    }

    @Test
    void shouldPassSignedIncomingMessage() throws CryptoException {
        when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
        when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
        when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
        final QuitMessage message = new QuitMessage(1, identity.getPublicKey(), proofOfWork, identity.getPublicKey(), REASON_SHUTTING_DOWN);
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
        final QuitMessage message = new QuitMessage(1, CompressedPublicKey.of("0248b7221b49775dcae85b02fdc9df41fbed6236c72c5c0356b59961190d3f8a13"), proofOfWork, identity.getPublicKey(), REASON_SHUTTING_DOWN);
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
        final QuitMessage message = new QuitMessage(1, sender, proofOfWork, identity.getPublicKey(), REASON_SHUTTING_DOWN);
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
    void shouldPassthroughIncomingMessagesForOtherRecipient(@Mock(answer = RETURNS_DEEP_STUBS) final QuitMessage message) {
        final SignatureHandler handler = SignatureHandler.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

        pipeline.processInbound(message);

        inboundMessages.awaitCount(1).assertValueCount(1);
        inboundMessages.assertValue(Pair.of(message.getSender(), message));
    }
}