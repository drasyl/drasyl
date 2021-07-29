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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.util.scheduler.DrasylScheduler;

import java.util.concurrent.CompletableFuture;

public class MigrationHandlerContext implements HandlerContext {
    private final ChannelHandlerContext ctx;
    private final DrasylServerChannel channel;

    public MigrationHandlerContext(final ChannelHandlerContext ctx) {
        this.ctx = ctx;
        this.channel = (DrasylServerChannel) ctx.channel();
    }

    @Override
    public ByteBuf alloc() {
        return null;
    }

    @Override
    public ByteBuf alloc(final boolean preferDirect) {
        return null;
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public Handler handler() {
        return null;
    }

    @Override
    public HandlerContext passException(final Exception cause) {
        return null;
    }

    @Override
    public CompletableFuture<Void> passInbound(final Address sender,
                                               final Object msg,
                                               final CompletableFuture<Void> future) {
        return null;
    }

    @Override
    public CompletableFuture<Void> passEvent(final Event event,
                                             final CompletableFuture<Void> future) {
        return null;
    }

    @Override
    public CompletableFuture<Void> passOutbound(final Address recipient,
                                                final Object msg,
                                                final CompletableFuture<Void> future) {
        return null;
    }

    @Override
    public DrasylConfig config() {
        return channel.drasylConfig();
    }

    @Override
    public Pipeline pipeline() {
        throw new RuntimeException("not supported");
    }

    @Override
    public DrasylScheduler independentScheduler() {
        return null;
    }

    @Override
    public DrasylScheduler dependentScheduler() {
        return null;
    }

    @Override
    public Identity identity() {
        return channel.localAddress0();
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
