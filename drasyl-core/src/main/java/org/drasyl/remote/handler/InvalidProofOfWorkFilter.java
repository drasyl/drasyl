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

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.remote.protocol.RemoteMessage;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;
import static org.drasyl.identity.IdentityManager.POW_DIFFICULTY;

/**
 * This handler filters out all messages received with invalid proof of work.
 */
@SuppressWarnings("java:S110")
@Sharable
public final class InvalidProofOfWorkFilter extends SimpleChannelInboundHandler<AddressedMessage<?, ?>> {
    private static final Logger LOG = LoggerFactory.getLogger(InvalidProofOfWorkFilter.class);
    public static final InvalidProofOfWorkFilter INSTANCE = new InvalidProofOfWorkFilter();

    private InvalidProofOfWorkFilter() {
        // singleton
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final AddressedMessage<?, ?> msg) throws Exception {
        if (msg.message() instanceof RemoteMessage) {
            final RemoteMessage remoteMsg = (RemoteMessage) msg.message();
            final boolean validProofOfWork = !ctx.channel().attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().equals(remoteMsg.getRecipient()) || remoteMsg.getProofOfWork().isValid(remoteMsg.getSender(), POW_DIFFICULTY);
            if (validProofOfWork) {
                ctx.fireChannelRead(msg);
            }
            else {
                LOG.debug("Message `{}` with invalid proof of work dropped.", remoteMsg::getNonce);
            }
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }
}
