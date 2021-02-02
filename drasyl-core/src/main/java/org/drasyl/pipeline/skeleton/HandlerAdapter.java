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
    public void handlerAdded(final HandlerContext ctx) {
        // Do nothing
    }

    /**
     * Do nothing by default, sub-classes may override this method.
     */
    @Override
    public void handlerRemoved(final HandlerContext ctx) {
        // Do nothing
    }

    @Skip
    @Override
    public void read(final HandlerContext ctx,
                     final Address sender,
                     final Object msg,
                     final CompletableFuture<Void> future) {
        ctx.fireRead(sender, msg, future);
    }

    @Skip
    @Override
    public void eventTriggered(final HandlerContext ctx,
                               final Event event,
                               final CompletableFuture<Void> future) {
        ctx.fireEventTriggered(event, future);
    }

    @Skip
    @Override
    public void exceptionCaught(final HandlerContext ctx, final Exception cause) {
        ctx.fireExceptionCaught(cause);
    }

    @Skip
    @Override
    public void write(final HandlerContext ctx,
                      final Address recipient,
                      final Object msg,
                      final CompletableFuture<Void> future) {
        ctx.write(recipient, msg, future);
    }
}
