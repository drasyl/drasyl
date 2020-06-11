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
package org.drasyl.peer.connection.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.SignedMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;

import static org.drasyl.peer.connection.server.NodeServerChannelGroup.ATTRIBUTE_IDENTITY;

/**
 * Acts as a guard for in- and outbound messages. <br> Signs automatically outbound messages. <br>
 * Validates automatically inbound messages and drops them, iff a signature is invalid or if a
 * validation is impossible, e.g. the public key of the sender is unknown. In this case, drop
 * information is written to the log.
 */
public class SignatureHandler extends SimpleChannelDuplexHandler<Message, Message> {
    public static final String SIGNATURE_HANDLER = "signatureHandler";
    private static final Logger LOG = LoggerFactory.getLogger(SignatureHandler.class);
    private final Identity identity;

    public SignatureHandler(Identity identity) {
        super(true, true, false);
        this.identity = identity;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx,
                                Message msg) {
        if (!(msg instanceof SignedMessage) || ((SignedMessage) msg).getKid() == null || ((SignedMessage) msg).getSignature() == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("[{}]: Dropped not signed message `{}`", ctx.channel().id().asShortText(), msg);
            }
            return;
        }

        inboundSafeguards(ctx, (SignedMessage) msg);
    }

    /**
     * Only passthroughs message, if it can be validated and no MITM is detected.
     *
     * @param ctx           channel handler context
     * @param signedMessage the signed message
     */
    private void inboundSafeguards(ChannelHandlerContext ctx, SignedMessage signedMessage) {
        PublicKey publicKey = extractPublicKey(signedMessage);

        // Prevent MITM after JoinMessage
        if (ctx.channel().hasAttr(ATTRIBUTE_IDENTITY)) {
            CompressedPublicKey channelKey = ctx.channel().attr(ATTRIBUTE_IDENTITY).get().getPublicKey();

            if (!channelKey.equals(signedMessage.getKid())) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("[{}]: Sender public key `{}`, and the associated channel public key `{}` are not identical. Maybe a MITM attack. Message `{}` was dropped.", ctx.channel().id().asShortText(), signedMessage.getKid(), channelKey, signedMessage);
                }

                return;
            }
        }

        if (publicKey != null) {
            if (Crypto.verifySignature(publicKey, signedMessage)) {
                ctx.fireChannelRead(signedMessage.getPayload());
            }
            else {
                StatusMessage exceptionMessage = new StatusMessage(StatusMessage.Code.STATUS_INVALID_SIGNATURE, signedMessage.getPayload().getId());
                channelWrite0(ctx, exceptionMessage, ctx.channel().newPromise());

                if (LOG.isInfoEnabled()) {
                    LOG.info("[{}]: Signature of the message `{}` was invalid.", ctx.channel().id().asShortText(), signedMessage);
                }
            }
        }
        else if (LOG.isInfoEnabled()) {
            LOG.info("[{}]: Could not find a matching public key for the message `{}`.", ctx.channel().id().asShortText(), signedMessage);
        }
    }

    /**
     * Extract the public key of a message.
     *
     * @param msg message for which the public key is to be determined
     * @return public key or zero if it could not be determined
     */
    private static PublicKey extractPublicKey(SignedMessage msg) {
        CompressedPublicKey compressedPublicKey = msg.getKid();

        try {
            if (compressedPublicKey != null) {
                return compressedPublicKey.toUncompressedKey();
            }
        }
        catch (CryptoException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Can't decompress public key due to the following error: ", e);
            }
        }

        return null;
    }

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx,
                                 Message msg, ChannelPromise promise) {
        try {
            SignedMessage signedMessage = new SignedMessage(msg, identity.getPublicKey());
            Crypto.sign(identity.getPrivateKey().toUncompressedKey(), signedMessage);

            ctx.write(signedMessage, promise);

            if (LOG.isTraceEnabled()) {
                LOG.trace("[{}]: Signed the message `{}`", ctx.channel().id().asShortText(), msg);
            }
        }
        catch (CryptoException e) {
            promise.setFailure(e);
            if (LOG.isDebugEnabled()) {
                LOG.debug("[{}]: Can't sign message `{}` due to the following error: ", ctx.channel().id().asShortText(), msg, e);
            }
        }
    }
}
