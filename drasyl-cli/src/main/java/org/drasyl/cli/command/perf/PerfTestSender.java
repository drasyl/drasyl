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
package org.drasyl.cli.command.perf;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import org.drasyl.cli.command.perf.message.Probe;
import org.drasyl.cli.command.perf.message.SessionRejection;
import org.drasyl.cli.command.perf.message.SessionRequest;
import org.drasyl.cli.command.perf.message.TestResults;
import org.drasyl.node.behaviour.Behavior;
import org.drasyl.node.behaviour.Behaviors;
import org.drasyl.node.event.Event;
import org.drasyl.util.RandomUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.time.Duration;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.command.perf.message.TestResults.MICROSECONDS;
import static org.drasyl.node.behaviour.Behaviors.same;

/**
 * Represents the sending node in a performance test.
 *
 * @see PerfTestReceiver
 */
public class PerfTestSender {
    public static final Duration SESSION_PROGRESS_INTERVAL = ofSeconds(1);
    /**
     * Is used to identity probe messages. probe messages are used for actual performance
     * measurements.
     */
    private static final Logger LOG = LoggerFactory.getLogger(PerfTestSender.class);
    public static final short COMPLETE_TEST_TRIES = (short) 10;
    private final SessionRequest session;
    private final EventLoopGroup eventLoopGroup;
    private final PrintStream printStream;
    private final Channel channel;
    private final Supplier<Behavior> successBehavior;
    private final Function<Exception, Behavior> failureBehavior;
    private final LongSupplier currentTimeSupplier;

    @SuppressWarnings("java:S107")
    PerfTestSender(final SessionRequest session,
                   final EventLoopGroup eventLoopGroup,
                   final PrintStream printStream,
                   final Channel channel,
                   final Supplier<Behavior> successBehavior,
                   final Function<Exception, Behavior> failureBehavior,
                   final LongSupplier currentTimeSupplier) {
        this.session = requireNonNull(session);
        this.eventLoopGroup = requireNonNull(eventLoopGroup);
        this.printStream = requireNonNull(printStream);
        this.channel = requireNonNull(channel);
        this.successBehavior = requireNonNull(successBehavior);
        this.failureBehavior = requireNonNull(failureBehavior);
        this.currentTimeSupplier = requireNonNull(currentTimeSupplier);
    }

    @SuppressWarnings("java:S107")
    public PerfTestSender(final SessionRequest session,
                          final EventLoopGroup eventLoopGroup,
                          final PrintStream printStream,
                          final Channel channel,
                          final Supplier<Behavior> successBehavior,
                          final Function<Exception, Behavior> failureBehavior) {
        this(session, eventLoopGroup, printStream, channel, successBehavior, failureBehavior, System::nanoTime);
    }

    public Behavior run() {
        return Behaviors.withScheduler(eventScheduler -> {
            printStream.println("Test parameters: " + session);
            printStream.println("Interval                 Transfer     Bitrate          Lost/Total Messages");
            eventLoopGroup.submit(() -> sendProbes(eventScheduler));

            return Behaviors.receive()
                    .onMessage(SessionRequest.class, (mySender, myPayload) -> {
                        // already in an active session -> decline further requests
                        channel.writeAndFlush(new SessionRejection());
                        return same();
                    })
                    .onEvent(TestCompleted.class, event -> {
                        // complete session
                        LOG.debug("All probe messages sent. Complete test at {} and wait for confirmation...", channel::remoteAddress);
                        return completing(event.getTotalResults(), COMPLETE_TEST_TRIES);
                    })
                    .onAnyEvent(event -> same())
                    .build();
        });
    }

    private void sendProbes(final Behaviors.EventScheduler eventScheduler) {
        final byte[] probePayload = RandomUtil.randomBytes(session.getSize());
        final int messageSize = session.getSize() + Long.BYTES + Long.BYTES;
        final long startTime = currentTimeSupplier.getAsLong();
        final TestResults totalResults = new TestResults(messageSize, startTime, startTime);
        TestResults intervalResults = new TestResults(messageSize, totalResults.getTestStartTime(), totalResults.getTestStartTime());

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

        eventScheduler.scheduleEvent(new TestCompleted(totalResults));
    }

    /**
     * Test is done. Send own results to receiver and wait for them results.
     */
    Behavior completing(final TestResults results,
                        final short remainingRetries) {
        if (remainingRetries > 0) {
            LOG.debug("Got no complete confirmation from {}. Retry and wait for confirmation (remaining={})...", channel::remoteAddress, () -> remainingRetries);
            channel.writeAndFlush(results);

            return Behaviors.withScheduler(eventScheduler -> {
                eventScheduler.scheduleEvent(new TestCompletionTimeout(), ofSeconds(1));

                return Behaviors.receive()
                        .onMessage(TestResults.class, (sender, payload) -> sender.equals(channel.remoteAddress()), (sender2, payload2) -> {
                            // receiver has sent his results -> print final results
                            LOG.debug("Session completion has been confirmed by {}. We're done!", sender2);
                            printStream.println("Receiver:");
                            printStream.println(payload2.print());
                            printStream.println();

                            return successBehavior.get();
                        })
                        .onEvent(TestCompletionTimeout.class, event -> completing(results, (short) (remainingRetries - 1)))
                        .onAnyEvent(myEvent -> same())
                        .build();
            }, eventLoopGroup);
        }
        else {
            LOG.debug("Got no complete confirmation from " + channel.remoteAddress() + ". Giving up.");
            return failureBehavior.apply(new Exception("Got no complete confirmation from " + channel.remoteAddress()));
        }
    }

    /**
     * Signals that all probe messages have been sent and the test can be completed.
     */
    static class TestCompleted implements Event {
        private final TestResults totalResults;

        public TestCompleted(final TestResults totalResults) {
            this.totalResults = totalResults;
        }

        public TestResults getTotalResults() {
            return totalResults;
        }
    }

    /**
     * Signals that the receiver has not sent his test results.
     */
    static class TestCompletionTimeout implements Event {
    }
}
