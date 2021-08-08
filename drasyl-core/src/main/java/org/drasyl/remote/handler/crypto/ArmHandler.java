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
import com.goterl.lazysodium.utils.SessionPair;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.channel.MigrationEvent;
import org.drasyl.channel.MigrationInboundMessage;
import org.drasyl.channel.MigrationOutboundMessage;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.LongTimeEncryptionEvent;
import org.drasyl.event.Peer;
import org.drasyl.event.PerfectForwardSecrecyEncryptionEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.KeyAgreementPublicKey;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.remote.protocol.ArmedMessage;
import org.drasyl.remote.protocol.FullReadMessage;
import org.drasyl.remote.protocol.KeyExchangeAcknowledgementMessage;
import org.drasyl.remote.protocol.KeyExchangeMessage;
import org.drasyl.util.ConcurrentReference;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongUnaryOperator;

import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;

/**
 * Arms (encrypt) outbound and disarms (decrypt) inbound messages. Considers only messages that are
 * addressed from or to us. Messages that could not be (dis-)armed are dropped.
 */
@SuppressWarnings({ "java:S110" })
public class ArmHandler extends SimpleDuplexHandler<ArmedMessage, FullReadMessage<?>, Address> {
    private static final Logger LOG = LoggerFactory.getLogger(ArmHandler.class);
    private final Map<IdentityPublicKey, Session> sessions;
    private final Crypto crypto;
    private final Duration expireAfter;
    private final Duration retryInterval;
    private final int maxAgreements;
    private final LongUnaryOperator updateLastModificationTime;

    protected ArmHandler(final Map<IdentityPublicKey, Session> sessions,
                         final Crypto crypto,
                         final int maxAgreements,
                         final Duration expireAfter,
                         final Duration retryInterval,
                         final LongUnaryOperator updateLastModificationTime) {
        this.sessions = sessions;
        this.crypto = crypto;
        this.maxAgreements = maxAgreements;
        this.expireAfter = expireAfter;
        this.retryInterval = retryInterval;
        this.updateLastModificationTime = updateLastModificationTime;
    }

    protected ArmHandler(final Map<IdentityPublicKey, Session> sessions,
                         final Crypto crypto,
                         final int maxAgreements,
                         final Duration expireAfter,
                         final Duration retryInterval) {
        this(sessions,
                crypto,
                maxAgreements,
                expireAfter,
                retryInterval,
                time -> {
                    if (time < System.currentTimeMillis() - retryInterval.toMillis()) {
                        return System.currentTimeMillis();
                    }

                    return time;
                });
    }

    public ArmHandler(final int maxSessionsCount,
                      final int maxAgreements,
                      final Duration expireAfter,
                      final Duration retryInterval) {
        this(CacheBuilder.newBuilder()
                .expireAfterAccess(expireAfter.toMillis(), TimeUnit.MILLISECONDS)
                .maximumSize(maxSessionsCount)
                .<IdentityPublicKey, Session>build()
                .asMap(), Crypto.INSTANCE, maxAgreements, expireAfter, retryInterval);
    }

    protected void filteredOutbound(final ChannelHandlerContext ctx,
                                    final Address recipient,
                                    final FullReadMessage<?> msg,
                                    final CompletableFuture<Void> future) {
        final IdentityPublicKey recipientsKey = msg.getRecipient();
        final Session session = getSession(ctx, recipientsKey);

        if (this.maxAgreements > 0) {
            ctx.executor().execute(() -> checkForRenewAgreement(ctx, session, recipient, recipientsKey));
        }

        final Optional<Agreement> agreement = session.getCurrentActiveAgreement().getValue();
        if (agreement.isPresent()
                && agreement.get().isInitialized()
                && !agreement.get().isStale()) {
            // do PFS encryption
            ArmHandlerUtil.sendEncrypted(crypto, agreement.get().getSessionPair().get(), agreement.get().getAgreementId().get(), ctx, recipient, msg, future); //NOSONAR
        }
        else {
            // do normal encryption
            ArmHandlerUtil.sendEncrypted(crypto, session.getLongTimeAgreementPair(), session.getLongTimeAgreementId(), ctx, recipient, msg, future);

            // start key exchange
            if (this.maxAgreements > 0) {
                ctx.executor().execute(() -> doKeyExchange(session, ctx, recipient, recipientsKey));
            }
        }
    }

