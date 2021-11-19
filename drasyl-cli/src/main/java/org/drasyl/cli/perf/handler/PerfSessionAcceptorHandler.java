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
package org.drasyl.cli.perf.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.cli.perf.message.SessionConfirmation;
import org.drasyl.cli.perf.message.SessionRequest;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;

/**
 * Listens for {@link SessionRequest}s.
 */
public class PerfSessionAcceptorHandler extends SimpleChannelInboundHandler<SessionRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(PerfSessionAcceptorHandler.class);
    private final PrintStream printStream;

    public PerfSessionAcceptorHandler(final PrintStream printStream) {
        this.printStream = requireNonNull(printStream);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final SessionRequest request) {
        LOG.debug("Got session request from `{}`.", ctx.channel().remoteAddress());

        // confirm session
        LOG.debug("Confirm session request from `{}`.", ctx.channel().remoteAddress());
        printStream.println("Accepted session from " + ctx.channel().remoteAddress());
        ctx.writeAndFlush(new SessionConfirmation()).addListener(FIRE_EXCEPTION_ON_FAILURE);

        if (!request.isReverse()) {
            LOG.debug("Start receiver.");
            ctx.pipeline().replace(ctx.name(), null, new PerfSessionReceiverHandler(request, printStream));
        }
        else {
            LOG.debug("Running in reverse mode. Start sender.");
            ctx.pipeline().replace(ctx.name(), null, new PerfSessionSenderHandler(request, printStream));
        }
    }
}
