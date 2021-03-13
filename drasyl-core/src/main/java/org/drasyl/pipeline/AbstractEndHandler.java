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
package org.drasyl.pipeline;

import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.util.scheduler.DrasylScheduler;

import java.util.concurrent.CompletableFuture;

/**
 * Skeleton implementation for end handler of the {@link Pipeline}.
 */
@SuppressWarnings({ "common-java:DuplicatedBlocks", "java:S107" })
abstract class AbstractEndHandler extends AbstractHandlerContext implements Handler {
    protected AbstractEndHandler(final String name,
                                 final DrasylConfig config,
                                 final Pipeline pipeline,
                                 final DrasylScheduler dependentScheduler,
                                 final DrasylScheduler independentScheduler,
                                 final Identity identity,
                                 final PeersManager peersManager,
                                 final Serialization inboundSerialization,
                                 final Serialization outboundSerialization) {
        super(name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
    }

    @Override
    public Handler handler() {
        return this;
    }

    @Override
    public void onAdded(final HandlerContext ctx) {
        // skip
    }

    @Override
    public void onRemoved(final HandlerContext ctx) {
        // skip
    }

    @Skip
    @Override
    public void onInbound(final HandlerContext ctx,
                          final Address sender,
                          final Object msg,
                          final CompletableFuture<Void> future) {
        // skip
        ctx.passInbound(sender, msg, future);
    }

    @Skip
    @Override
    public void onEvent(final HandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        // skip
        ctx.passEvent(event, future);
    }

    @Skip
    @Override
    public void onException(final HandlerContext ctx, final Exception cause) {
        //skip
        ctx.passException(cause);
    }

    @Skip
    @Override
    public void onOutbound(final HandlerContext ctx,
                           final Address recipient,
                           final Object msg,
                           final CompletableFuture<Void> future) {
        // skip
        ctx.passOutbound(recipient, msg, future);
    }
}
