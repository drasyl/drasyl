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
import org.drasyl.channel.MigrationHandlerContext;
import org.drasyl.channel.MigrationInboundMessage;
import org.drasyl.channel.MigrationOutboundMessage;
import org.drasyl.event.Event;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.Skip;
import org.drasyl.pipeline.address.Address;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.channel.Null.NULL;

/**
 * Skeleton implementation of a {@link Handler}.
 */
public class HandlerAdapter implements Handler {
    /**
     * Do nothing by default, sub-classes may override this method.
     */
    @Override
    public void onAdded(final MigrationHandlerContext ctx) {
        // Do nothing
    }

    /**
     * Do nothing by default, sub-classes may override this method.
     */
    @Override
    public void onRemoved(final MigrationHandlerContext ctx) {
        // Do nothing
    }

    @Skip
    @Override
    public void onInbound(final MigrationHandlerContext ctx,
                          final Address sender,
                          final Object msg,
                          final CompletableFuture<Void> future) throws Exception {
        ctx.passInbound(sender, msg, future);
    }

    @Skip
    @Override
    public void onEvent(final MigrationHandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        ctx.passEvent(event, future);
    }

    @Skip
    @Override
    public void onException(final MigrationHandlerContext ctx, final Exception cause) {
        ctx.fireExceptionCaught(cause);
    }

    @Skip
    @Override
    public void onOutbound(final MigrationHandlerContext ctx,
                           final Address recipient,
                           final Object msg,
                           final CompletableFuture<Void> future) throws Exception {
        ctx.passOutbound(recipient, msg, future);
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
        final MigrationHandlerContext handlerCtx = new MigrationHandlerContext(ctx);
        onAdded(handlerCtx);
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        final MigrationHandlerContext handlerCtx = new MigrationHandlerContext(ctx);
        onRemoved(handlerCtx);
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
            final MigrationHandlerContext handlerCtx = new MigrationHandlerContext(ctx);
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
                onOutbound(handlerCtx, migrationMsg.address(), payload, future);
            }
            catch (final Exception e) {
                future.completeExceptionally(e);
                handlerCtx.fireExceptionCaught(e);
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
            final MigrationHandlerContext handlerCtx = new MigrationHandlerContext(ctx);
            Object payload = migrationMsg.message();
            if (payload == NULL) {
                payload = null;
            }
            try {
                onInbound(handlerCtx, migrationMsg.address(), payload, migrationMsg.future());
            }
            catch (final Exception e) {
                migrationMsg.future().completeExceptionally(e);
                handlerCtx.fireExceptionCaught(e);
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
            final MigrationHandlerContext handlerCtx = new MigrationHandlerContext(ctx);
            onEvent(handlerCtx, ((MigrationEvent) evt).event(), ((MigrationEvent) evt).future());
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
