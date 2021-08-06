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
package org.drasyl.pipeline.skeleton;

import org.drasyl.channel.MigrationHandlerContext;
import org.drasyl.channel.MigrationInboundMessage;
import org.drasyl.channel.MigrationOutboundMessage;
import org.drasyl.pipeline.address.Address;
import org.drasyl.remote.protocol.RemoteMessage;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;

import java.util.concurrent.CompletableFuture;

import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;

/**
 * This handler skips {@link RemoteMessage}s that has no recipient or sender or is addresses to a
 * loopback (to us from us).
 *
 * @param <I> type of inbound messages
 * @param <O> type of outbound messages
 * @param <A> type of address
 */
@SuppressWarnings({ "java:S110" })
public abstract class SimpleDuplexRemoteMessageSkipLoopbackHandler<I extends RemoteMessage, O extends RemoteMessage, A extends Address> extends SimpleDuplexHandler<I, O, A> {
    private final boolean skipNullAddresses;

    /**
     * By default also messages that has no sender or recipient are skipped.
     */
    protected SimpleDuplexRemoteMessageSkipLoopbackHandler() {
        this(true);
    }

    /**
     * @param skipNullAddresses if the message should be skipped if the recipient or sender is
     *                          {@code null}
     */
    protected SimpleDuplexRemoteMessageSkipLoopbackHandler(final boolean skipNullAddresses) {
        this.skipNullAddresses = skipNullAddresses;
    }

    @Override
    protected void matchedOutbound(final MigrationHandlerContext ctx,
                                   final A recipient,
                                   final O msg,
                                   final CompletableFuture<Void> future) throws Exception {
        if (skipNullAddresses && msg.getRecipient() == null) {
            FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new MigrationOutboundMessage<>((Object) msg, (Address) recipient)))).combine(future);
            return;
        }

        if (!ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().equals(msg.getSender())
                || ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().equals(msg.getRecipient())) {
            FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new MigrationOutboundMessage<>((Object) msg, (Address) recipient)))).combine(future);
            return;
        }

        filteredOutbound(ctx, recipient, msg, future);
    }

    /**
     * This method gets called when the messages is not address to a loopback (from us to us).
     *
     * @param ctx       the handler context
     * @param recipient the recipient of the message
     * @param msg       the message
     * @param future    the future to complete
     * @throws Exception if any error occurs
     */
    protected abstract void filteredOutbound(final MigrationHandlerContext ctx,
                                             final A recipient,
                                             final O msg,
                                             final CompletableFuture<Void> future) throws Exception;

    @Override
    protected void matchedInbound(final MigrationHandlerContext ctx,
                                  final A sender,
                                  final I msg,
                                  final CompletableFuture<Void> future) throws Exception {
        if (!ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().equals(msg.getRecipient())
                || ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().equals(msg.getSender())) {
            ctx.fireChannelRead(new MigrationInboundMessage<>((Object) msg, (Address) sender, future));
            return;
        }

        filteredInbound(ctx, sender, msg, future);
    }

    /**
     * This method gets called when the messages is not address to a loopback (from us to us).
     *
     * @param ctx    the handler context
     * @param sender the sender of the message
     * @param msg    the message
     * @param future the future to complete
     * @throws Exception if any error occurs
     */
    protected abstract void filteredInbound(final MigrationHandlerContext ctx,
                                            final A sender,
                                            final I msg,
                                            final CompletableFuture<Void> future) throws Exception;
}
