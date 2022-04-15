/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.jtasklet.provider.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.handler.PeersRttReport;
import org.drasyl.jtasklet.message.VmHeartbeat;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class VmHeartbeatHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(VmHeartbeatHandler.class);
    private final AtomicReference<PeersRttReport> lastRttReport;
    private final long benchmark;
    private final PrintStream err;
    private final AtomicReference<String> token;
    private boolean isClosing;

    public VmHeartbeatHandler(final AtomicReference<PeersRttReport> lastRttReport,
                              final long benchmark,
                              final PrintStream err,
                              final AtomicReference<String> token) {
        this.lastRttReport = requireNonNull(lastRttReport);
        this.benchmark = benchmark;
        this.err = requireNonNull(err);
        this.token = requireNonNull(token);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            sendHeartbeat(ctx);
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        isClosing = true;
        ctx.close(promise);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        sendHeartbeat(ctx);
        ctx.fireChannelActive();
    }

    private void sendHeartbeat(final ChannelHandlerContext ctx) {
        if (!isClosing && ctx.channel().isActive()) {
            final PeersRttReport report = lastRttReport.get();
            final VmHeartbeat msg = new VmHeartbeat(benchmark, report, token.get());
            LOG.debug("Send heartbeat `{}` to `{}`", msg, ctx.channel().remoteAddress());
            ctx.writeAndFlush(msg).addListener(f -> {
                if (f.isSuccess()) {
                    LOG.trace("ACKed");
                    ctx.executor().schedule(() -> sendHeartbeat(ctx), 1_000L, MILLISECONDS);
                }
                else if (f.cause() instanceof ClosedChannelException) {
                    // ignore
                }
                else {
                    ctx.fireExceptionCaught(f.cause());
                }
            });
        }
    }
}
