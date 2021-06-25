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
package org.drasyl.remote.handler.crypto;

import com.google.protobuf.MessageLite;
import com.goterl.lazysodium.utils.SessionPair;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.KeyAgreementPublicKey;
import org.drasyl.identity.KeyAgreementSecretKey;
import org.drasyl.identity.KeyPair;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.remote.protocol.InvalidMessageFormatException;
import org.drasyl.remote.protocol.Protocol;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A simple util/helper class for the {@link ArmHandler} that provides some static methods.
 */
public final class ArmHandlerUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ArmHandlerUtil.class);

    private ArmHandlerUtil() {
        // util/helper class
    }

    /**
     * Encrypts the given {@code msg} with the given {@code agreementPair}.
     *
     * @param agreementPair the agreementPair for encryption
     * @param agreementId   the corresponding agreementId
     * @param ctx           the handler the handler context
     * @param recipient     the recipient of the encrypted message
     * @param msg           the message to encrypt
     * @param future        the future to fulfill
     * @return the corresponding {@code future}
     */
    public static CompletableFuture<Void> sendEncrypted(final SessionPair agreementPair,
                                                        final AgreementId agreementId,
                                                        final HandlerContext ctx,
                                                        final Address recipient,
                                                        final RemoteEnvelope<? extends MessageLite> msg,
                                                        final CompletableFuture<Void> future) {
        try {
            msg.setAgreementId(agreementId);

            ctx.passOutbound(recipient,
                    msg.armAndRelease(agreementPair),
                    future); // send encrypted message
            assert msg.refCnt() == 0 : "Assertion error, the message was not correctly released!"; // NOSONAR
        }
        catch (final InvalidMessageFormatException e) {
            future.completeExceptionally(new CryptoException(e));
        }

        return future;
    }

    /**
     * Decrypts the given {@code msg} with the given {@code agreementPair}.
     *
     * @param agreementPair the encryption key pair
     * @param msg           the message to decrypt
     * @return the decrypted message or an exception on error
     * @throws InvalidMessageFormatException if the message could not be decrypted
     */
    public static RemoteEnvelope<? extends MessageLite> decrypt(final SessionPair agreementPair,
                                                                final RemoteEnvelope<? extends MessageLite> msg) throws InvalidMessageFormatException {
        try {
            return msg.disarmAndRelease(agreementPair);
        }
        finally {
            assert msg.refCnt() == 0 : "Assertion error, the message was not correctly released!"; // NOSONAR
        }
    }

    /**
     * Sends an acknowledgement message to the {@code recipientsAddress}.
     *
     * @param ctx               the handler context
     * @param recipientsAddress the address of the recipient
     * @param recipientsKey     the public key of the recipient
     * @param session           the corresponding session
     * @return the future for the acknowledgement message
     */
    public static CompletableFuture<Void> sendAck(final HandlerContext ctx,
                                                  final Address recipientsAddress,
                                                  final IdentityPublicKey recipientsKey,
                                                  final Session session) {
        final Optional<Agreement> agreement = session.getCurrentInactiveAgreement().getValue();

        if (agreement.isPresent() && agreement.get().getAgreementId().isPresent()) {
            final AgreementId agreementId = agreement.get().getAgreementId().get(); // NOSONAR

            // encrypt message with long time key
            return ArmHandlerUtil.sendEncrypted(session.getLongTimeAgreementPair(), session.getLongTimeAgreementId(), ctx, recipientsAddress, RemoteEnvelope.keyExchangeAcknowledgement(
                    ctx.config().getNetworkId(),
                    ctx.identity().getIdentityPublicKey(),
                    ctx.identity().getProofOfWork(),
                    recipientsKey,
                    agreementId
            ), new CompletableFuture<>()).whenComplete((x, e) -> {
                if (e == null) {
                    LOG.trace("[{} => {}] Send ack message for session {}", () -> ctx.identity().getIdentityPublicKey().toString().substring(0, 4), () -> recipientsKey.toString().substring(0, 4), () -> agreementId);
                }
                else {
                    LOG.debug("[{} => {}] Error on sending ack message for session {}: {}", () -> ctx.identity().getIdentityPublicKey().toString().substring(0, 4), () -> recipientsKey.toString().substring(0, 4), () -> agreementId, () -> e);
                }
            });
        }
        else {
            return CompletableFuture.failedFuture(new IllegalStateException("There is currently no inactive agreement."));
        }
    }

    /**
     * Sends the given {@code agreement} as key exchange message to the {@code recipient}.
     *
     * @param ctx           the handler context
     * @param session       the respective session
     * @param agreement     the respective agreement
     * @param recipient     the recipient of the agreement
     * @param recipientsKey the public key of the recipient
     */
    public static void sendKeyExchangeMsg(final HandlerContext ctx,
                                          final Session session,
                                          final Agreement agreement,
                                          final Address recipient,
                                          final IdentityPublicKey recipientsKey) {
        final RemoteEnvelope<Protocol.KeyExchange> msg = RemoteEnvelope.keyExchange(
                ctx.config().getNetworkId(),
                ctx.identity().getIdentityPublicKey(),
                ctx.identity().getProofOfWork(),
                recipientsKey,
                agreement.getKeyPair().getPublicKey());

        // encrypt message with long time key
        ArmHandlerUtil.sendEncrypted(session.getLongTimeAgreementPair(), session.getLongTimeAgreementId(), ctx, recipient, msg, new CompletableFuture<>());
    }

    /**
     * Computes a new inactive agreement if not already present.
     *
     * @param session the corresponding session
     * @return an inactive agreement
     */
    public static Agreement computeInactiveAgreementIfNeeded(final Crypto crypto,
                                                             final Session session) {
        return session.getCurrentInactiveAgreement().computeIfAbsent(() -> {
            // here we compute a new ephemeral key pair for key agreement
            try {
                final KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> keyPair = crypto.generateEphemeralKeyPair();

                return Agreement.builder()
                        .setKeyPair(keyPair)
                        .build();
            }
            catch (final CryptoException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
