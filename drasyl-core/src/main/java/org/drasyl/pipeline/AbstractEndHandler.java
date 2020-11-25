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
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.TypeValidator;

import java.util.concurrent.CompletableFuture;

/**
 * Skeleton implementation for end handler of the {@link Pipeline}.
 */
@SuppressWarnings({ "common-java:DuplicatedBlocks" })
abstract class AbstractEndHandler extends AbstractHandlerContext implements Handler {
    protected AbstractEndHandler(final String name,
                                 final DrasylConfig config,
                                 final Pipeline pipeline,
                                 final Scheduler scheduler,
                                 final Identity identity,
                                 final PeersManager peersManager,
                                 final TypeValidator inboundValidator,
                                 final TypeValidator outboundValidator) {
        super(name, config, pipeline, scheduler, identity, peersManager, inboundValidator, outboundValidator);
    }

    @Override
    public Handler handler() {
        return this;
    }

    @Override
    public void handlerAdded(final HandlerContext ctx) {
        // skip
    }

    @Override
    public void handlerRemoved(final HandlerContext ctx) {
        // skip
    }

    @Skip
    @Override
    public void read(final HandlerContext ctx,
                     final Address sender,
                     final Object msg,
                     final CompletableFuture<Void> future) {
        // skip
        ctx.fireRead(sender, msg, future);
    }

    @Skip
    @Override
    public void eventTriggered(final HandlerContext ctx,
                               final Event event,
                               final CompletableFuture<Void> future) {
        // skip
        ctx.fireEventTriggered(event, future);
    }

    @Skip
    @Override
    public void exceptionCaught(final HandlerContext ctx, final Exception cause) {
        //skip
        ctx.fireExceptionCaught(cause);
    }

    @Skip
    @Override
    public void write(final HandlerContext ctx,
                      final Address recipient,
                      final Object msg,
                      final CompletableFuture<Void> future) {
        // skip
        ctx.write(recipient, msg, future);
    }
}