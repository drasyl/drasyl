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
import io.netty.util.concurrent.Future;
import org.drasyl.cli.perf.message.Noop;
import org.drasyl.cli.perf.message.PerfMessage;
import org.drasyl.cli.perf.message.SessionConfirmation;
import org.drasyl.cli.perf.message.SessionRejection;
import org.drasyl.cli.perf.message.SessionRequest;
import org.drasyl.handler.discovery.AddPathAndChildrenEvent;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Requests a perf session once the channel becomes active or this handler is added to an active
 * channel.
 */
public class PerfSessionRequestorHandler extends SimpleChannelInboundHandler<PerfMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(PerfSessionRequestorHandler.class);
    private final PrintStream out;
    private final SessionRequest request;
    private final long requestTimeoutMillis;
    private final boolean waitForDirectConnection;
    private Future<?> timeoutTask;
    private boolean sessionRequested;
    private boolean directConnectionRequested;
    private boolean directConnectionPresent;

    @SuppressWarnings("java:S107")
    PerfSessionRequestorHandler(final PrintStream out,
                                final SessionRequest request,
                                final long requestTimeoutMillis,
                                final boolean waitForDirectConnection,
                                final Future<?> timeoutTask,
                                final boolean sessionRequested,
                                final boolean directConnectionRequested,
                                final boolean directConnectionPresent) {
        this.out = requireNonNull(out);
        this.request = requireNonNull(request);
        this.requestTimeoutMillis = requirePositive(requestTimeoutMillis);
        this.waitForDirectConnection = waitForDirectConnection;
        this.timeoutTask = timeoutTask;
        this.sessionRequested = sessionRequested;
        this.directConnectionRequested = directConnectionRequested;
        this.directConnectionPresent = directConnectionPresent;
    }

    public PerfSessionRequestorHandler(final PrintStream out,
                                       final SessionRequest request,
                                       final long requestTimeoutMillis,
                                       final boolean waitForDirectConnection) {
        this(out, request, requestTimeoutMillis, waitForDirectConnection, null, false, false, false);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
        requestSession(ctx);
        createTimeoutTask(ctx);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx,
                             final PerfMessage msg) {
        if (sessionRequested && msg instanceof SessionConfirmation) {
            handleSessionConfirmation(ctx);
        }
        else if (sessionRequested && msg instanceof SessionRejection) {
            handleSessionRejection(ctx);
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
        final boolean isDirection = evt instanceof AddPathAndSuperPeerEvent || evt instanceof AddPathAndChildrenEvent || evt instanceof AddPathEvent;
        if (directConnectionRequested && !directConnectionPresent && isDirection) {
            out.println("Direct connection to " + ctx.channel().remoteAddress() + " established!");
            directConnectionPresent = true;
            requestSession(ctx);
        }
        ctx.fireUserEventTriggered(evt);
    }

    private void createTimeoutTask(final ChannelHandlerContext ctx) {
        if (timeoutTask == null) {
            timeoutTask = ctx.executor().schedule(() -> {
                if (ctx.channel().isOpen()) {
                    ctx.fireExceptionCaught(new PerfSessionRequestTimeoutException(requestTimeoutMillis));
                }
            }, requestTimeoutMillis, MILLISECONDS);
        }
    }

    private void requestSession(final ChannelHandlerContext ctx) {
        if (!sessionRequested && (!waitForDirectConnection || directConnectionPresent) && ctx.channel().isActive()) {
            out.println("Request session at " + ctx.channel().remoteAddress() + "...");
            ctx.writeAndFlush(request).addListener(FIRE_EXCEPTION_ON_FAILURE);
            sessionRequested = true;
        }
        if (!directConnectionRequested && waitForDirectConnection && !directConnectionPresent) {
            out.println("Try to establish direct connection to " + ctx.channel().remoteAddress() + "...");
            ctx.writeAndFlush(new Noop()).addListener(FIRE_EXCEPTION_ON_FAILURE);
            directConnectionRequested = true;
        }
    }

    private void handleSessionConfirmation(final ChannelHandlerContext ctx) {
        timeoutTask.cancel(false);

        out.println("Session has been confirmed by " + ctx.channel().remoteAddress() + "!");

        if (!request.isReverse()) {
            LOG.debug("Start sender.");
            ctx.pipeline().replace(ctx.name(), null, new PerfSessionSenderHandler(request, out));
        }
        else {
            LOG.debug("Running in reverse mode. Start receiver.");
            ctx.pipeline().replace(ctx.name(), null, new PerfSessionReceiverHandler(request, out));
        }
    }

    @SuppressWarnings("java:S2325")
    private void handleSessionRejection(final ChannelHandlerContext ctx) {
        timeoutTask.cancel(false);

        ctx.fireExceptionCaught(new PerfSessionRequestRejectedException());
    }

    public static class PerfSessionRequestTimeoutException extends Exception {
        public PerfSessionRequestTimeoutException(final long timeoutMillis) {
            super("The server did not respond within " + timeoutMillis + "ms. Try again later.");
        }
    }

    public static class PerfSessionRequestRejectedException extends Exception {
        public PerfSessionRequestRejectedException() {
            super("The server is busy running a test. Try again later.");
        }
    }
}
