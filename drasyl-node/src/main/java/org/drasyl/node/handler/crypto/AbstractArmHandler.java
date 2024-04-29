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
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.handler.remote.protocol.InvalidMessageFormatException;
import org.drasyl.handler.remote.protocol.Nonce;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Skeleton handler that arms (encrypt) outbound and disarms (decrypt) inbound messages. Messages
 * that could not be (dis-)armed are dropped.
 */
@UnstableApi
public abstract class AbstractArmHandler extends MessageToMessageCodec<ArmHeader, ByteBuf> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractArmHandler.class);
    protected final Crypto crypto;
    protected final IdentityPublicKey peerIdentity;
    protected final Session session;

    protected AbstractArmHandler(final Crypto crypto,
                                 final IdentityPublicKey peerIdentity,
                                 final Session session) {
        this.crypto = crypto;
        this.session = session;
        this.peerIdentity = peerIdentity;
    }

    protected AbstractArmHandler(final Crypto crypto,
                                 final Duration expireAfter,
                                 final int maxAgreements,
                                 final Identity identity,
                                 final IdentityPublicKey peerIdentity) throws CryptoException {
        this(crypto, peerIdentity, new Session(Agreement.of(
                AgreementId.of(identity.getKeyAgreementPublicKey(), peerIdentity.getLongTimeKeyAgreementKey()),
                crypto.generateSessionKeyPair(identity.getKeyAgreementKeyPair(), peerIdentity.getLongTimeKeyAgreementKey()),
                -1), maxAgreements, expireAfter));
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final ByteBuf msg,
                          final List<Object> out) throws Exception {
        // check for agreement
        Agreement agreement = session.getCurrentActiveAgreement().getValue().orElse(null);

        // if not available or stale, return default agreement
        if (agreement == null || agreement.isStale()) {
            agreement = session.getLongTimeAgreement();

            onNonAgreement(ctx);
        }

        final ByteBuf byteBuf = ArmMessage.fromApplication(msg, ctx.alloc());
        final ArmHeader arm = arm(ctx, agreement, byteBuf);
        byteBuf.release();
        out.add(arm);
        LOG.trace("[{}] Armed msg: {}", ctx.channel()::id, () -> msg);
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ArmHeader msg,
                          final List<Object> out) throws Exception {
        final Agreement agreement = getAgreement(msg.getAgreementId());
        final Object plaintext;

        if (agreement == null) {
            onNonAgreement(ctx);

            LOG.debug("Agreement id `{}` could not be found. Dropped message: {}", msg::getAgreementId, () -> msg);
            return;
        }

        plaintext = unarm(ctx, agreement, msg.getNonce(), msg.content());

        removeStaleAgreement(ctx, agreement);

        if (plaintext instanceof ByteBuf) {
            out.add(plaintext);
            LOG.trace("[{}] Disarmed msg: {}", ctx.channel()::id, () -> msg);
        }
        else {
            inboundArmMessage(ctx, plaintext);
        }
    }

    protected abstract void inboundArmMessage(final ChannelHandlerContext ctx, Object msg);

    protected abstract void onNonAgreement(ChannelHandlerContext ctx);

    protected Object unarm(final ChannelHandlerContext ctx,
                           final Agreement agreement,
                           final Nonce nonce,
                           final ByteBuf byteBuf) throws CryptoException {
        try {
            final byte[] bytes = crypto.decrypt(ByteBufUtil.getBytes(byteBuf), new byte[0], nonce, agreement.getSessionPair());
            return ArmMessage.of(ctx.alloc().buffer(bytes.length).writeBytes(bytes));
        }
        catch (final InvalidMessageFormatException e) {
            throw new CryptoException("Can't unarm message: ", e);
        }
    }

    protected ArmHeader arm(final ChannelHandlerContext ctx,
                            final Agreement agreement,
                            final ByteBuf msg) throws CryptoException {
        final Nonce nonce = Nonce.randomNonce();
        final byte[] bytes = crypto.encrypt(ByteBufUtil.getBytes(msg), new byte[0], nonce, agreement.getSessionPair());
        return ArmHeader.of(
                agreement.getAgreementId(),
                nonce,
                ctx.alloc().buffer(bytes.length).writeBytes(bytes)
        );
    }

    protected abstract void removeStaleAgreement(final ChannelHandlerContext ctx,
                                                 final Agreement agreement);

    protected abstract Agreement getAgreement(final AgreementId agreementId);
}
