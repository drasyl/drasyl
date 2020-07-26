/*
 * Copyright (c) 2020.
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
package org.drasyl.pipeline;

import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;

import java.util.concurrent.CompletableFuture;

/**
 * Skeleton implementation of a {@link Handler}.
 */
public class HandlerAdapter implements Handler {
    /**
     * Do nothing by default, sub-classes may override this method.
     */
    @Override
    public void handlerAdded(HandlerContext ctx) {
        // Do nothing
    }

    /**
     * Do nothing by default, sub-classes may override this method.
     */
    @Override
    public void handlerRemoved(HandlerContext ctx) {
        // Do nothing
    }

    @Override
    public void read(HandlerContext ctx, CompressedPublicKey sender, Object msg) {
        ctx.fireRead(sender, msg);
    }

    @Override
    public void eventTriggered(HandlerContext ctx, Event event) {
        ctx.fireEventTriggered(event);
    }

    @Override
    public void exceptionCaught(HandlerContext ctx, Exception cause) {
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void write(HandlerContext ctx,
                      CompressedPublicKey recipient,
                      Object msg,
                      CompletableFuture<Void> future) {
        ctx.write(recipient, msg, future);
    }
}
