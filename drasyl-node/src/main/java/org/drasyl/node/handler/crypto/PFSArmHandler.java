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
package org.drasyl.node.handler.crypto;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.KeyAgreementPublicKey;
import org.drasyl.identity.KeyAgreementSecretKey;
import org.drasyl.identity.KeyPair;
import org.drasyl.node.event.LongTimeEncryptionEvent;
import org.drasyl.node.event.Peer;
import org.drasyl.node.event.PerfectForwardSecrecyEncryptionEvent;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Arms (encrypt) outbound and disarms (decrypt) inbound messages. Messages that could not be
 * (dis-)armed are dropped. Does key-exchange with ephemeral keys to achieve PFS.
 */
@SuppressWarnings({ "java:S110", "java:S107" })
public class PFSArmHandler extends AbstractArmHandler {
    private static final Logger LOG = LoggerFactory.getLogger(PFSArmHandler.class);
    private final Duration retryInterval;
    private final LongSupplier expireProvider;
    private State state;

    protected PFSArmHandler(final Crypto crypto,
                            final Identity identity,
                            final IdentityPublicKey peerIdentity,
                            final Session session,
                            final LongSupplier expireProvider,
                            final Duration retryInterval,
                            final State state) {
        super(crypto, identity, peerIdentity, session);
        this.expireProvider = expireProvider;
        this.retryInterval = retryInterval;
        this.state = state;
    }

    public PFSArmHandler(final Crypto crypto,
                         final Duration expireAfter,
                         final Duration retryInterval,
                         final int maxAgreements,
                         final Identity identity,
                         final IdentityPublicKey peerIdentity) throws CryptoException {
        super(crypto, expireAfter, maxAgreements, identity, peerIdentity);
        this.retryInterval = retryInterval;
        this.expireProvider = () -> System.currentTimeMillis() + expireAfter.toMillis();
        this.state = State.LONG_TIME;
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final ByteBuf msg,
                          final List<Object> out) throws Exception {
        ctx.executor().execute(() -> checkForRenewAgreement(ctx));

        super.encode(ctx, msg, out);
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ArmHeader msg,
                          final List<Object> out) throws Exception {
        ctx.executor().execute(() -> checkForRenewAgreement(ctx));

        receivedAck(ctx, msg.getAgreementId());
        super.decode(ctx, msg, out);
    }

