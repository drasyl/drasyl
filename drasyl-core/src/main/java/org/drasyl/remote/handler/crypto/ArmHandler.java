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

import com.google.common.cache.CacheBuilder;
import com.google.protobuf.MessageLite;
import com.goterl.lazysodium.utils.SessionPair;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.LongTimeEncryptionEvent;
import org.drasyl.event.Peer;
import org.drasyl.event.PerfectForwardSecrecyEncryptionEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.KeyAgreementPublicKey;
import org.drasyl.identity.KeyAgreementSecretKey;
import org.drasyl.identity.KeyPair;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.remote.protocol.InvalidMessageFormatException;
import org.drasyl.remote.protocol.Protocol;
import org.drasyl.remote.protocol.Protocol.KeyExchange;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.drasyl.util.ConcurrentReference;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Arms (encrypt) outbound and disarms (decrypt) inbound messages. Considers only messages that are
 * addressed from or to us. Messages that could not be (dis-)armed are dropped.
 */
@SuppressWarnings({ "java:S110" })
public class ArmHandler extends SimpleDuplexHandler<RemoteEnvelope<? extends MessageLite>, RemoteEnvelope<? extends MessageLite>, Address> {
    public static final Duration RETRY_INTERVAL = Duration.ofSeconds(10);
    public static final int MAX_SESSIONS = 100_000;
    public static final int MAX_AGREEMENTS = 100;
    private static final Logger LOG = LoggerFactory.getLogger(ArmHandler.class);
    private final Map<IdentityPublicKey, Session> sessions;
    private final Crypto crypto;

    protected ArmHandler(final Map<IdentityPublicKey, Session> sessions, final Crypto crypto) {
        this.sessions = sessions;
        this.crypto = crypto;
    }

    public ArmHandler() {
        this(CacheBuilder.newBuilder()
                .expireAfterAccess(Session.SESSION_EXPIRE_TIME.toMillis(), TimeUnit.MILLISECONDS)
                .maximumSize(MAX_SESSIONS)
                .<IdentityPublicKey, Session>build()
                .asMap(), Crypto.INSTANCE);
    }

    @Override
    protected void matchedOutbound(final HandlerContext ctx,
                                   final Address recipient,
                                   final RemoteEnvelope<? extends MessageLite> msg,
                                   final CompletableFuture<Void> future) throws Exception {
        if (msg.getRecipient() != null
                && ctx.identity().getIdentityPublicKey().equals(msg.getSender())
                && !ctx.identity().getIdentityPublicKey().equals(msg.getRecipient())) {
            final IdentityPublicKey recipientsKey = msg.getRecipient();
            final Session session = getSession(ctx, recipientsKey);

            ctx.independentScheduler().scheduleDirect(() -> checkForRenewAgreement(ctx, session, recipient, recipientsKey));

            final Optional<Agreement> agreement = session.getCurrentActiveAgreement().getValue();
            if (agreement.isPresent() && agreement.get().isInitialized()) {
                // do PFS encryption
                ArmHandler.encrypt(agreement.get().getSessionPair().get(), agreement.get().getAgreementId().get(), ctx, recipient, msg, future); //NOSONAR
            }
            else {
                // do normal encryption
                ArmHandler.encrypt(session.getLongTimeAgreementPair(), session.getLongTimeAgreementId(), ctx, recipient, msg, future);

                // start key exchange
                ctx.independentScheduler().scheduleDirect(() -> doKeyExchange(session, ctx, recipient, recipientsKey));
            }
        }
        else {
            // pass unencrypted to next handler
            ctx.passOutbound(recipient, msg, future);
        }
    }

    /**
     * Gets or computes the {@link Session} for the corresponding {@code recipientsKey}.
     *
     * @param ctx           the handler context
     * @param recipientsKey the recipients key
     * @return corresponding {@link Session}
     */
    private Session getSession(final HandlerContext ctx, final IdentityPublicKey recipientsKey) {
        return sessions.computeIfAbsent(recipientsKey, k -> {
            try {
                final SessionPair longTimeSession = crypto.generateSessionKeyPair(
                        ctx.identity().getKeyAgreementKeyPair(),
                        recipientsKey.getLongTimeKeyAgreementKey());
                final AgreementId agreementId = AgreementId.of(
                        ctx.identity().getKeyAgreementPublicKey(),
                        recipientsKey.getLongTimeKeyAgreementKey());

                return new Session(agreementId, longTimeSession);
            }
            catch (final CryptoException e) {
                throw new RuntimeException(e);
            }
        });
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
    private static CompletableFuture<Void> encrypt(final SessionPair agreementPair,
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
        }
        catch (final InvalidMessageFormatException e) {
            future.completeExceptionally(new CryptoException(e));
        }

        return future;
    }