    protected void filteredInbound(final ChannelHandlerContext ctx,
                                   final Address sender,
                                   final ArmedMessage msg,
                                   final CompletableFuture<Void> future) throws Exception {
        final IdentityPublicKey recipientsKey = msg.getSender(); // on inbound our recipient is the sender of the message
        final Session session = getSession(ctx, recipientsKey);
        final FullReadMessage<?> plaintextMsg;
        final AgreementId agreementId = msg.getAgreementId();
        boolean longTimeEncryptionUsed = false;

        if (this.maxAgreements > 0) {
            ctx.executor().execute(() -> checkForRenewAgreement(ctx, session, sender, recipientsKey));
        }

        // long time encryption was used
        if (session.getLongTimeAgreementId().equals(agreementId)) {
            plaintextMsg = ArmHandlerUtil.decrypt(crypto, session.getLongTimeAgreementPair(), msg);
            longTimeEncryptionUsed = true;
        }
        // pfs encryption was used
        else {
            final Agreement agreement = session.getInitializedAgreements().get(agreementId);
            final Optional<Agreement> inactiveAgreement = session.getCurrentInactiveAgreement().getValue();

            if (agreement != null && agreement.getSessionPair().isPresent()) {
                plaintextMsg = ArmHandlerUtil.decrypt(crypto, agreement.getSessionPair().get(), msg);

                if (agreement.isStale()) {
                    // remove stale agreement
                    session.getInitializedAgreements().remove(agreementId);

                    session.getCurrentActiveAgreement().computeOnCondition(a -> agreementId.equals(a.getAgreementId().orElse(null)), a -> {
                        ctx.fireUserEventTriggered(new MigrationEvent(LongTimeEncryptionEvent.of(Peer.of(recipientsKey))));

                        return null;
                    });
                }
            }
            // Maybe the first encrypted message is arrived before the corresponding ACK. In this case this message acts also as ACK.
            else if (inactiveAgreement.isPresent() && agreementId.equals(inactiveAgreement.get().getAgreementId().orElse(null))) {
                receivedAcknowledgement(ctx, agreementId, session, recipientsKey);

                // at this point, the session should be available
                plaintextMsg = ArmHandlerUtil.decrypt(crypto, session.getInitializedAgreements().get(agreementId).getSessionPair().orElse(null), msg);
            }
            else {
                future.completeExceptionally(new CryptoException("Decryption-Error: agreement id could not be found. Message was dropped."));
                LOG.debug("Agreement id `{}` could not be found. Dropped message: {}", () -> agreementId, () -> msg);

                // on unknown agreement id we want to send a new key exchange message, may be we're crashed and the recipient node sends us an old agreement
                if (this.maxAgreements > 0) {
                    doKeyExchange(session, ctx, sender, recipientsKey);
                }
                return;
            }
        }

        // check for key exchange msg
        if (this.maxAgreements > 0 && longTimeEncryptionUsed && plaintextMsg instanceof KeyExchangeMessage) {
            receivedKeyExchangeMessage(ctx, sender, (KeyExchangeMessage) plaintextMsg, session, future);
        }
        // check for key exchange acknowledgement msg
        else if (this.maxAgreements > 0 && plaintextMsg instanceof KeyExchangeAcknowledgementMessage) {
            receivedAcknowledgement(ctx, ((KeyExchangeAcknowledgementMessage) plaintextMsg).getAcknowledgementAgreementId(), session, recipientsKey);
            future.complete(null);
        }
        else {
            ctx.fireChannelRead(new MigrationInboundMessage<>((Object) plaintextMsg, sender, future));
        }
    }

