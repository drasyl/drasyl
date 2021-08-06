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

import io.netty.channel.ChannelHandler;
import org.drasyl.channel.MigrationHandlerContext;
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.handler.filter.InboundMessageFilter;
import org.drasyl.remote.protocol.RemoteMessage;

import java.util.concurrent.CompletableFuture;

import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;
import static org.drasyl.identity.IdentityManager.POW_DIFFICULTY;

/**
 * This handler filters out all messages received with invalid proof of work.
 */
@SuppressWarnings("java:S110")
@ChannelHandler.Sharable
@Stateless
public final class InvalidProofOfWorkFilter extends InboundMessageFilter<RemoteMessage, Address> {
    public static final InvalidProofOfWorkFilter INSTANCE = new InvalidProofOfWorkFilter();

    private InvalidProofOfWorkFilter() {
        // singleton
    }

    @Override
    protected boolean accept(final MigrationHandlerContext ctx,
                             final Address sender,
                             final RemoteMessage msg) throws Exception {
        return !ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().equals(msg.getRecipient()) || msg.getProofOfWork().isValid(msg.getSender(), POW_DIFFICULTY);
    }

    @SuppressWarnings("java:S112")
    @Override
    protected void messageRejected(final MigrationHandlerContext ctx,
                                   final Address sender,
                                   final RemoteMessage msg,
                                   final CompletableFuture<Void> future) throws Exception {
        throw new Exception("Message `" + msg.getNonce() + "` with invalid proof of work dropped.");
    }
}