    /**
     * Generates a new agreement if the current {@link Agreement#isStale()} and the {@link
     * Session#getInitializedAgreements()}.{@link ConcurrentReference#getValue()}.{@link
     * Optional#isEmpty() isEmpty()}.
     *
     * @param session the session
     * @param ctx     the handler context
     * @return old or new session, but not {@code null}
     */
    private Agreement computeOnEmptyOrStaleAgreement(final Session session,
                                                     final IdentityPublicKey recipientsKey,
                                                     final HandlerContext ctx) {
        return session.getCurrentActiveAgreement()
                // remove stale agreement
                .computeOnCondition(a -> a != null && a.isStale(), agreement -> {
                    ctx.passEvent(LongTimeEncryptionEvent.of(Peer.of(recipientsKey)), new CompletableFuture<>());

                    return null;
                })
                // compute new session if no active or half open agreement is available
                .orElseGet(() -> computeInactiveAgreementIfNeeded(session));
    }

    /**
     * Does a key exchange, if the current {@link Agreement#isStale()}, if we don't have any valid
     * agreement or if it is time to renew the agreement.
     *
     * @param session            the session for this {@code recipientPublicKey}
     * @param ctx                the handler context
     * @param recipient          the recipients address
     * @param recipientPublicKey the recipient's public key
     */
    private void doKeyExchange(final Session session,
                               final HandlerContext ctx,
                               final Address recipient,
                               final IdentityPublicKey recipientPublicKey) {
        final Agreement agreement = computeOnEmptyOrStaleAgreement(session, recipientPublicKey, ctx);

        /*
         * When a new session was created or an old session is used that has currently no
         * completed agreement, we want to send a key exchange message.
         */
        if ((!agreement.isInitialized() && session.getLastKeyExchangeAt().get() < System.currentTimeMillis() - RETRY_INTERVAL.toMillis())) {
            session.getLastKeyExchangeAt().set(System.currentTimeMillis());
            ArmHandler.sendKeyExchangeMsg(ctx, session, agreement, recipient, recipientPublicKey);
        }
    }

    @Override
    protected void matchedInbound(final HandlerContext ctx,
                                  final Address sender,
                                  final RemoteEnvelope<? extends MessageLite> msg,
                                  final CompletableFuture<Void> future) throws Exception {
        if (msg.getSender() != null
                && ctx.identity().getIdentityPublicKey().equals(msg.getRecipient())
                && !ctx.identity().getIdentityPublicKey().equals(msg.getSender())
        ) {
            final IdentityPublicKey recipientsKey = msg.getSender(); // on inbound our recipient is the sender of the message
            final Session session = getSession(ctx, recipientsKey);
            final RemoteEnvelope<? extends MessageLite> plaintextMsg;
            final AgreementId agreementId = msg.getAgreementId();
            boolean longTimeEncryptionUsed = false;

            ctx.independentScheduler().scheduleDirect(() -> checkForRenewAgreement(ctx, session, sender, recipientsKey));

            if (agreementId != null) {
                // long time encryption was used
                if (session.getLongTimeAgreementId().equals(agreementId)) {
                    plaintextMsg = decrypt(session.getLongTimeAgreementPair(), msg);
                    longTimeEncryptionUsed = true;
                }
                // pfs encryption was used
                else {
                    final Agreement agreement = session.getInitializedAgreements().get(agreementId);
                    final Optional<Agreement> inactiveAgreement = session.getCurrentInactiveAgreement().getValue();

                    if (agreement != null && agreement.getSessionPair().isPresent()) {
                        plaintextMsg = ArmHandler.decrypt(agreement.getSessionPair().get(), msg);

                        if (agreement.isStale()) {
                            // remove stale agreement
                            session.getInitializedAgreements().remove(agreementId);
                        }
                    }
                    // Maybe the first encrypted message is arrived before the corresponding ACK. In this case this message acts also as ACK.
                    else if (inactiveAgreement.isPresent() && agreementId.equals(inactiveAgreement.get().getAgreementId().orElse(null))) {
                        ArmHandler.receivedAcknowledgement(ctx, agreementId, session, recipientsKey);

                        // at this point, the session should be available
                        plaintextMsg = ArmHandler.decrypt(session.getInitializedAgreements().get(agreementId).getSessionPair().orElse(null), msg);
                    }
                    else {
                        future.completeExceptionally(new CryptoException("Decryption-Error: agreement id could not be found. Message was dropped."));
                        LOG.trace("Agreement id `{}` could not be found. Dropped message: {}", () -> agreementId, () -> msg);

                        // on unknown agreement id we want to send a new key exchange message, may be we're crashed and the recipient node sends us an old agreement
                        doKeyExchange(session, ctx, sender, recipientsKey);
                        return;
                    }
                }
            }
            else {
                // no encryption was used
                ctx.passInbound(sender, msg, future);
                return;
            }

            // check for key exchange msg
            if (longTimeEncryptionUsed && plaintextMsg.getPrivateHeader().getType() == Protocol.MessageType.KEY_EXCHANGE) {
                receivedKeyExchangeMessage(ctx, sender, plaintextMsg, session, future);
            }
            // check for key exchange acknowledgement msg
            else if (plaintextMsg.getPrivateHeader().getType() == Protocol.MessageType.KEY_EXCHANGE_ACKNOWLEDGEMENT) {
                final Protocol.KeyExchangeAcknowledgement agreementAckMsg = (Protocol.KeyExchangeAcknowledgement) plaintextMsg.getBodyAndRelease();

                ArmHandler.receivedAcknowledgement(ctx, AgreementId.of(agreementAckMsg.getAgreementId().toByteArray()), session, recipientsKey);
                future.complete(null);
            }
            else {
                ctx.passInbound(sender, plaintextMsg, future);
            }
        }
        else {
            ctx.passInbound(sender, msg, future);
        }
    }

