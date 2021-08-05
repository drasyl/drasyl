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

import io.netty.util.ReferenceCounted;
import org.drasyl.channel.MigrationHandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.util.ReferenceCountUtil;

import java.util.concurrent.CompletableFuture;

/**
 * This class provides the functionality to either accept or reject new inbound messages.
 * <p>
 * You should inherit from this class if you would like to implement your own inbound messages
 * filter. Basically you have to implement {@link #accept(MigrationHandlerContext, Address, Object)}
 * to decided whether you want to pass through or drop a message.
 * <p>
 * Furthermore overriding {@link #messageRejected(MigrationHandlerContext, Address, Object,
 * CompletableFuture)} gives you the flexibility to respond to rejected messages.
 * <p>
 * This class will automatically call {@link ReferenceCounted#release()} on rejected messages and
 * complete the corresponding future.
 */
@SuppressWarnings("java:S118")
public abstract class InboundMessageFilter<I, A extends Address> extends SimpleInboundHandler<I, A> {
    @Override
    protected void matchedInbound(final MigrationHandlerContext ctx,
                                  final A sender,
                                  final I msg,
                                  final CompletableFuture<Void> future) throws Exception {
        try {
            if (accept(ctx, sender, msg)) {
                ctx.passInbound(sender, msg, future);
            }
            else {
                messageRejected(ctx, sender, msg, future);
                ReferenceCountUtil.safeRelease(msg);
                future.complete(null);
            }
        }
        catch (final InboundFilterException e) {
            ReferenceCountUtil.safeRelease(msg);
            throw e;
        }
        catch (final Exception e) {
            ReferenceCountUtil.safeRelease(msg);
            throw new InboundFilterException(e);
        }
    }

    /**
     * This method is called for every inbound message.
     *
     * @return {@code true} if message should be passed through. {@code false} is message should be
     * dropped.
     */
    @SuppressWarnings("java:S112")
    protected abstract boolean accept(MigrationHandlerContext ctx,
                                      final A sender,
                                      final I msg) throws Exception;

    /**
     * This method is called if {@code msg} gets rejected by {@link #accept(MigrationHandlerContext,
     * Address, Object)}. You should override it if you would like to handle (e.g. respond to)
     * rejected messages.
     */
    @SuppressWarnings({ "unused", "RedundantThrows", "java:S112" })
    protected void messageRejected(final MigrationHandlerContext ctx,
                                   final A sender,
                                   final I msg,
                                   final CompletableFuture<Void> future) throws Exception {
        // do nothing
    }
}
