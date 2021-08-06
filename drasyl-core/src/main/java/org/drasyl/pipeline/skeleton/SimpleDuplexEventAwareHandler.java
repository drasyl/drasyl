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

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.TypeParameterMatcher;
import org.drasyl.channel.MigrationOutboundMessage;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.address.Address;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;

import java.util.concurrent.CompletableFuture;

/**
 * {@link HandlerAdapter} which allows to explicit only handle a specific type of messages and
 * events.
 */
@SuppressWarnings({ "common-java:DuplicatedBlocks", "java:S118" })
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
    public void onOutbound(final ChannelHandlerContext ctx,
                           final Address recipient,
                           final Object msg,
                           final CompletableFuture<Void> future) throws Exception {
        if (acceptOutbound(msg) && acceptAddress(recipient)) {
            @SuppressWarnings("unchecked") final O castedMsg = (O) msg;
            @SuppressWarnings("unchecked") final A castedAddress = (A) recipient;
            matchedOutbound(ctx, castedAddress, castedMsg, future);
        }
        else {
            FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new MigrationOutboundMessage<>(msg, recipient)))).combine(future);
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
    @SuppressWarnings("java:S112")
    protected abstract void matchedOutbound(ChannelHandlerContext ctx,
                                            A recipient,
                                            O msg,
                                            CompletableFuture<Void> future) throws Exception;
}
