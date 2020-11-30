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

package org.drasyl.handler;

import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.SignedMessage;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.util.concurrent.CompletableFuture;

/**
 * Acts as a guard for in- and outbound messages. <br> Signs automatically outbound messages. <br>
 * Validates automatically inbound messages and drops them, iff a signature is invalid or if a
 * validation is impossible, e.g. the public key of the sender is unknown. In this case, drop
 * information is written to the log.
 */
public class SignatureHandler extends SimpleDuplexHandler<Message, Message, Address> {
    public static final SignatureHandler INSTANCE = new SignatureHandler();
    public static final String SIGNATURE_HANDLER = "SIGNATURE_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(SignatureHandler.class);

    private SignatureHandler() {
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final Message msg,
                               final CompletableFuture<Void> future) {
        if (!ctx.identity().getPublicKey().equals(msg.getRecipient())) {
            // passthrough all messages not addressed to us
            ctx.fireRead(sender, msg, future);
        }
        else if (!(msg instanceof SignedMessage)) {
            LOG.debug("Dropped not signed message `{}`", msg);
            future.completeExceptionally(new Exception("Dropped not signed message"));
        }
        else {
            inboundSafeguards(ctx, sender, (SignedMessage) msg, future);
        }
    }

    /**
     * Only passthroughs messages with valid signature.
     *
     * @param ctx           handler context
     * @param sender        message's sender
     * @param signedMessage the signed message
     * @param future        message future
     */
    private void inboundSafeguards(final HandlerContext ctx,
                                   final Address sender,
                                   final SignedMessage signedMessage,
                                   final CompletableFuture<Void> future) {
        if (signedMessage.getSignature() == null) {
            LOG.debug("Signed message `{}` has no signature.", signedMessage);
            future.completeExceptionally(new Exception("Signed message has no signature."));
        }
        else {
            final PublicKey publicKey = extractPublicKey(signedMessage);

            if (publicKey != null) {
                if (Crypto.verifySignature(publicKey, signedMessage)) {
                    ctx.fireRead(sender, signedMessage.getPayload(), future);
                }
                else {
                    LOG.debug("Signature of the message `{}` was invalid.", signedMessage);
                    future.completeExceptionally(new Exception("Signature was invalid."));
                }
            }
            else {
                LOG.debug("Could not find a matching public key for the message `{}`.", signedMessage);
                future.completeExceptionally(new Exception("Could not find a matching public key for the message."));
            }
        }
    }

    /**
     * Extract the public key of a message.
     *
     * @param msg message for which the public key is to be determined
     * @return public key or zero if it could not be determined
     */
    private static PublicKey extractPublicKey(final SignedMessage msg) {
        final CompressedPublicKey compressedPublicKey = msg.getSender();

        try {
            if (compressedPublicKey != null) {
                return compressedPublicKey.toUncompressedKey();
            }
        }
        catch (final CryptoException e) {
            LOG.debug("Can't decompress public key due to the following error: ", e);
        }

        return null;
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final Address recipient,
                                final Message msg,
                                final CompletableFuture<Void> future) {
        if (!ctx.identity().getPublicKey().equals(msg.getSender())) {
            // passthrough all messages not addressed from us
            ctx.write(recipient, msg, future);
        }
        else {
            try {
                final SignedMessage signedMessage = new SignedMessage(ctx.config().getNetworkId(), ctx.identity().getPublicKey(), ctx.identity().getProofOfWork(), msg.getRecipient(), msg);
                Crypto.sign(ctx.identity().getPrivateKey().toUncompressedKey(), signedMessage);

                ctx.write(recipient, signedMessage, future);
                LOG.trace("Signed the message `{}`", msg);
            }
            catch (final CryptoException e) {
                future.completeExceptionally(e);
                LOG.debug("Can't sign message `{}` due to the following error: ", msg, e);
            }
        }
    }
}