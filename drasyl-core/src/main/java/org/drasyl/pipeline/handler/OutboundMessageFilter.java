/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.pipeline.handler;

import io.netty.util.ReferenceCounted;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.util.ReferenceCountUtil;

import java.util.concurrent.CompletableFuture;

/**
 * This class provides the functionality to either accept or reject new outbound messages.
 * <p>
 * You should inherit from this class if you would like to implement your own outbound messages
 * filter. Basically you have to implement {@link #accept(HandlerContext, Address, Object)} to
 * decided whether you want to pass through or drop a message.
 * <p>
 * Furthermore overriding {@link #messageRejected(HandlerContext, Address, Object,
 * CompletableFuture)} gives you the flexibility to respond to rejected messages.
 * <p>
 * This class will automatically call {@link ReferenceCounted#release()} on rejected messages.
 */
@SuppressWarnings("java:S118")
public abstract class OutboundMessageFilter<O, A extends Address> extends SimpleOutboundHandler<O, A> {
    @Override
    protected void matchedOutbound(final HandlerContext ctx,
                                   final A recipient,
                                   final O msg,
                                   final CompletableFuture<Void> future) throws Exception {
        try {
            if (accept(ctx, recipient, msg)) {
                ctx.passOutbound(recipient, msg, future);
            }
            else {
                messageRejected(ctx, recipient, msg, future);
                ReferenceCountUtil.safeRelease(msg);
                future.complete(null);
            }
        }
        catch (final Exception e) {
            ReferenceCountUtil.safeRelease(msg);
            future.completeExceptionally(new Exception("Unable to filter message:", e));
        }
    }

    /**
     * This method is called for every outbound message.
     *
     * @return {@code true} if message should be passed through. {@code false} is message should be
     * dropped.
     */
    @SuppressWarnings("java:S112")
    protected abstract boolean accept(HandlerContext ctx,
                                      final A sender,
                                      final O msg) throws Exception;

    /**
     * This method is called if {@code msg} gets rejected by {@link #accept(HandlerContext, Address,
     * Object)}. You should override it if you would like to handle (e.g. respond to) rejected
     * messages.
     */
    @SuppressWarnings("unused")
    protected void messageRejected(final HandlerContext ctx,
                                   final A sender,
                                   final O msg,
                                   final CompletableFuture<Void> future) {
        // do nothing
    }
}
