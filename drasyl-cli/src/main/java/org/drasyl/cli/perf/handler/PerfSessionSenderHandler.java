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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.cli.perf.message.PerfMessage;
import org.drasyl.cli.perf.message.Probe;
import org.drasyl.cli.perf.message.SessionRejection;
import org.drasyl.cli.perf.message.SessionRequest;
import org.drasyl.cli.perf.message.TestResults;
import org.drasyl.util.RandomUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.time.Duration;
import java.util.function.LongSupplier;

import static io.netty.channel.ChannelFutureListener.CLOSE;
import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.perf.message.TestResults.MICROSECONDS;

public class PerfSessionSenderHandler extends SimpleChannelInboundHandler<PerfMessage> {
    public static final Duration SESSION_PROGRESS_INTERVAL = ofSeconds(1);
    private static final Logger LOG = LoggerFactory.getLogger(PerfSessionSenderHandler.class);
    private final SessionRequest session;
    private final PrintStream printStream;
    private final LongSupplier currentTimeSupplier;

    @SuppressWarnings("java:S107")
    PerfSessionSenderHandler(final SessionRequest session,
                             final PrintStream printStream,
                             final LongSupplier currentTimeSupplier) {
        this.session = requireNonNull(session);
        this.printStream = requireNonNull(printStream);
        this.currentTimeSupplier = requireNonNull(currentTimeSupplier);
    }

    public PerfSessionSenderHandler(final SessionRequest session,
                                    final PrintStream printStream) {
        this(session, printStream, System::nanoTime);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            performTest(ctx);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        performTest(ctx);
        ctx.fireChannelActive();
    }

    private void performTest(final ChannelHandlerContext ctx) {
        printStream.println("Test parameters: " + session);
        printStream.println("Interval                 Transfer     Bitrate          Lost/Total Messages");
        final byte[] probePayload = RandomUtil.randomBytes(session.getSize());
        final int messageSize = session.getSize() + Long.BYTES + Long.BYTES;
        final long startTime = currentTimeSupplier.getAsLong();
        final TestResults totalResults = new TestResults(messageSize, startTime, startTime);
        TestResults intervalResults = new TestResults(messageSize, totalResults.getTestStartTime(), totalResults.getTestStartTime());

        final Channel channel = ctx.channel();
        long currentTime;
        long sentMessages = 0;
        final long endTime = startTime + 1_000_000_000L * session.getTime();
        while (endTime > (currentTime = currentTimeSupplier.getAsLong())) {
            // print interim results?
            if (intervalResults.getStartTime() + SESSION_PROGRESS_INTERVAL.toNanos() <= currentTime) {
                intervalResults.stop(currentTime);
                printStream.println(intervalResults.print());
                totalResults.add(intervalResults);
                intervalResults = new TestResults(messageSize, startTime, currentTime);
            }

            // send message?
            final double desiredSentMessages = ((double) currentTime - startTime) / MICROSECONDS * session.getMps();
            if (desiredSentMessages >= sentMessages) {
                if (channel.isWritable()) {
                    final TestResults finalIntervalResults = intervalResults;
                    channel.writeAndFlush(new Probe(probePayload, sentMessages)).addListener(future -> {
                        if (!future.isSuccess()) {
                            LOG.trace("Unable to send message", future::cause);
                            finalIntervalResults.incrementLostMessages();
                        }
                    });
                }
                else {
                    LOG.trace("Unable to send message: Channel is not writable ({} bytes before writable).", channel::bytesBeforeWritable);
                    intervalResults.incrementLostMessages();
                }
                sentMessages++;
                intervalResults.incrementTotalMessages();
            }
        }

        // final interim results
        intervalResults.stop(currentTime);
        printStream.println(intervalResults.print());
        totalResults.add(intervalResults);

        printStream.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
        totalResults.stop(currentTime);
        printStream.println("Sender:");
        printStream.println(totalResults.print());

        // complete session
        LOG.debug("All probe messages sent. Complete test at {} and wait for confirmation...", channel::remoteAddress);
        ctx.writeAndFlush(totalResults).addListener(FIRE_EXCEPTION_ON_FAILURE);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final PerfMessage msg) throws Exception {
        if (msg instanceof TestResults) {
            // receiver has sent his results -> print final results
            printStream.println("Receiver:");
            printStream.println(((TestResults) msg).print());
            printStream.println();
            LOG.debug("Session completion has been confirmed by `{}`. We're done! Close channel.", ctx.channel().remoteAddress());
            ctx.channel().close();
        }
        else if (msg instanceof SessionRequest) {
            // already in an active session -> decline further requests
            LOG.debug("Peer requested a new session. But current session is still in progress. Close channel.");
            ctx.writeAndFlush(new SessionRejection()).addListener(CLOSE);
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }
}
