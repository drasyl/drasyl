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

import org.drasyl.channel.MigrationHandlerContext;
import org.drasyl.event.Event;
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
    public void onEvent(final MigrationHandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        ctx.passEvent(event, future);
    }

    @Override
    protected void matchedEvent(final MigrationHandlerContext ctx,
                                final Event event,
                                final CompletableFuture<Void> future) {
        ctx.passEvent(event, future);
    }
}