    /**
     * Gets or computes the {@link Session} for the corresponding {@code recipientsKey}.
     *
     * @param ctx           the handler context
     * @param recipientsKey the recipients key
     * @return corresponding {@link Session}
     */
    private Session getSession(final ChannelHandlerContext ctx,
                               final IdentityPublicKey recipientsKey) {
        return sessions.computeIfAbsent(recipientsKey, k -> {
            try {
                final SessionPair longTimeSession = crypto.generateSessionKeyPair(
                        ctx.attr(IDENTITY_ATTR_KEY).get().getKeyAgreementKeyPair(),
                        recipientsKey.getLongTimeKeyAgreementKey());
                final AgreementId agreementId = AgreementId.of(
                        ctx.attr(IDENTITY_ATTR_KEY).get().getKeyAgreementPublicKey(),
                        recipientsKey.getLongTimeKeyAgreementKey());

                return new Session(agreementId, longTimeSession, this.maxAgreements, this.expireAfter);
            }
            catch (final CryptoException e) {
                throw new RuntimeException(e);
            }
        });
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
                                                     final ChannelHandlerContext ctx) {
        return session.getCurrentActiveAgreement()
                // remove stale agreement
                .computeOnCondition(a -> a != null && a.isStale(), agreement -> {
                    ctx.fireUserEventTriggered(new MigrationEvent(LongTimeEncryptionEvent.of(Peer.of(recipientsKey))));

                    return null;
                })
                // compute new session if no active or half open agreement is available
                .orElseGet(() -> ArmHandlerUtil.computeInactiveAgreementIfNeeded(crypto, session));
    }

