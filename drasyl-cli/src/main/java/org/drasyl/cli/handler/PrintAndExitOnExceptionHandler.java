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

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.util.Worm;

import java.io.PrintStream;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;

/**
 * Consumes all exceptions, prints them to given {@link PrintStream}, closes the channel, and will
 * then exit with error code {@code 1}.
 */
public class PrintAndExitOnExceptionHandler extends ChannelInboundHandlerAdapter {
    private final PrintStream printStream;
    private final Worm<Integer> exitCode;
    private boolean closeCalled;

    public PrintAndExitOnExceptionHandler(final PrintStream printStream,
                                          final Worm<Integer> exitCode) {
        this.printStream = requireNonNull(printStream);
        this.exitCode = requireNonNull(exitCode);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx,
                                final Throwable cause) {
        if (ctx.channel().isOpen()) {
            cause.printStackTrace(printStream);
            if (!closeCalled) {
                closeCalled = true;
                ctx.channel().close()
                        .addListener(FIRE_EXCEPTION_ON_FAILURE)
                        .addListener((ChannelFutureListener) future -> exitCode.trySet(1));
            }
        }
    }
}