    /**
     * Handles a received acknowledgement message and does a full initialization of the
     * corresponding agreement.
     *
     * @param ctx                 the handler context
     * @param id                  the agreement id
     * @param session             the corresponding session
     * @param recipientsPublicKey the public key of the recipient
     */
    private static void receivedAcknowledgement(final HandlerContext ctx,
                                                final AgreementId id,
                                                final Session session,
                                                final IdentityPublicKey recipientsPublicKey) {
        session.getCurrentInactiveAgreement().computeOnCondition(a -> a != null && id.equals(a.getAgreementId().orElse(null)), a -> {
            final Agreement initializedAgreement = a.toBuilder()
                    .setStaleAt(OptionalLong.of(System.currentTimeMillis() + Session.SESSION_EXPIRE_TIME.toMillis()))
                    .build();

            session.getInitializedAgreements().put(id, initializedAgreement);
            session.getCurrentActiveAgreement().computeOnCondition(c -> true, f -> initializedAgreement);

            ctx.passEvent(PerfectForwardSecrecyEncryptionEvent.of(Peer.of(recipientsPublicKey)), new CompletableFuture<>());

            return null;
        });
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
    private static CompletableFuture<Void> sendAck(final HandlerContext ctx,
                                                   final Address recipientsAddress,
                                                   final IdentityPublicKey recipientsKey,
                                                   final Session session) {
        // encrypt message with long time key
        return encrypt(session.getLongTimeAgreementPair(), session.getLongTimeAgreementId(), ctx, recipientsAddress, RemoteEnvelope.keyExchangeAcknowledgement(
                ctx.config().getNetworkId(),
                ctx.identity().getIdentityPublicKey(),
                ctx.identity().getProofOfWork(),
                recipientsKey,
                session.getCurrentInactiveAgreement().getValue().get().getAgreementId().get()
        ), new CompletableFuture<>());
    }

    /**
     * Handles a received {@link KeyExchange} message and creates/initializes the corresponding
     * {@link Agreement} object.
     *
     * @param ctx          the handler context
     * @param sender       the sender of the {@link KeyExchange} message
     * @param plaintextMsg the message
     * @param session      the corresponding session
     * @param future       the future to fulfill
     */
    private void receivedKeyExchangeMessage(final HandlerContext ctx,
                                            final Address sender,
                                            final RemoteEnvelope<? extends MessageLite> plaintextMsg,
                                            final Session session,
                                            final CompletableFuture<Void> future) {
        ctx.independentScheduler().scheduleDirect(() -> {
            try {
                final IdentityPublicKey recipientsKey = plaintextMsg.getSender(); // on inbound our recipient is the sender of the message
                @SuppressWarnings("unchecked") final KeyExchange keyExchangeMsg = ((RemoteEnvelope<KeyExchange>) plaintextMsg).getBodyAndRelease();
                final KeyAgreementPublicKey sessionKey = KeyAgreementPublicKey.of(keyExchangeMsg.getSessionKey().toByteArray());

                LOG.trace("[{} <= {}] Received key exchange message: {}", () -> ctx.identity().getIdentityPublicKey().toString().substring(0, 4), () -> recipientsKey.toString().substring(0, 4), () -> keyExchangeMsg);

                doKeyExchange(session, ctx, sender, recipientsKey);
                session.getCurrentInactiveAgreement().computeOnCondition(Objects::nonNull,
                        agreement -> agreement.toBuilder()
                                .setRecipientsKeyAgreementKey(Optional.of(sessionKey))
                                .setAgreementId(Optional.of(AgreementId.of(agreement.getKeyPair().getPublicKey(), sessionKey)))
                                .build());

                // encrypt message with long time key
                FutureCombiner.getInstance().add(ArmHandler.sendAck(ctx, sender, recipientsKey, session)).combine(future);
            }
            catch (final Exception e) {
                future.completeExceptionally(new CryptoException(e));
            }
        });
    }

    /**
     * Decrypts the given {@code msg} with the given {@code agreementPair}.
     *
     * @param agreementPair the encryption key pair
     * @param msg           the message to decrypt
     * @return the decrypted message or an exception on error
     * @throws InvalidMessageFormatException if the message could not be decrypted
     */
    private static RemoteEnvelope<? extends MessageLite> decrypt(final SessionPair agreementPair,
                                                                 final RemoteEnvelope<? extends MessageLite> msg) throws InvalidMessageFormatException {
        return msg.disarmAndRelease(agreementPair);
    }

    /**
     * Checks if the currently active agreement must be renewed and does so.
     *
     * @param ctx           the handler context
     * @param session       the respective session
     * @param recipient     the recipient of the agreement
     * @param recipientsKey the public key of the recipient
     */
    private void checkForRenewAgreement(final HandlerContext ctx,
                                        final Session session,
                                        final Address recipient,
                                        final IdentityPublicKey recipientsKey) {
        // remove stale agreement
        final Optional<Agreement> agreement = session.getCurrentActiveAgreement().computeOnCondition(a -> a != null && a.isStale(), a -> {
            ctx.passEvent(LongTimeEncryptionEvent.of(Peer.of(recipientsKey)), new CompletableFuture<>());

            return null;
        });

        if (agreement.isPresent() && agreement.get().isRenewable() && session.getLastRenewAttemptAt().get() < System.currentTimeMillis() - RETRY_INTERVAL.toMillis()) {
            session.getLastRenewAttemptAt().set(System.currentTimeMillis());
            final Agreement inactiveAgreement = computeInactiveAgreementIfNeeded(session);

            ArmHandler.sendKeyExchangeMsg(ctx, session, inactiveAgreement, recipient, recipientsKey);
        }
    }

    /**
     * Computes a new inactive agreement if not already present.
     *
     * @param session the corresponding session
     * @return an inactive agreement
     */
    private Agreement computeInactiveAgreementIfNeeded(final Session session) {
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

    /**
     * Sends the given {@code agreement} as key exchange message to the {@code recipient}.
     *
     * @param ctx           the handler context
     * @param session       the respective session
     * @param agreement     the respective agreement
     * @param recipient     the recipient of the agreement
     * @param recipientsKey the public key of the recipient
     */
    private static void sendKeyExchangeMsg(final HandlerContext ctx,
                                           final Session session,
                                           final Agreement agreement,
                                           final Address recipient,
                                           final IdentityPublicKey recipientsKey) {
        final RemoteEnvelope<KeyExchange> msg = RemoteEnvelope.keyExchange(
                ctx.config().getNetworkId(),
                ctx.identity().getIdentityPublicKey(),
                ctx.identity().getProofOfWork(),
                recipientsKey,
                agreement.getKeyPair().getPublicKey());

        // encrypt message with long time key
        encrypt(session.getLongTimeAgreementPair(), session.getLongTimeAgreementId(), ctx, recipient, msg, new CompletableFuture<>())
                .exceptionally(e -> {
                    LOG.warn("Error on key exchange: ", e);
                    return null;
                });

        LOG.trace("[{} => {}] Do key exchange: {}", () -> ctx.identity().getIdentityPublicKey().toString().substring(0, 4), () -> recipientsKey.toString().substring(0, 4), () -> msg);
    }
}
