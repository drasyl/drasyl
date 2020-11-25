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
package org.drasyl.pipeline.skeleton;

import io.netty.util.internal.TypeParameterMatcher;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.address.Address;

import java.util.concurrent.CompletableFuture;

/**
 * {@link HandlerAdapter} which allows to explicit only handle a specific type of messages and
 * events.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public abstract class SimpleDuplexEventAwareHandler<I, E, O, A extends Address> extends SimpleInboundEventAwareHandler<I, E, A> {
    private final TypeParameterMatcher outboundMessageMatcher;

    protected SimpleDuplexEventAwareHandler() {
        outboundMessageMatcher = TypeParameterMatcher.find(this, SimpleDuplexEventAwareHandler.class, "O");
    }

    protected SimpleDuplexEventAwareHandler(final Class<? extends I> inboundMessageType,
                                            final Class<? extends E> inboundEventType,
                                            final Class<? extends O> outboundMessageType,
                                            final Class<? extends A> addressType) {
        super(inboundMessageType, inboundEventType, addressType);
        outboundMessageMatcher = TypeParameterMatcher.get(outboundMessageType);
    }

    @Override
    public void write(final HandlerContext ctx,
                      final Address recipient,
                      final Object msg,
                      final CompletableFuture<Void> future) {
        if (acceptOutbound(msg) && acceptAddress(recipient)) {
            @SuppressWarnings("unchecked") final O castedMsg = (O) msg;
            @SuppressWarnings("unchecked") final A castedAddress = (A) recipient;
            matchedWrite(ctx, castedAddress, castedMsg, future);
        }
        else {
            ctx.write(recipient, msg, future);
        }
    }

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be
     * passed to the next {@link Handler} in the {@link Pipeline}.
     */
    protected boolean acceptOutbound(final Object msg) {
        return outboundMessageMatcher.match(msg);
    }

    /**
     * Is called for each message of type {@link O}.
     *
     * @param ctx       handler context
     * @param recipient the recipient of the message
     * @param msg       the message
     * @param future    a future for the message
     */
    protected abstract void matchedWrite(HandlerContext ctx,
                                         A recipient,
                                         O msg,
                                         CompletableFuture<Void> future);
}
