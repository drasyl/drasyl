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

import io.netty.util.internal.TypeParameterMatcher;
import org.drasyl.identity.CompressedPublicKey;

import java.util.concurrent.CompletableFuture;

/**
 * {@link DuplexHandler} which allows to explicit only handle a specific type of messages and
 * events.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public abstract class SimpleDuplexHandler<I, E, O> extends SimpleInboundHandler<I, E> implements OutboundHandler {
    private final TypeParameterMatcher outboundMessageMatcher;

    protected SimpleDuplexHandler() {
        outboundMessageMatcher = TypeParameterMatcher.find(this, SimpleDuplexHandler.class, "O");
    }

    protected SimpleDuplexHandler(Class<? extends I> inboundMessageType,
                                  Class<? extends E> inboundEventType,
                                  Class<? extends O> outboundMessageType) {
        super(inboundMessageType, inboundEventType);
        outboundMessageMatcher = TypeParameterMatcher.get(outboundMessageType);
    }

    @Override
    public void write(HandlerContext ctx,
                      CompressedPublicKey recipient,
                      Object msg,
                      CompletableFuture<Void> future) {
        if (acceptOutbound(msg)) {
            @SuppressWarnings("unchecked")
            O castedMsg = (O) msg;
            matchedWrite(ctx, recipient, castedMsg, future);
        }
        else {
            ctx.write(recipient, msg, future);
        }
    }

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be
     * passed to the next {@link InboundHandler} in the {@link Pipeline}.
     */
    protected boolean acceptOutbound(Object msg) {
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
                                         CompressedPublicKey recipient,
                                         O msg,
                                         CompletableFuture<Void> future);
}
