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
package org.drasyl.handler.crypto;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;

import java.time.Duration;

/**
 * Arms (encrypt) outbound and disarms (decrypt) inbound messages. Messages that could not be
 * (dis-)armed are dropped. Uses only long time keys.
 */
@SuppressWarnings("java:S110")
public class LongTimeArmHandler extends AbstractArmHandler {
    protected LongTimeArmHandler(final Crypto crypto,
                                 final Identity identity,
                                 final IdentityPublicKey peerIdentity,
                                 final Session session) {
        super(crypto, identity, peerIdentity, session);
    }

    public LongTimeArmHandler(final Crypto crypto,
                              final Duration expireAfter,
                              final int maxAgreements,
                              final Identity identity,
                              final IdentityPublicKey peerIdentity) throws CryptoException {
        super(crypto, expireAfter, maxAgreements, identity, peerIdentity);
    }

    @Override
    protected void inboundArmMessage(final ChannelHandlerContext ctx, final Object msg) {
        // NO-OP
    }

    @Override
    protected void onNonAgreement(final ChannelHandlerContext ctx) {
        // NO-OP
    }

    @Override
    protected void removeStaleAgreement(final ChannelHandlerContext ctx,
                                        final Agreement agreement) {
        // NO-OP
    }

    @Override
    protected Agreement getAgreement(final AgreementId agreementId) {
        return session.getLongTimeAgreement();
    }
}
