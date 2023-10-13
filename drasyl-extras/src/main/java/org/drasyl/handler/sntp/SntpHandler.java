/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.sntp;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class SntpHandler extends SimpleChannelInboundHandler<SntpMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(SntpHandler.class);
    private final CompletableFuture<Long> result;
    private long requestTime;
    private final AtomicLong responseTime;

    public SntpHandler(final CompletableFuture<Long> result, final AtomicLong responseTime) {
        this.result = result;
        this.responseTime = responseTime;
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        // Send NTP request to the first server in list
        requestTime = System.currentTimeMillis();

        ctx.writeAndFlush(SntpMessage.of(requestTime));
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final SntpMessage msg) throws Exception {
        final long clockOffset = ((requestTime - SntpMessage.toJavaTime(msg.getOriginateTimestamp())) + (SntpMessage.toJavaTime(msg.getTransmitTimestamp()) - responseTime.get())) / 2;

        this.result.complete(clockOffset);
        ctx.channel().close();
    }
}
