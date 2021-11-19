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
import org.drasyl.cli.perf.message.Probe;
import org.drasyl.cli.perf.message.SessionRejection;
import org.drasyl.cli.perf.message.SessionRequest;
import org.drasyl.cli.perf.message.TestResults;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static io.netty.channel.ChannelFutureListener.CLOSE;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.cli.perf.message.TestResults.MICROSECONDS;

public class PerfSessionReceiverHandler extends SimpleChannelInboundHandler<Object> {
    public static final Duration SESSION_PROGRESS_INTERVAL = ofSeconds(1);
    public static final Duration SESSION_TIMEOUT = ofSeconds(10);
    private static final Logger LOG = LoggerFactory.getLogger(PerfSessionReceiverHandler.class);
    private final SessionRequest session;
    private final PrintStream printStream;
    private final LongSupplier currentTimeSupplier;
    private TestResults intervalResults;
    private Future<?> sessionProgress;
    private AtomicLong lastMessageReceivedTime;
    private AtomicLong lastReceivedMessageNo;
    private AtomicLong lastOutOfOrderMessageNo;
    private TestResults totalResults;

    @SuppressWarnings("java:S107")
    PerfSessionReceiverHandler(final SessionRequest session,
                               final PrintStream printStream,
                               final LongSupplier currentTimeSupplier) {
        this.session = requireNonNull(session);
        this.printStream = requireNonNull(printStream);
        this.currentTimeSupplier = requireNonNull(currentTimeSupplier);
    }

    public PerfSessionReceiverHandler(final SessionRequest session,
                                      final PrintStream printStream) {
        this(session, printStream, System::nanoTime);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        final int messageSize = session.getSize() + Long.BYTES + Long.BYTES;
        final long startTime = currentTimeSupplier.getAsLong();
        lastMessageReceivedTime = new AtomicLong(startTime);
        lastReceivedMessageNo = new AtomicLong(-1);
        lastOutOfOrderMessageNo = new AtomicLong(-1);
        totalResults = new TestResults(messageSize, startTime, startTime);
        intervalResults = new TestResults(messageSize, totalResults.getTestStartTime(), totalResults.getTestStartTime());
        printStream.println("Test parameters: " + session);
        printStream.println("Interval                 Transfer     Bitrate          Lost/Total Messages");

        sessionProgress = ctx.executor().scheduleAtFixedRate(() -> {
            // print interim results and kill dead session
            intervalResults.stop(currentTimeSupplier.getAsLong());
            printStream.println(intervalResults.print());
            totalResults.add(intervalResults);
            intervalResults = new TestResults(messageSize, startTime, intervalResults.getStopTime());

            final double currentTime = currentTimeSupplier.getAsLong();
            if (lastMessageReceivedTime.get() < currentTime - SESSION_TIMEOUT.toNanos()) {
                final double timeSinceLastMessage = (currentTime - lastMessageReceivedTime.get()) / MICROSECONDS;
                printStream.printf((Locale) null, "No message received for %.2fs. Abort session.%n", timeSinceLastMessage);
                sessionProgress.cancel(false);
                LOG.debug("Close channel.");
                ctx.channel().close();
            }
        }, SESSION_PROGRESS_INTERVAL.toMillis(), SESSION_PROGRESS_INTERVAL.toMillis(), MILLISECONDS);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        sessionProgress.cancel(false);
        ctx.fireChannelInactive();
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final Object msg) throws Exception {
        if (msg instanceof Probe) {
            handleProbeMessage(lastMessageReceivedTime, lastReceivedMessageNo, lastOutOfOrderMessageNo, (Probe) msg);
        }
        else if (msg instanceof TestResults) {
            // sender has sent his results
            LOG.debug("Got complete request from `{}`.", ctx.channel().remoteAddress());

            if (intervalResults != null && intervalResults.getTotalMessages() > 0) {
                intervalResults.stop(lastMessageReceivedTime.get());
                printStream.println(intervalResults.print());
                totalResults.add(intervalResults);
                intervalResults = null;
            }

            totalResults.stop(lastMessageReceivedTime.get());
            totalResults.adjustResults((TestResults) msg);

            // print final results
            printStream.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
            printStream.println("Sender:");
            printStream.println(((TestResults) msg).print());
            printStream.println("Receiver:");
            printStream.println(totalResults.print());
            printStream.println();

            sessionProgress.cancel(false);

            // reply our results
            LOG.debug("Send complete confirmation to `{}` and close channel.", ctx.channel().remoteAddress());
            ctx.writeAndFlush(totalResults).addListener(CLOSE);
        }
        else if (msg instanceof SessionRequest) {
            // already in an active session -> decline further requests
            LOG.debug("Peer is busy with an other session. Close channel.");
            ctx.writeAndFlush(new SessionRejection()).addListener(CLOSE);
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    void handleProbeMessage(final AtomicLong lastMessageReceivedTime,
                            final AtomicLong lastReceivedMessageNo,
                            final AtomicLong lastOutOfOrderMessageNo,
                            final Probe probe) {
        // record prob message
        LOG.trace("Got probe message {} of {}", probe::getMessageNo, session::getMps);
        lastMessageReceivedTime.set(currentTimeSupplier.getAsLong());
        intervalResults.incrementTotalMessages();
        final long expectedMessageNo = lastReceivedMessageNo.get() + 1;
        if (expectedMessageNo != probe.getMessageNo() && lastReceivedMessageNo.get() > probe.getMessageNo() && lastOutOfOrderMessageNo.get() != expectedMessageNo) {
            intervalResults.incrementOutOfOrderMessages();
            lastOutOfOrderMessageNo.set(expectedMessageNo);
        }
        if (probe.getMessageNo() > lastReceivedMessageNo.get()) {
            lastReceivedMessageNo.set(probe.getMessageNo());
        }
    }
}
