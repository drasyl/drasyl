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
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.channel.MigrationEvent;
import org.drasyl.channel.MigrationInboundMessage;
import org.drasyl.channel.MigrationOutboundMessage;
import org.drasyl.event.Event;
import org.drasyl.pipeline.Skip;
import org.drasyl.pipeline.address.Address;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.channel.Null.NULL;

/**
 * Skeleton implementation of a {@link HandlerAdapter}.
 */
public class HandlerAdapter implements ChannelOutboundHandler, ChannelInboundHandler {
    /**
     * Do nothing by default, sub-classes may override this method.
     */
    public void onAdded(final ChannelHandlerContext ctx) {
    }

    /**
     * Do nothing by default, sub-classes may override this method.
     */
    public void onRemoved(final ChannelHandlerContext ctx) {
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

    @Skip
    public void onException(final ChannelHandlerContext ctx, final Exception cause) {
        ctx.fireExceptionCaught(cause);
    }

    @SuppressWarnings("java:S112")
    @Skip
    public void onOutbound(final ChannelHandlerContext ctx,
                           final Address recipient,
                           final Object msg,
                           final CompletableFuture<Void> future) throws Exception {
        FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new MigrationOutboundMessage<>(msg, recipient)))).combine(future);
    }

    @Override
    public void bind(final ChannelHandlerContext ctx,
                     final SocketAddress localAddress,
                     final ChannelPromise promise) throws Exception {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(final ChannelHandlerContext ctx,
                        final SocketAddress remoteAddress,
                        final SocketAddress localAddress,
                        final ChannelPromise promise) throws Exception {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(final ChannelHandlerContext ctx,
                           final ChannelPromise promise) {
        ctx.disconnect(promise);
    }

    @Override
    public void close(final ChannelHandlerContext ctx,
                      final ChannelPromise promise) throws Exception {
        ctx.close(promise);
    }

    @Override
    public void deregister(final ChannelHandlerContext ctx,
                           final ChannelPromise promise) throws Exception {
        ctx.deregister(promise);
    }

    @Override
    public void read(final ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        onAdded(ctx);
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        onRemoved(ctx);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx,
                                final Throwable cause) {
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof MigrationOutboundMessage) {
            final MigrationOutboundMessage<?, ?> migrationMsg = (MigrationOutboundMessage<?, ?>) msg;
            final CompletableFuture<Void> future = new CompletableFuture<>();
            future.whenComplete((unused, throwable) -> {
                if (throwable == null) {
                    promise.setSuccess();
                }
                else {
                    promise.setFailure(throwable);
                }
            });
            Object payload = migrationMsg.message();
            if (payload == NULL) {
                payload = null;
            }
            try {
                onOutbound(ctx, migrationMsg.address(), payload, future);
            }
            catch (final Exception e) {
                future.completeExceptionally(e);
                ctx.fireExceptionCaught(e);
                ReferenceCountUtil.safeRelease(msg);
            }
        }
        else {
            throw new RuntimeException("not implemented yet"); // NOSONAR
        }
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void channelRegistered(final ChannelHandlerContext ctx) {
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(final ChannelHandlerContext ctx) {
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof MigrationInboundMessage) {
            final MigrationInboundMessage<?, ?> migrationMsg = (MigrationInboundMessage<?, ?>) msg;
            Object payload = migrationMsg.message();
            if (payload == NULL) {
                payload = null;
            }
            try {
                onInbound(ctx, migrationMsg.address(), payload, migrationMsg.future());
            }
            catch (final Exception e) {
                migrationMsg.future().completeExceptionally(e);
                ctx.fireExceptionCaught(e);
                ReferenceCountUtil.safeRelease(msg);
            }
        }
        else {
            throw new RuntimeException("not implemented yet"); // NOSONAR
        }
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        ctx.fireChannelReadComplete();
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) {
        if (evt instanceof MigrationEvent) {
            onEvent(ctx, ((MigrationEvent) evt).event(), ((MigrationEvent) evt).future());
        }
        else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void channelWritabilityChanged(final ChannelHandlerContext ctx) {
        ctx.fireChannelWritabilityChanged();
    }
}
