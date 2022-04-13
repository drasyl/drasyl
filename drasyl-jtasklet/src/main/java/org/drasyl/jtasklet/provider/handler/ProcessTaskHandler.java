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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.jtasklet.message.OffloadTask;
import org.drasyl.jtasklet.message.ReturnResult;
import org.drasyl.jtasklet.message.VmUp;
import org.drasyl.jtasklet.provider.runtime.ExecutionResult;
import org.drasyl.jtasklet.provider.runtime.RuntimeEnvironment;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;

public class ProcessTaskHandler extends SimpleChannelInboundHandler<OffloadTask> {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessTaskHandler.class);
    private final RuntimeEnvironment runtimeEnvironment;
    private final PrintStream out;
    private final AtomicReference<Channel> brokerChannel;
    private static final EventLoopGroup eventLoop = new NioEventLoopGroup(1);

    public ProcessTaskHandler(final RuntimeEnvironment runtimeEnvironment,
                              final PrintStream out,
                              final AtomicReference<Channel> brokerChannel) {
        this.runtimeEnvironment = requireNonNull(runtimeEnvironment);
        this.out = requireNonNull(out);
        this.brokerChannel = requireNonNull(brokerChannel);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        out.println("Send me tasks! I'm hungry!");
        ctx.fireChannelActive();
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final OffloadTask msg) {
        LOG.info("Got offloading task request `{}` from `{}`", msg, ctx.channel().remoteAddress());
        eventLoop.execute(() -> {
            out.print("Got task from " + ctx.channel().remoteAddress() + ". Compute...");
            final ExecutionResult result = runtimeEnvironment.execute(msg.getSource(), msg.getInput());
            out.println("done after " + result.getExecutionTime() + "ms!");

            final ReturnResult response = new ReturnResult(result.getOutput());
            LOG.info("Send result `{}` to `{}`", response, ctx.channel().remoteAddress());
            out.print("Send result back to " + ctx.channel().remoteAddress() + "...");
            ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE).addListener(future -> {
                if (future.isSuccess()) {
                    out.println("done!");
                    final Channel channel = brokerChannel.get();
                    if (channel != null) {
                        channel.writeAndFlush(new VmUp()).addListener(FIRE_EXCEPTION_ON_FAILURE).addListener(future1 -> out.println("Send me tasks! I'm hungry!"));
                    }
                }
            });
        });
    }
}
