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
package org.drasyl.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.serialization.Serialization;

import java.util.concurrent.CompletableFuture;

/**
 * A wrapper used to add {@link Handler} to a {@link io.netty.channel.Channel}.
 */
public class MigrationHandlerContext implements HandlerContext {
    private final ChannelHandlerContext ctx;
    private final DrasylServerChannel channel;

    public MigrationHandlerContext(final ChannelHandlerContext ctx) {
        this.ctx = ctx;
        this.channel = (DrasylServerChannel) ctx.channel();
    }

    @Override
    public ByteBuf alloc() {
        return alloc(false);
    }

    @Override
    public ByteBuf alloc(final boolean preferDirect) {
        if (preferDirect) {
            return ctx.alloc().directBuffer();
        }
        else {
            return ctx.alloc().ioBuffer();
        }
    }

    @Override
    public String name() {
        throw new RuntimeException("not implemented yet"); // NOSONAR
    }

    @Override
    public Handler handler() {
        throw new RuntimeException("not implemented yet"); // NOSONAR
    }

    @Override
    public HandlerContext passException(final Exception cause) {
        return null;
    }

    @Override
    public CompletableFuture<Void> passInbound(final Address sender,
                                               final Object msg,
                                               final CompletableFuture<Void> future) {
        final MigrationMessage<?, ?> addressedMsg = new MigrationMessage<>(msg, sender);

        try {
            ctx.fireChannelRead(addressedMsg);
            future.complete(null);
        }
        catch (final Exception e) { // NOSONAR
            future.completeExceptionally(e);
        }

        return future;
    }

    @Override
    public CompletableFuture<Void> passEvent(final Event event,
                                             final CompletableFuture<Void> future) {
        try {
            ctx.fireUserEventTriggered(event);
            future.complete(null);
        }
        catch (final Exception e) { // NOSONAR
            future.completeExceptionally(e);
        }

        return future;
    }

    @Override
    public CompletableFuture<Void> passOutbound(final Address recipient,
                                                final Object msg,
                                                final CompletableFuture<Void> future) {
        final MigrationMessage<?, ?> addressedMsg = new MigrationMessage<>(msg, recipient);

        ctx.writeAndFlush(addressedMsg).addListener(f -> {
            if (f.isSuccess()) {
                future.complete(null);
            }
            else {
                future.completeExceptionally(f.cause());
            }
        });

        return future;
    }

    @Override
    public DrasylConfig config() {
        return channel.drasylConfig();
    }

    @Override
    public Pipeline pipeline() {
        throw new RuntimeException("not supported"); // NOSONAR
    }

    @Override
    public Scheduler independentScheduler() {
        return new MigrationScheduler(ctx.executor());
    }

    @Override
    public Scheduler dependentScheduler() {
        return new MigrationScheduler(ctx.executor());
    }

    @Override
    public Identity identity() {
        return channel.identity();
    }

    @Override
    public PeersManager peersManager() {
        return channel.peersManager();
    }

    @Override
    public Serialization inboundSerialization() {
        return channel.inboundSerialization();
    }

    @Override
    public Serialization outboundSerialization() {
        return channel.outboundSerialization();
    }
}
