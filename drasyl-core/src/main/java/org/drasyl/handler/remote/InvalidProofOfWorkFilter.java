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
package org.drasyl.handler.remote;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;

import static org.drasyl.identity.Identity.POW_DIFFICULTY;

/**
 * This handler filters out all messages received with invalid proof of work.
 */
@SuppressWarnings("java:S110")
@Sharable
public final class InvalidProofOfWorkFilter extends SimpleChannelInboundHandler<InetAddressedMessage<RemoteMessage>> {
    public InvalidProofOfWorkFilter() {
        super(false);
    }

    @Override
    public boolean acceptInboundMessage(final Object msg) throws Exception {
        return msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof RemoteMessage;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final InetAddressedMessage<RemoteMessage> msg) throws InvalidProofOfWorkException {
        final RemoteMessage remoteMsg = msg.content();
        final boolean validProofOfWork = !ctx.channel().localAddress().equals(remoteMsg.getRecipient()) || remoteMsg.getProofOfWork().isValid(remoteMsg.getSender(), POW_DIFFICULTY);
        if (validProofOfWork) {
            ctx.fireChannelRead(msg);
        }
        else {
            msg.release();
            throw new InvalidProofOfWorkException(remoteMsg);
        }
    }

    /**
     * Signals that a message was received with an invalid {@link org.drasyl.identity.ProofOfWork}
     * and was dropped.
     */
    public static class InvalidProofOfWorkException extends Exception {
        public InvalidProofOfWorkException(final RemoteMessage msg) {
            super("Message `" + msg.getNonce() + "` from `" + (msg.getSender() != null ? msg.getSender() : null) + "` with invalid proof of work (" + (msg.getProofOfWork() != null ? msg.getProofOfWork() : null) + ") dropped.");
        }
    }
}
