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
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.channel.MigrationEvent;
import org.drasyl.channel.MigrationInboundMessage;
import org.drasyl.channel.MigrationOutboundMessage;
import org.drasyl.event.Event;
import org.drasyl.pipeline.Skip;
import org.drasyl.pipeline.address.Address;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.TypeParameterMatcher;

import java.util.concurrent.CompletableFuture;

import static org.drasyl.channel.Null.NULL;

/**
 * {@link HandlerAdapter} which allows to explicit only handle a specific type of address.
 *
 * @param <A> the type of the {@link Address}.
 */
@SuppressWarnings("java:S118")
public abstract class AddressHandlerAdapter<A> extends HandlerAdapter {
    private final TypeParameterMatcher matcherAddress;

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter
     * of the class.
     */
    protected AddressHandlerAdapter() {
        matcherAddress = TypeParameterMatcher.find(this, AddressHandlerAdapter.class, "A");
    }

    /**
     * Create a new instance
     *
     * @param addressType the type of messages to match
     */
    protected AddressHandlerAdapter(final Class<? extends A> addressType) {
        matcherAddress = TypeParameterMatcher.get(addressType);
    }

    /**
     * Returns {@code true} if the given address should be handled, {@code false} otherwise.
     */
    protected boolean acceptAddress(final Address address) {
        return matcherAddress.match(address);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof MigrationInboundMessage) {
            final MigrationInboundMessage<?, ?> migrationMsg = (MigrationInboundMessage<?, ?>) msg;
            final Object payload = migrationMsg.message() == NULL ? null : migrationMsg.message();
            try {
                onInbound(ctx, migrationMsg.address(), payload, migrationMsg.future());
            }
            catch (final Exception e) {
                migrationMsg.future().completeExceptionally(e);
                ctx.fireExceptionCaught(e);
                ReferenceCountUtil.safeRelease(migrationMsg.message());
            }
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof MigrationOutboundMessage) {
            final MigrationOutboundMessage<?, ?> migrationMsg = (MigrationOutboundMessage<?, ?>) msg;
            final CompletableFuture<Void> future = new CompletableFuture<>();
            FutureUtil.combine(future, promise);
            final Object payload = migrationMsg.message() == NULL ? null : migrationMsg.message();
            try {
                onOutbound(ctx, migrationMsg.address(), payload, future);
            }
            catch (final Exception e) {
                future.completeExceptionally(e);
                ctx.fireExceptionCaught(e);
                ReferenceCountUtil.safeRelease(migrationMsg.message());
            }
        }
        else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        onAdded(ctx);
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        onRemoved(ctx);
    }

    @Skip
    public void onException(final ChannelHandlerContext ctx, final Exception cause) {
        ctx.fireExceptionCaught(cause);
    }

    @SuppressWarnings("java:S112")
    @Skip
    public void onInbound(final ChannelHandlerContext ctx,
                          final Address sender,
                          final Object msg,
                          final CompletableFuture<Void> future) throws Exception {
        ctx.fireChannelRead(new MigrationInboundMessage<>(msg, sender, future));
    }

    @Skip
    public void onEvent(final ChannelHandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        ctx.fireUserEventTriggered(new MigrationEvent(event, future));
    }

    @SuppressWarnings("java:S112")
    @Skip
    public void onOutbound(final ChannelHandlerContext ctx,
                           final Address recipient,
                           final Object msg,
                           final CompletableFuture<Void> future) throws Exception {
        FutureUtil.combine(ctx.writeAndFlush(new MigrationOutboundMessage<>(msg, recipient)), future);
    }

    /**
     * Do nothing by default, sub-classes may override this method.
     */
    public void onAdded(final ChannelHandlerContext ctx) {
        // NOOP
    }

    /**
     * Do nothing by default, sub-classes may override this method.
     */
    public void onRemoved(final ChannelHandlerContext ctx) {
        // NOOP
    }
}
