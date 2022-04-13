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
package org.drasyl.jtasklet.consumer.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.jtasklet.message.OffloadTask;
import org.drasyl.jtasklet.message.ReleaseToken;
import org.drasyl.jtasklet.message.ReturnResult;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;

public class OffloadTaskHandler extends SimpleChannelInboundHandler<ReturnResult> {
    private static final Logger LOG = LoggerFactory.getLogger(OffloadTaskHandler.class);
    private final PrintStream out;
    private final String source;
    private final Object[] input;
    private final Consumer<Object[]> outputConsumer;
    private final AtomicReference<Instant> offloadTaskTime;
    private final AtomicReference<Instant> returnResultTime;
    private final AtomicReference<String> token;
    private final AtomicReference<Channel> brokerChannel;

    public OffloadTaskHandler(final PrintStream out,
                              final String source,
                              final Object[] input,
                              final Consumer<Object[]> outputConsumer,
                              final AtomicReference<Instant> offloadTaskTime,
                              final AtomicReference<Instant> returnResultTime,
                              final AtomicReference<String> token,
                              final AtomicReference<Channel> brokerChannel) {
        this.out = requireNonNull(out);
        this.source = requireNonNull(source);
        this.input = requireNonNull(input);
        this.outputConsumer = requireNonNull(outputConsumer);
        this.offloadTaskTime = requireNonNull(offloadTaskTime);
        this.returnResultTime = requireNonNull(returnResultTime);
        this.token = requireNonNull(token);
        this.brokerChannel = requireNonNull(brokerChannel);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        final OffloadTask msg = new OffloadTask(token.get(), source, input);
        LOG.info("Send offload task request `{}` to `{}`", msg, ctx.channel().remoteAddress());
        out.print("Offload task to " + ctx.channel().remoteAddress() + " with input " + Arrays.toString(input) + "...");
        offloadTaskTime.set(Instant.now());
        ctx.writeAndFlush(msg).addListener(FIRE_EXCEPTION_ON_FAILURE).addListener(future -> {
            if (future.isSuccess()) {
                out.println("done!");
            }
            else {
                out.println("failed!");
                brokerChannel.get().writeAndFlush(new ReleaseToken(token.get()));
                ctx.channel().close();
            }
        });

        ctx.fireChannelActive();
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final ReturnResult msg) {
        LOG.info("Got result `{}` from `{}`", msg, ctx.channel().remoteAddress());
        returnResultTime.set(Instant.now());
        outputConsumer.accept(msg.getOutput());
        ctx.channel().parent().close();
    }
}
