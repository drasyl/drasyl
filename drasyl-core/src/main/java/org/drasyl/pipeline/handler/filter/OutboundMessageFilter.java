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
package org.drasyl.pipeline.handler.filter;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCounted;
import org.drasyl.channel.MigrationOutboundMessage;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.ReferenceCountUtil;

import java.util.concurrent.CompletableFuture;

/**
 * This class provides the functionality to either accept or reject new outbound messages.
 * <p>
 * You should inherit from this class if you would like to implement your own outbound messages
 * filter. Basically you have to implement {@link #accept(ChannelHandlerContext, Address, Object)}
 * to decided whether you want to pass through or drop a message.
 * <p>
 * Furthermore overriding {@link #messageRejected(ChannelHandlerContext, Address, Object,
 * CompletableFuture)} gives you the flexibility to respond to rejected messages.
 * <p>
 * This class will automatically call {@link ReferenceCounted#release()} on rejected messages and
 * complete the corresponding future.
 */
@SuppressWarnings("java:S118")
public abstract class OutboundMessageFilter<O, A extends Address> extends SimpleOutboundHandler<O, A> {
    @Override
    protected void matchedOutbound(final ChannelHandlerContext ctx,
                                   final A recipient,
                                   final O msg,
                                   final CompletableFuture<Void> future) throws Exception {
        try {
            if (accept(ctx, recipient, msg)) {
                FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new MigrationOutboundMessage<>((Object) msg, (Address) recipient)))).combine(future);
            }
            else {
                messageRejected(ctx, recipient, msg, future);
                ReferenceCountUtil.safeRelease(msg);
                future.complete(null);
            }
        }
        catch (final OutboundFilterException e) {
            ReferenceCountUtil.safeRelease(msg);
            throw e;
        }
        catch (final Exception e) {
            ReferenceCountUtil.safeRelease(msg);
            throw new OutboundFilterException(e);
        }
    }

    /**
     * This method is called for every outbound message.
     *
     * @return {@code true} if message should be passed through. {@code false} is message should be
     * dropped.
     */
    @SuppressWarnings("java:S112")
    protected abstract boolean accept(ChannelHandlerContext ctx,
                                      final A sender,
                                      final O msg) throws Exception;

    /**
     * This method is called if {@code msg} gets rejected by {@link #accept(ChannelHandlerContext,
     * Address, Object)}. You should override it if you would like to handle (e.g. respond to)
     * rejected messages.
     */
    @SuppressWarnings({ "unused", "RedundantThrows", "java:S112" })
    protected void messageRejected(final ChannelHandlerContext ctx,
                                   final A sender,
                                   final O msg,
                                   final CompletableFuture<Void> future) throws Exception {
        // do nothing
    }
}
