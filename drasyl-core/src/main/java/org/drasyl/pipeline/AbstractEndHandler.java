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

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.pipeline.codec.TypeValidator;

import java.util.concurrent.CompletableFuture;

/**
 * Skeleton implementation for end handler of the {@link Pipeline}.
 */
@SuppressWarnings({ "common-java:DuplicatedBlocks" })
public abstract class AbstractEndHandler extends AbstractHandlerContext implements Handler {
    public AbstractEndHandler(String name,
                              DrasylConfig config,
                              Pipeline pipeline,
                              Scheduler scheduler,
                              Identity identity,
                              TypeValidator validator) {
        super(name, config, pipeline, scheduler, identity, validator);
    }

    @Override
    public Handler handler() {
        return this;
    }

    @Override
    public void handlerAdded(HandlerContext ctx) {
        // skip
    }

    @Override
    public void handlerRemoved(HandlerContext ctx) {
        // skip
    }

    @Override
    public void read(HandlerContext ctx,
                     CompressedPublicKey sender,
                     Object msg,
                     CompletableFuture<Void> future) {
        // skip
        ctx.fireRead(sender, msg, future);
    }

    @Override
    public void eventTriggered(HandlerContext ctx, Event event, CompletableFuture<Void> future) {
        // skip
        ctx.fireEventTriggered(event, future);
    }

    @Override
    public void exceptionCaught(HandlerContext ctx, Exception cause) {
        //skip
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void write(HandlerContext ctx,
                      CompressedPublicKey recipient,
                      Object msg,
                      CompletableFuture<Void> future) {
        // skip
        ctx.write(recipient, msg, future);
    }
}