    @Override
    protected void inboundArmMessage(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof AcknowledgementMessage) {
            receivedAck(ctx, ((AcknowledgementMessage) msg).getAgreementId());
        }
        else if (msg instanceof KeyExchangeMessage) {
            receivedKeyExchangeMessage(ctx, ((KeyExchangeMessage) msg).getSessionKey());
        }
    }

    @Override
    protected void onNonAgreement(final ChannelHandlerContext ctx) {
        // on unknown agreementId we want to send a new key exchange message, may be we're crashed and the recipient node sends us an old agreement
        ctx.executor().execute(() -> doKeyExchange(ctx));
    }

    /**
     * Checks if the currently active agreement must be renewed and does so.
     *
     * @param ctx the handler context
     */
    private void checkForRenewAgreement(final ChannelHandlerContext ctx) {
        final Agreement agreement = session.getCurrentActiveAgreement().computeOnCondition(a -> a != null && a.isStale(), a -> {
            session.getInitializedAgreements().remove(a.getAgreementId());
            if (state == State.PFS) {
                ctx.fireUserEventTriggered(LongTimeEncryptionEvent.of(Peer.of(peerIdentity)));
                state = State.LONG_TIME;
            }

            return null;
        }).orElse(null);

        if (agreement != null && agreement.isRenewable() && session.getLastRenewAttemptAt() < System.currentTimeMillis() - retryInterval.toMillis()) {
            session.setLastRenewAttemptAt(System.currentTimeMillis());
            doKeyExchange(ctx);
        }
    }

    /**
     * Handles a received {@link KeyExchangeMessage} message and creates/initializes the
     * corresponding {@link PendingAgreement} object.
     *
     * @param ctx        the handler context
     * @param sessionKey the session key of the peer
     */
    private void receivedKeyExchangeMessage(final ChannelHandlerContext ctx,
                                            final KeyAgreementPublicKey sessionKey) {
        ctx.executor().execute(() -> {
            LOG.trace("[{}] Received key exchange message", ctx.channel()::id);
            if (!sessionKey.equals(peerIdentity.getLongTimeKeyAgreementKey())) {
                computeInactiveAgreementIfNeeded().setRecipientsKeyAgreementKey(sessionKey);

                doKeyExchange(ctx);
                sendAck(ctx);
            }
            else {
                LOG.debug("[{}] Received key exchange message with long time key. This is invalid and may be a sign for an MITM attack.", ctx.channel()::id);
            }
        });
    }

    /**
     * Handles a received acknowledgement message and does a full initialization of the
     * corresponding agreement.
     *
     * @param ctx         the handler context
     * @param agreementId the agreement id
     */
    private void receivedAck(final ChannelHandlerContext ctx,
                             final AgreementId agreementId) {
        final PendingAgreement pendingAgreement = session.getCurrentInactiveAgreement().computeOnCondition(a -> a != null && Objects.equals(agreementId, a.getAgreementId()), a -> {
            try {
                LOG.trace("[{}] Received ack message", ctx.channel()::id);

                final Agreement agreement = a.buildAgreement(crypto, expireProvider.getAsLong());

                session.getInitializedAgreements().put(agreementId, agreement);
                session.getCurrentActiveAgreement().computeOnCondition(c -> true, f -> agreement);
                session.setLastRenewAttemptAt(System.currentTimeMillis());

                if (state == State.LONG_TIME) {
                    ctx.fireUserEventTriggered(PerfectForwardSecrecyEncryptionEvent.of(Peer.of(this.peerIdentity)));
                    state = State.PFS;
                }

                return null;
            }
            catch (final CryptoException e) {
                LOG.debug("Can't compute new agreement: ", e);
                return a;
            }
        }).orElse(computeInactiveAgreementIfNeeded());

        // We received an ACK for an unknown agreement
        if (!Objects.equals(agreementId, pendingAgreement.getAgreementId())) {
            doKeyExchange(ctx);
        }
    }

    /**
     * Sends an ack message to the peer for the current pending agreement.
     *
     * @param ctx the handler context
     */
    private void sendAck(final ChannelHandlerContext ctx) {
        final PendingAgreement pendingAgreement = session.getCurrentInactiveAgreement().getValue().orElse(null);

        if (pendingAgreement != null) {
            try {
                final ByteBuf byteBuf = ctx.alloc().buffer();
                AcknowledgementMessage.of(pendingAgreement.getAgreementId()).writeTo(byteBuf);
                ctx.writeAndFlush(arm(ctx, session.getLongTimeAgreement(), byteBuf));
                byteBuf.release();
                LOG.trace("[{}] Send ack message for session {}", ctx.channel()::id, pendingAgreement::getAgreementId);
            }
            catch (final CryptoException e) {
                LOG.trace("[{}] Error on sending ack message for session {}: {}", ctx.channel()::id, pendingAgreement::getAgreementId, e::toString);
            }
        }
    }

    /**
     * Does a key exchange, if the last key exchange is overdue.
     *
     * @param ctx the handler context
     */
    private void doKeyExchange(final ChannelHandlerContext ctx) {
        final PendingAgreement pendingAgreement = computeInactiveAgreementIfNeeded();

        if (session.getLastKeyExchangeAt() < System.currentTimeMillis() - retryInterval.toMillis()) {
            LOG.trace("[{}] Send key exchange message, do to key exchange overdue", ctx.channel()::id);
            try {
                final ByteBuf byteBuf = ctx.alloc().buffer();
                KeyExchangeMessage.of(pendingAgreement.getKeyPair().getPublicKey()).writeTo(byteBuf);
                ctx.writeAndFlush(arm(ctx, session.getLongTimeAgreement(), byteBuf));
                byteBuf.release();
            }
            catch (final CryptoException e) {
                LOG.debug("Can't arm key exchange message: ", e);
            }

            session.setLastKeyExchangeAt(System.currentTimeMillis());
        }
    }

    /**
     * Computes a new inactive agreement if not already present.
     *
     * @return an inactive agreement
     */
    private PendingAgreement computeInactiveAgreementIfNeeded() {
        return session.getCurrentInactiveAgreement().computeIfAbsent(() -> {
            // here we compute a new ephemeral key pair for key agreement
            try {
                final KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> keyPair = crypto.generateEphemeralKeyPair();

                return new PendingAgreement(keyPair);
            }
            catch (final CryptoException e) {
                LOG.debug("Could not generate ephemeral key: ", e);
                return null;
            }
        });
    }

    protected void removeStaleAgreement(final ChannelHandlerContext ctx,
                                        final Agreement agreement) {
        if (agreement.isStale()) {
            session.getInitializedAgreements().remove(agreement.getAgreementId());
            session.getCurrentActiveAgreement().computeOnCondition(a -> a != null && a.getAgreementId().equals(agreement.getAgreementId()), a -> {
                if (state == State.PFS) {
                    ctx.fireUserEventTriggered(LongTimeEncryptionEvent.of(Peer.of(this.peerIdentity)));
                    state = State.LONG_TIME;
                }

                return null;
            });
        }
    }

    protected Agreement getAgreement(final AgreementId agreementId) {
        // check for default agreement
        if (Objects.equals(agreementId, session.getLongTimeAgreement().getAgreementId())) {
            return session.getLongTimeAgreement();
        }
        // check for other agreement
        else {
            return session.getInitializedAgreements().get(agreementId);
        }
    }

    protected enum State {
        LONG_TIME,
        PFS
    }
}
