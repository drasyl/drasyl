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
package org.drasyl.remote.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.identity.Identity;
import org.drasyl.remote.protocol.RemoteMessage;

import static java.util.Objects.requireNonNull;
import static org.drasyl.identity.IdentityManager.POW_DIFFICULTY;

/**
 * This handler filters out all messages received with invalid proof of work.
 */
@SuppressWarnings("java:S110")
public final class InvalidProofOfWorkFilter extends SimpleChannelInboundHandler<AddressedMessage<?, ?>> {
    private final Identity identity;

    public InvalidProofOfWorkFilter(final Identity identity) {
        this.identity = requireNonNull(identity);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final AddressedMessage<?, ?> msg) throws InvalidProofOfWorkException {
        if (msg.message() instanceof RemoteMessage) {
            final RemoteMessage remoteMsg = (RemoteMessage) msg.message();
            final boolean validProofOfWork = !identity.getIdentityPublicKey().equals(remoteMsg.getRecipient()) || remoteMsg.getProofOfWork().isValid(remoteMsg.getSender(), POW_DIFFICULTY);
            if (validProofOfWork) {
                ctx.fireChannelRead(msg.retain());
            }
            else {
                throw new InvalidProofOfWorkException(remoteMsg);
            }
        }
        else {
            ctx.fireChannelRead(msg.retain());
        }
    }

    /**
     * Signals that a message was received with an invalid {@link org.drasyl.identity.ProofOfWork}
     * and was dropped.
     */
    public static class InvalidProofOfWorkException extends Exception {
        public InvalidProofOfWorkException(final RemoteMessage msg) {
            super("Message `" + msg.getNonce() + "` with invalid proof of work dropped.");
        }
    }
}
