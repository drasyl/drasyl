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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.Future;
import org.drasyl.handler.PeersRttReport;
import org.drasyl.jtasklet.message.VmHeartbeat;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class VmHeartbeatHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(VmHeartbeatHandler.class);
    private final AtomicReference<PeersRttReport> lastRttReport;
    private final long benchmark;
    private Future<?> heartbeat;

    public VmHeartbeatHandler(final AtomicReference<PeersRttReport> lastRttReport,
                              final long benchmark) {
        this.lastRttReport = requireNonNull(lastRttReport);
        this.benchmark = benchmark;
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        sendHeartbeat(ctx);
        ctx.fireChannelActive();
    }

    private void sendHeartbeat(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            final PeersRttReport report = lastRttReport.get();
            final VmHeartbeat msg = new VmHeartbeat(benchmark, report);
            LOG.debug("Send heartbeat `{}` to `{}`", msg, ctx.channel().remoteAddress());
            ctx.writeAndFlush(msg).addListener(FIRE_EXCEPTION_ON_FAILURE).addListener(f -> {
                if (f.isSuccess()) {
                    LOG.trace("ACKed");
                    ctx.executor().schedule(() -> sendHeartbeat(ctx), 1_000L, MILLISECONDS);
                }
            });
        }
    }
}
