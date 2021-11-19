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
package org.drasyl.cli.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.Future;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Throws a {@link SuperPeerTimeoutException} if no {@link AddPathAndSuperPeerEvent} event has been
 * received within the given timeout. Postbones the {@link ChannelInboundHandlerAdapter#channelActive(ChannelHandlerContext)}
 * event till a {@link AddPathAndSuperPeerEvent} has been received.
 */
public class SuperPeerTimeoutHandler extends ChannelInboundHandlerAdapter {
    private final long timeoutMillis;
    private Future<?> timeoutTask;

    SuperPeerTimeoutHandler(final long timeoutMillis, final Future<?> timeoutTask) {
        this.timeoutMillis = requirePositive(timeoutMillis);
        this.timeoutTask = timeoutTask;
    }

    public SuperPeerTimeoutHandler(final long timeoutMillis) {
        this(timeoutMillis, null);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        createTimeoutTask(ctx);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        timeoutTask.cancel(false);
        timeoutTask = null;
        ctx.fireChannelInactive();
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) {
        if (evt instanceof AddPathAndSuperPeerEvent) {
            timeoutTask.cancel(false);
            ctx.pipeline().remove(this);
            ctx.fireChannelActive();
        }
        ctx.fireUserEventTriggered(evt);
    }

    private void createTimeoutTask(final ChannelHandlerContext ctx) {
        if (timeoutTask == null) {
            timeoutTask = ctx.executor().schedule(() -> {
                ctx.pipeline().remove(this);
                if (ctx.channel().isOpen()) {
                    ctx.fireExceptionCaught(new SuperPeerTimeoutException(timeoutMillis));
                }
            }, timeoutMillis, MILLISECONDS);
        }
    }

    public static class SuperPeerTimeoutException extends Exception {
        public SuperPeerTimeoutException(final long timeoutMillis) {
            super("Node did not come online within " + timeoutMillis + "ms. Look like all super peer(s) are unreachable.");
        }
    }
}
