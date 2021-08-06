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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundInvoker;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.EventExecutorGroup;
import org.drasyl.event.Event;
import org.drasyl.pipeline.address.Address;
import org.drasyl.util.FutureUtil;

import java.net.SocketAddress;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

public class MigrationPipeline implements ChannelPipeline {
    private final ChannelPipeline channelPipeline;

    public MigrationPipeline(final ChannelPipeline channelPipeline) {
        this.channelPipeline = requireNonNull(channelPipeline);
    }

    @Override
    public ChannelPipeline addFirst(final String name, final ChannelHandler handler) {
        return null;
    }

    @Override
    public ChannelPipeline addFirst(final EventExecutorGroup group,
                                    final String name, final ChannelHandler handler) {
        return null;
    }

    @Override
    public ChannelPipeline addLast(final String name, final ChannelHandler handler) {
        return null;
    }

    @Override
    public ChannelPipeline addLast(final EventExecutorGroup group,
                                   final String name,
                                   final ChannelHandler handler) {
        return null;
    }

    @Override
    public ChannelPipeline addBefore(final String baseName,
                                     final String name,
                                     final ChannelHandler handler) {
        return null;
    }

    @Override
    public ChannelPipeline addBefore(final EventExecutorGroup group,
                                     final String baseName,
                                     final String name,
                                     final ChannelHandler handler) {
        return null;
    }

    @Override
    public ChannelPipeline addAfter(final String baseName,
                                    final String name,
                                    final ChannelHandler handler) {
        return null;
    }

    @Override
    public ChannelPipeline addAfter(final EventExecutorGroup group,
                                    final String baseName,
                                    final String name,
                                    final ChannelHandler handler) {
        return null;
    }

    @Override
    public ChannelPipeline addFirst(final ChannelHandler... handlers) {
        return null;
    }

    @Override
    public ChannelPipeline addFirst(final EventExecutorGroup group,
                                    final ChannelHandler... handlers) {
        return null;
    }

    @Override
    public ChannelPipeline addLast(final ChannelHandler... handlers) {
        return null;
    }

    @Override
    public ChannelPipeline addLast(final EventExecutorGroup group,
                                   final ChannelHandler... handlers) {
        return null;
    }

    @Override
    public ChannelPipeline remove(final ChannelHandler handler) {
        return null;
    }

    @Override
    public ChannelHandler remove(final String name) {
        return null;
    }

    @Override
    public <T extends ChannelHandler> T remove(final Class<T> handlerType) {
        return null;
    }

    @Override
    public ChannelHandler removeFirst() {
        return null;
    }

    @Override
    public ChannelHandler removeLast() {
        return null;
    }

    @Override
    public ChannelPipeline replace(final ChannelHandler oldHandler,
                                   final String newName,
                                   final ChannelHandler newHandler) {
        return null;
    }

    @Override
    public ChannelHandler replace(final String oldName,
                                  final String newName,
                                  final ChannelHandler newHandler) {
        return null;
    }

    @Override
    public <T extends ChannelHandler> T replace(final Class<T> oldHandlerType,
                                                final String newName,
                                                final ChannelHandler newHandler) {
        return null;
    }

    @Override
    public ChannelHandler first() {
        return null;
    }

    @Override
    public ChannelHandlerContext firstContext() {
        return null;
    }

    @Override
    public ChannelHandler last() {
        return null;
    }

    @Override
    public ChannelHandlerContext lastContext() {
        return null;
    }

    @Override
    public ChannelHandler get(final String name) {
        return null;
    }

    @Override
    public <T extends ChannelHandler> T get(final Class<T> handlerType) {
        return null;
    }

    @Override
    public ChannelHandlerContext context(final ChannelHandler handler) {
        return null;
    }

    @Override
    public ChannelHandlerContext context(final String name) {
        return null;
    }

    @Override
    public ChannelHandlerContext context(final Class<? extends ChannelHandler> handlerType) {
        return null;
    }

    @Override
    public Channel channel() {
        return null;
    }