    /**
     * Does a key exchange, if the last key exchange is overdue.
     *
     * @param session            the session for this {@code recipientPublicKey}
     * @param ctx                the handler context
     * @param recipient          the recipients address
     * @param recipientPublicKey the recipient's public key
     */
    private void doKeyExchange(final Session session,
                               final ChannelHandlerContext ctx,
                               final Address recipient,
                               final IdentityPublicKey recipientPublicKey) {
        final Agreement agreement = computeOnEmptyOrStaleAgreement(session, recipientPublicKey, ctx);

        /*
         * When a new session was created or an old session is used that has currently no
         * completed agreement, we want to send a key exchange message.
         */
        if (session.getLastKeyExchangeAt().getAndUpdate(updateLastModificationTime) < System.currentTimeMillis() - retryInterval.toMillis()) {
            LOG.trace("[{} => {}] Send key exchange message, do to key exchange overdue", () -> ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().toString().substring(0, 4), () -> recipientPublicKey.toString().substring(0, 4));
            ArmHandlerUtil.sendKeyExchangeMsg(crypto, ctx, session, agreement, recipient, recipientPublicKey);
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
    private void receivedAcknowledgement(final ChannelHandlerContext ctx,
                                         final AgreementId id,
                                         final Session session,
                                         final IdentityPublicKey recipientsPublicKey) {
        LOG.trace("[{} <= {}] Received ack message", () -> ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().toString().substring(0, 4), () -> recipientsPublicKey.toString().substring(0, 4));

        session.getCurrentInactiveAgreement().computeOnCondition(a -> a != null && id.equals(a.getAgreementId().orElse(null)), a -> {
            final Agreement initializedAgreement = a.toBuilder()
                    .setStaleAt(OptionalLong.of(System.currentTimeMillis() + this.expireAfter.toMillis()))
                    .build();

            session.getInitializedAgreements().put(id, initializedAgreement);
            session.getCurrentActiveAgreement().computeOnCondition(c -> true, f -> initializedAgreement);

            ctx.fireUserEventTriggered(new MigrationEvent(PerfectForwardSecrecyEncryptionEvent.of(Peer.of(recipientsPublicKey))));

            return null;
        });
    }

    /**
     * Handles a received {@link KeyExchangeMessage} message and creates/initializes the
     * corresponding {@link Agreement} object.
     *
     * @param ctx          the handler context
     * @param sender       the sender of the {@link KeyExchangeMessage} message
     * @param plaintextMsg the message
     * @param session      the corresponding session
     * @param future       the future to fulfill
     */
    private void receivedKeyExchangeMessage(final ChannelHandlerContext ctx,
                                            final Address sender,
                                            final KeyExchangeMessage plaintextMsg,
                                            final Session session,
                                            final CompletableFuture<Void> future) {
        //TODO: Umschreiben. Hier lieber schauen, ob die Exchange Nachricht sich auf den aktuellen aktiven Key bezieht oder einen möglichen neuen
        //TODO: in jedem Fall den jeweiligen Key mit einem ACK bestätigen.
        //TODO: Handlet es sich um einen neuen Key muss auch ein neuer "inactive" key erzeugt und bestätigt werden
        // on inbound our recipient is the sender of the message
        // encrypt message with long time key
        ctx.executor().execute(() -> {
            try {
                //TODO: Umschreiben. Hier lieber schauen, ob die Exchange Nachricht sich auf den aktuellen aktiven Key bezieht oder einen möglichen neuen
                //TODO: in jedem Fall den jeweiligen Key mit einem ACK bestätigen.
                //TODO: Handlet es sich um einen neuen Key muss auch ein neuer "inactive" key erzeugt und bestätigt werden

                final IdentityPublicKey recipientsKey = plaintextMsg.getSender(); // on inbound our recipient is the sender of the message
                final KeyAgreementPublicKey sessionKey = plaintextMsg.getSessionKey();

                LOG.trace("[{} <= {}] Received key exchange message", () -> ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().toString().substring(0, 4), () -> recipientsKey.toString().substring(0, 4));

                ArmHandlerUtil.computeInactiveAgreementIfNeeded(crypto, session);
                session.getCurrentInactiveAgreement().computeOnCondition(Objects::nonNull,
                        agreement -> agreement.toBuilder()
                                .setRecipientsKeyAgreementKey(Optional.of(sessionKey))
                                .setAgreementId(Optional.of(AgreementId.of(agreement.getKeyPair().getPublicKey(), sessionKey)))
                                .build());
                doKeyExchange(session, ctx, sender, recipientsKey);

                // encrypt message with long time key
                FutureCombiner.getInstance().add(ArmHandlerUtil.sendAck(crypto, ctx, sender, recipientsKey, session)).combine(future);
            }
            catch (final Exception e) {
                future.completeExceptionally(new CryptoException(e));
            }
        });
    }

    /**
     * Checks if the currently active agreement must be renewed and does so.
     *
     * @param ctx           the handler context
     * @param session       the respective session
     * @param recipient     the recipient of the agreement
     * @param recipientsKey the public key of the recipient
     */
    private void checkForRenewAgreement(final ChannelHandlerContext ctx,
                                        final Session session,
                                        final Address recipient,
                                        final IdentityPublicKey recipientsKey) {
        // remove stale agreement
        final Optional<Agreement> agreement = session.getCurrentActiveAgreement().computeOnCondition(a -> a != null && a.isStale(), a -> {
            ctx.fireUserEventTriggered(new MigrationEvent(LongTimeEncryptionEvent.of(Peer.of(recipientsKey))));

            return null;
        });

        //TODO: Bei stale agreements passiert nichts
        //TODO: Vielleicht kann man auch nur über diese Methode PFS anstoßen? Würde alles extrem vereinfachen
        if (agreement.isPresent() &&
                agreement.get().isRenewable()
                && session.getLastRenewAttemptAt().getAndUpdate(updateLastModificationTime) < System.currentTimeMillis() - retryInterval.toMillis()) {
            final Agreement inactiveAgreement = ArmHandlerUtil.computeInactiveAgreementIfNeeded(crypto, session);

            LOG.trace("[{} => {}] Send key exchange message, do to renewable", () -> ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().toString().substring(0, 4), () -> recipientsKey.toString().substring(0, 4));
            ArmHandlerUtil.sendKeyExchangeMsg(crypto, ctx, session, inactiveAgreement, recipient, recipientsKey);
        }
    }

    @Override
    protected void matchedOutbound(final ChannelHandlerContext ctx,
                                   final Address recipient,
                                   final FullReadMessage<?> msg,
                                   final CompletableFuture<Void> future) throws Exception {
        if (msg.getRecipient() == null) {
            FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new MigrationOutboundMessage<>((Object) msg, recipient)))).combine(future);
            return;
        }

        if (!ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().equals(msg.getSender())
                || ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().equals(msg.getRecipient())) {
            FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new MigrationOutboundMessage<>((Object) msg, recipient)))).combine(future);
            return;
        }

        filteredOutbound(ctx, recipient, msg, future);
    }

    @Override
    protected void matchedInbound(final ChannelHandlerContext ctx,
                                  final Address sender,
                                  final ArmedMessage msg,
                                  final CompletableFuture<Void> future) throws Exception {
        if (!ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().equals(msg.getRecipient())
                || ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().equals(msg.getSender())) {
            ctx.fireChannelRead(new MigrationInboundMessage<>((Object) msg, sender, future));
            return;
        }

        filteredInbound(ctx, sender, msg, future);
    }
}
