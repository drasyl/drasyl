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
import org.drasyl.channel.MigrationHandlerContext;
import org.drasyl.channel.MigrationInboundMessage;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;

import java.util.concurrent.CompletableFuture;

import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;

/**
 * This handler converts outgoing messages addressed to the local node to incoming messages
 * addressed to the local node.
 */
@ChannelHandler.Sharable
@Stateless
public class LoopbackMessageHandler extends SimpleOutboundHandler<Object, Address> {
    private boolean started;

    LoopbackMessageHandler(final boolean started) {
        this.started = started;
    }

    public LoopbackMessageHandler() {
        this(false);
    }

    @Override
    public void onEvent(final MigrationHandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        if (event instanceof NodeUpEvent) {
            started = true;
        }
        else if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            started = false;
        }

        // passthrough event
        ctx.passEvent(event, future);
    }

    @Override
    protected void matchedOutbound(final MigrationHandlerContext ctx,
                                   final Address recipient,
                                   final Object msg,
                                   final CompletableFuture<Void> future) {
        if (started && ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().equals(recipient)) {
            ctx.fireChannelRead(new MigrationInboundMessage<>(msg, (Address) ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey(), future));
        }
        else {
            ctx.passOutbound(recipient, msg, future);
        }
    }
}
