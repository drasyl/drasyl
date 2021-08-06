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

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import org.drasyl.event.Event;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.address.Address;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * A wrapper used to add {@link Handler} to a {@link io.netty.channel.Channel}.
 */
public class MigrationHandlerContext implements ChannelHandlerContext {
    private final ChannelHandlerContext ctx;

    public MigrationHandlerContext(final ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Channel channel() {
        return ctx.channel();
    }

    @Override
    public EventExecutor executor() {
        return ctx.executor();
    }

    @Override
    public String name() {
        return ctx.name();
    }

    @Override
    public ChannelHandler handler() {
        return ctx.handler();
    }

    @Override
    public boolean isRemoved() {
        return ctx.isRemoved();
    }

    @Override
    public ChannelHandlerContext fireChannelRegistered() {
        return ctx.fireChannelRegistered();
    }

    @Override
    public ChannelHandlerContext fireChannelUnregistered() {
        return ctx.fireChannelUnregistered();
    }

    @Override
    public ChannelHandlerContext fireChannelActive() {
        return ctx.fireChannelActive();
    }

    @Override
    public ChannelHandlerContext fireChannelInactive() {
        return ctx.fireChannelInactive();
    }

    @Override
    public ChannelHandlerContext fireExceptionCaught(final Throwable cause) {
        return ctx.fireExceptionCaught(cause);
    }

    @Override
    public ChannelHandlerContext fireUserEventTriggered(final Object evt) {
        return ctx.fireUserEventTriggered(evt);
    }

    @Override
    public ChannelHandlerContext fireChannelRead(final Object msg) {
        return ctx.fireChannelRead(msg);
    }

    @Override
    public ChannelHandlerContext fireChannelReadComplete() {
        return ctx.fireChannelReadComplete();
    }

    @Override
    public ChannelHandlerContext fireChannelWritabilityChanged() {
        return ctx.fireChannelWritabilityChanged();
    }

    @Override
    public ChannelFuture bind(final SocketAddress localAddress) {
        return ctx.bind(localAddress);
    }

    @Override
    public ChannelFuture connect(final SocketAddress remoteAddress) {
        return ctx.connect(remoteAddress);
    }

    @Override
    public ChannelFuture connect(final SocketAddress remoteAddress,
                                 final SocketAddress localAddress) {
        return ctx.connect(remoteAddress, localAddress);
    }

    @Override
    public ChannelFuture disconnect() {
        return ctx.disconnect();
    }

    @Override
    public ChannelFuture close() {
        return ctx.close();
    }

    @Override
    public ChannelFuture deregister() {
        return ctx.deregister();
    }

    @Override
    public ChannelFuture bind(final SocketAddress localAddress, final ChannelPromise promise) {
        return ctx.bind(localAddress, promise);
    }

    @Override
    public ChannelFuture connect(final SocketAddress remoteAddress, final ChannelPromise promise) {
        return ctx.connect(remoteAddress, promise);
    }

    @Override
    public ChannelFuture connect(final SocketAddress remoteAddress,
                                 final SocketAddress localAddress,
                                 final ChannelPromise promise) {
        return ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public ChannelFuture disconnect(final ChannelPromise promise) {
        return ctx.disconnect(promise);
    }

    @Override
    public ChannelFuture close(final ChannelPromise promise) {
        return ctx.close(promise);
    }

    @Override
    public ChannelFuture deregister(final ChannelPromise promise) {
        return ctx.deregister(promise);
    }

    @Override
    public ChannelHandlerContext read() {
        return ctx.read();
    }

    @Override
    public ChannelFuture write(final Object msg) {
        return ctx.write(msg);
    }

    @Override
    public ChannelFuture write(final Object msg, final ChannelPromise promise) {
        return ctx.write(msg, promise);
    }

    @Override
    public ChannelHandlerContext flush() {
        return ctx.flush();
    }

    @Override
    public ChannelFuture writeAndFlush(final Object msg, final ChannelPromise promise) {
        return ctx.writeAndFlush(msg, promise);
    }

    @Override
    public ChannelFuture writeAndFlush(final Object msg) {
        return ctx.writeAndFlush(msg);
    }

    @Override
    public ChannelPromise newPromise() {
        return ctx.newPromise();
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return ctx.newProgressivePromise();
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return ctx.newSucceededFuture();
    }

    @Override
    public ChannelFuture newFailedFuture(final Throwable cause) {
        return ctx.newFailedFuture(cause);
    }

    @Override
    public ChannelPromise voidPromise() {
        return ctx.voidPromise();
    }

    @Override
    public ChannelPipeline pipeline() {
        return ctx.pipeline();
    }

    @Override
    public ByteBufAllocator alloc() {
        return ctx.alloc();
    }

    @Override
    public <T> Attribute<T> attr(final AttributeKey<T> key) {
        return ctx.attr(key);
    }

    @Override
    public <T> boolean hasAttr(final AttributeKey<T> key) {
        return ctx.hasAttr(key);
    }

    @SuppressWarnings("UnusedReturnValue")
    public CompletableFuture<Void> passEvent(final Event event,
                                             final CompletableFuture<Void> future) {
        fireUserEventTriggered(new MigrationEvent(event, future));
        return future;
    }

    public CompletableFuture<Void> passOutbound(final Address recipient,
                                                final Object msg,
                                                final CompletableFuture<Void> future) {
        FutureCombiner.getInstance().add(FutureUtil.toFuture(writeAndFlush(new MigrationOutboundMessage<>(msg, recipient)))).combine(future);
        return future;
    }
}
