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
package org.drasyl.loopback.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.channel.MigrationInboundMessage;
import org.drasyl.channel.MigrationOutboundMessage;
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;

import java.util.concurrent.CompletableFuture;

import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;

/**
 * This handler converts outgoing messages addressed to the local node to incoming messages
 * addressed to the local node.
 */
@ChannelHandler.Sharable
@Stateless
public class LoopbackMessageHandler extends SimpleDuplexHandler<Object, Object, Address> {
    private final boolean started;

    LoopbackMessageHandler(final boolean started) {
        this.started = started;
    }

    public LoopbackMessageHandler() {
        this(false);
    }

    @Override
    protected void matchedOutbound(final ChannelHandlerContext ctx,
                                   final Address recipient,
                                   final Object msg,
                                   final CompletableFuture<Void> future) {
        if (started && ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().equals(recipient)) {
            ctx.fireChannelRead(new MigrationInboundMessage<>(msg, (Address) ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey(), future));
        }
        else {
            FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new MigrationOutboundMessage<>(msg, recipient)))).combine(future);
        }
    }

    @Override
    protected void matchedInbound(final ChannelHandlerContext ctx,
                                  final Address sender,
                                  final Object msg,
                                  final CompletableFuture<Void> future) throws Exception {
        ctx.fireChannelRead(new MigrationInboundMessage<>(msg, sender, future));
    }
}
