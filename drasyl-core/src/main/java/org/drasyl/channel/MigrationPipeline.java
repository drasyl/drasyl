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

import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import org.drasyl.event.Event;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.util.FutureUtil;

import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

public class MigrationPipeline implements Pipeline {
    private final ChannelPipeline channelPipeline;

    public MigrationPipeline(final ChannelPipeline channelPipeline) {
        this.channelPipeline = requireNonNull(channelPipeline);
    }

    @Override
    public Pipeline addLast(final String name, final Handler handler) {
        channelPipeline.addLast(name, handler);
        return this;
    }

    @Override
    public Pipeline remove(final String name) {
        channelPipeline.remove(name);
        return this;
    }

    @Override
    public CompletableFuture<Void> processInbound(final Address sender,
                                                  final Object msg) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        channelPipeline.fireChannelRead(new MigrationInboundMessage<>(msg, sender, future));
        return future;
    }

    @Override
    public CompletableFuture<Void> processInbound(final Event event) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        channelPipeline.fireUserEventTriggered(new MigrationEvent(event, future));
        return future;
    }

    @Override
    public CompletableFuture<Void> processOutbound(final Address recipient, final Object msg) {
        final ChannelPromise promise = channelPipeline.newPromise();
        channelPipeline.writeAndFlush(new MigrationOutboundMessage<>(msg, recipient), promise);
        return FutureUtil.toFuture(promise);
    }
}
