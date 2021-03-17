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
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Skip;
import org.drasyl.pipeline.address.Address;

import java.util.concurrent.CompletableFuture;

/**
 * {@link HandlerAdapter} which allows to explicit only handle a specific type of messages and
 * events.
 */
@SuppressWarnings({ "common-java:DuplicatedBlocks", "java:S118" })
public abstract class SimpleDuplexHandler<I, O, A extends Address> extends SimpleDuplexEventAwareHandler<I, Event, O, A> {
    protected SimpleDuplexHandler() {
    }

    protected SimpleDuplexHandler(final Class<? extends I> inboundMessageType,
                                  final Class<? extends O> outboundMessageType,
                                  final Class<? extends A> addressType) {
        super(inboundMessageType, Event.class, outboundMessageType, addressType);
    }

    @Skip
    @Override
    public void onEvent(final HandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        ctx.passEvent(event, future);
    }

    @Override
    protected void matchedEvent(final HandlerContext ctx,
                                final Event event,
                                final CompletableFuture<Void> future) {
        ctx.passEvent(event, future);
    }
}