    @Override
    public List<String> names() {
        return null;
    }

    @Override
    public Map<String, ChannelHandler> toMap() {
        return null;
    }

    @Override
    public ChannelPipeline fireChannelRegistered() {
        return null;
    }

    @Override
    public ChannelPipeline fireChannelUnregistered() {
        return null;
    }

    @Override
    public ChannelPipeline fireChannelActive() {
        return null;
    }

    @Override
    public ChannelPipeline fireChannelInactive() {
        return null;
    }

    @Override
    public ChannelPipeline fireExceptionCaught(final Throwable cause) {
        return null;
    }

    @Override
    public ChannelPipeline fireUserEventTriggered(final Object event) {
        return channelPipeline.fireUserEventTriggered(event);
    }

    @Override
    public ChannelPipeline fireChannelRead(final Object msg) {
        return channelPipeline.fireChannelRead(msg);
    }

    @Override
    public ChannelPipeline fireChannelReadComplete() {
        return null;
    }

    @Override
    public ChannelPipeline fireChannelWritabilityChanged() {
        return null;
    }

    @Override
    public ChannelFuture bind(final SocketAddress localAddress) {
        return null;
    }

    @Override
    public ChannelFuture connect(final SocketAddress remoteAddress) {
        return null;
    }

    @Override
    public ChannelFuture connect(final SocketAddress remoteAddress,
                                 final SocketAddress localAddress) {
        return null;
    }

    @Override
    public ChannelFuture disconnect() {
        return null;
    }

    @Override
    public ChannelFuture close() {
        return null;
    }

    @Override
    public ChannelFuture deregister() {
        return null;
    }

    @Override
    public ChannelFuture bind(final SocketAddress localAddress, final ChannelPromise promise) {
        return null;
    }

    @Override
    public ChannelFuture connect(final SocketAddress remoteAddress, final ChannelPromise promise) {
        return null;
    }

    @Override
    public ChannelFuture connect(final SocketAddress remoteAddress,
                                 final SocketAddress localAddress,
                                 final ChannelPromise promise) {
        return null;
    }

    @Override
    public ChannelFuture disconnect(final ChannelPromise promise) {
        return null;
    }

    @Override
    public ChannelFuture close(final ChannelPromise promise) {
        return null;
    }

    @Override
    public ChannelFuture deregister(final ChannelPromise promise) {
        return null;
    }

    @Override
    public ChannelOutboundInvoker read() {
        return null;
    }

    @Override
    public ChannelFuture write(final Object msg) {
        return null;
    }

    @Override
    public ChannelFuture write(final Object msg, final ChannelPromise promise) {
        return null;
    }

    @Override
    public ChannelPipeline flush() {
        return null;
    }

    @Override
    public ChannelFuture writeAndFlush(final Object msg, final ChannelPromise promise) {
        return channelPipeline.writeAndFlush(msg, promise);
    }

    @Override
    public ChannelFuture writeAndFlush(final Object msg) {
        return null;
    }

    @Override
    public ChannelPromise newPromise() {
        return channelPipeline.newPromise();
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return null;
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return null;
    }

    @Override
    public ChannelFuture newFailedFuture(final Throwable cause) {
        return null;
    }

    @Override
    public ChannelPromise voidPromise() {
        return null;
    }

    @Override
    public Iterator<Map.Entry<String, ChannelHandler>> iterator() {
        return null;
    }

    public CompletableFuture<Void> processInbound(final Address sender,
                                                  final Object msg) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        fireChannelRead(new MigrationInboundMessage<>(msg, sender, future));
        return future;
    }

    public CompletableFuture<Void> processInbound(final Event event) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        fireUserEventTriggered(new MigrationEvent(event, future));
        return future;
    }

    public CompletableFuture<Void> processOutbound(final Address recipient, final Object msg) {
        final ChannelPromise promise = newPromise();
        writeAndFlush(new MigrationOutboundMessage<>(msg, recipient), promise);
        return FutureUtil.toFuture(promise);
    }
}
