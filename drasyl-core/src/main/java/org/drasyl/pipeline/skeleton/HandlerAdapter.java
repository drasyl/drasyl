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

import org.drasyl.event.Event;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Skip;
import org.drasyl.pipeline.address.Address;

import java.util.concurrent.CompletableFuture;

/**
 * Skeleton implementation of a {@link Handler}.
 */
public class HandlerAdapter implements Handler {
    /**
     * Do nothing by default, sub-classes may override this method.
     */
    @Override
    public void onAdded(final HandlerContext ctx) {
        // Do nothing
    }

    /**
     * Do nothing by default, sub-classes may override this method.
     */
    @Override
    public void onRemoved(final HandlerContext ctx) {
        // Do nothing
    }

    @Skip
    @Override
    public void onInbound(final HandlerContext ctx,
                          final Address sender,
                          final Object msg,
                          final CompletableFuture<Void> future) throws Exception {
        ctx.passInbound(sender, msg, future);
    }

    @Skip
    @Override
    public void onEvent(final HandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        ctx.passEvent(event, future);
    }

    @Skip
    @Override
    public void onException(final HandlerContext ctx, final Exception cause) {
        ctx.passException(cause);
    }

    @Skip
    @Override
    public void onOutbound(final HandlerContext ctx,
                           final Address recipient,
                           final Object msg,
                           final CompletableFuture<Void> future) throws Exception {
        ctx.passOutbound(recipient, msg, future);
    }
}
