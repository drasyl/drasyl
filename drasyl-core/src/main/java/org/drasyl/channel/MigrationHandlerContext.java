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
package org.drasyl.channel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.serialization.Serialization;

import java.util.concurrent.CompletableFuture;

/**
 * A wrapper used to add {@link Handler} to a {@link io.netty.channel.Channel}.
 */
public class MigrationHandlerContext {
    private final ChannelHandlerContext ctx;
    private final DrasylServerChannel channel;

    public MigrationHandlerContext(final ChannelHandlerContext ctx) {
        this.ctx = ctx;
        this.channel = (DrasylServerChannel) ctx.channel();
    }

    public ByteBuf alloc() {
        return alloc(false);
    }

    public ByteBuf alloc(final boolean preferDirect) {
        if (preferDirect) {
            return ctx.alloc().directBuffer();
        }
        else {
            return ctx.alloc().ioBuffer();
        }
    }

    public String name() {
        return ctx.name();
    }

    public Handler handler() {
        throw new RuntimeException("not implemented yet"); // NOSONAR
    }

    public MigrationHandlerContext passException(final Exception cause) {
        return null;
    }

    public CompletableFuture<Void> passInbound(final Address sender,
                                               final Object msg,
                                               final CompletableFuture<Void> future) {
        final MigrationInboundMessage<?, ?> migrationMsg = new MigrationInboundMessage<>(msg, sender, future);

        ctx.fireChannelRead(migrationMsg);

        return future;
    }

    @SuppressWarnings("UnusedReturnValue")
    public CompletableFuture<Void> passEvent(final Event event,
                                             final CompletableFuture<Void> future) {
        ctx.fireUserEventTriggered(new MigrationEvent(event, future));

        return future;
    }

    public CompletableFuture<Void> passOutbound(final Address recipient,
                                                final Object msg,
                                                final CompletableFuture<Void> future) {
        final MigrationOutboundMessage<?, ?> migrationMsg = new MigrationOutboundMessage<>(msg, recipient);

        ctx.writeAndFlush(migrationMsg).addListener(f -> {
            if (f.isSuccess()) {
                future.complete(null);
            }
            else {
                future.completeExceptionally(f.cause());
            }
        });

        return future;
    }

    public DrasylConfig config() {
        return channel.drasylConfig();
    }

    public Pipeline pipeline() {
        return new MigrationPipeline(ctx.pipeline());
    }

    public Scheduler independentScheduler() {
        return new MigrationScheduler(ctx.executor());
    }

    public Scheduler dependentScheduler() {
        return new MigrationScheduler(ctx.executor());
    }

    public Identity identity() {
        return channel.identity();
    }

    public PeersManager peersManager() {
        return channel.peersManager();
    }

    public Serialization inboundSerialization() {
        return channel.inboundSerialization();
    }

    public Serialization outboundSerialization() {
        return channel.outboundSerialization();
    }
}
