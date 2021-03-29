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

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.behaviour.Behavior;
import org.drasyl.behaviour.Behaviors;
import org.drasyl.cli.command.perf.message.SessionRejection;
import org.drasyl.cli.command.perf.message.SessionRequest;
import org.drasyl.cli.command.perf.message.TestResults;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.RandomUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static org.drasyl.behaviour.Behaviors.same;
import static org.drasyl.cli.command.perf.message.TestResults.MICROSECONDS;

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
    static final byte[] PROBE_HEADER = new byte[]{ 20, 21, 1, 23, 0, 1, 38, 16 };
    private static final Logger LOG = LoggerFactory.getLogger(PerfTestSender.class);
    public static final short COMPLETE_TEST_TRIES = (short) 10;
    private final SessionRequest session;
    private final CompressedPublicKey receiver;
    private final Scheduler scheduler;
    private final PrintStream printStream;
    private final BiFunction<CompressedPublicKey, Object, CompletableFuture<Void>> sendMethod;
    private final Supplier<Behavior> successBehavior;
    private final Function<Exception, Behavior> failureBehavior;
    private final LongSupplier currentTimeSupplier;
    private TestResults intervalResults;

    @SuppressWarnings("java:S107")
    PerfTestSender(final CompressedPublicKey receiver,
                   final SessionRequest session,
                   final Scheduler scheduler,
                   final PrintStream printStream,
                   final BiFunction<CompressedPublicKey, Object, CompletableFuture<Void>> sendMethod,
                   final Supplier<Behavior> successBehavior,
                   final Function<Exception, Behavior> failureBehavior,
                   final LongSupplier currentTimeSupplier) {
        this.receiver = requireNonNull(receiver);
        this.session = requireNonNull(session);
        this.scheduler = requireNonNull(scheduler);
        this.printStream = requireNonNull(printStream);
        this.sendMethod = requireNonNull(sendMethod);
        this.successBehavior = requireNonNull(successBehavior);
        this.failureBehavior = requireNonNull(failureBehavior);
        this.currentTimeSupplier = requireNonNull(currentTimeSupplier);
    }

    @SuppressWarnings("java:S107")
    public PerfTestSender(final CompressedPublicKey receiver,
                          final SessionRequest session,
                          final Scheduler scheduler,
                          final PrintStream printStream,
                          final BiFunction<CompressedPublicKey, Object, CompletableFuture<Void>> sendMethod,
                          final Supplier<Behavior> successBehavior,
                          final Function<Exception, Behavior> failureBehavior) {
        this(receiver, session, scheduler, printStream, sendMethod, successBehavior, failureBehavior, System::nanoTime);
    }

    public Behavior run() {
        return Behaviors.withScheduler(eventScheduler -> {
            printStream.println("Test parameters: " + session);
            printStream.println("Interval                 Transfer     Bitrate          Lost/Total Messages");
            scheduler.scheduleDirect(() -> {
                final byte[] probePayload = RandomUtil.randomBytes(session.getSize());
                final int messageSize = session.getSize() + PROBE_HEADER.length + Long.BYTES;
                final long startTime = currentTimeSupplier.getAsLong();
                final TestResults totalResults = new TestResults(messageSize, startTime, startTime);
                intervalResults = new TestResults(messageSize, totalResults.getTestStartTime(), totalResults.getTestStartTime());

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
                        sendProbeMessage(probePayload, sentMessages);
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
            });

            return Behaviors.receive()
                    .onMessage(SessionRequest.class, (mySender, myPayload) -> {
                        // already in an active session -> decline further requests
                        sendMethod.apply(mySender, new SessionRejection());
                        return same();
                    })
                    .onEvent(TestCompleted.class, event -> {
                        // complete session
                        LOG.debug("All probe messages sent. Complete test at {} and wait for confirmation...", receiver);
                        return completing(event.getTotalResults(), COMPLETE_TEST_TRIES);
                    })
                    .onAnyEvent(event -> same())
                    .build();
        });
    }

    private void sendProbeMessage(final byte[] probePayload, final long messageNo) {
        final ByteArrayOutputStream byteArrayStream = new ByteArrayOutputStream();
        try (final DataOutputStream outputStream = new DataOutputStream(byteArrayStream)) {
            outputStream.write(PROBE_HEADER);
            outputStream.writeLong(messageNo);
            outputStream.write(probePayload);
            final Object message = byteArrayStream.toByteArray();
            sendMethod.apply(receiver, message).exceptionally(e -> {
                LOG.trace("Unable to send message", e);
                intervalResults.incrementLostMessages();
                return null;
            });
        }
        catch (final IOException e) {
            // should never happen
            LOG.error("Unable to serialize message", e);
            intervalResults.incrementLostMessages();
        }
    }

    /**
     * Test is done. Send own results to receiver and wait for them results.
     */
    Behavior completing(final TestResults results,
                        final short remainingRetries) {
        if (remainingRetries > 0) {
            LOG.debug("Got no complete confirmation from {}. Retry and wait for confirmation (remaining={})...", () -> receiver, () -> remainingRetries);
            sendMethod.apply(receiver, results);

            return Behaviors.withScheduler(eventScheduler -> {
                eventScheduler.scheduleEvent(new TestCompletionTimeout(), ofSeconds(1));

                return Behaviors.receive()
                        .onMessage(TestResults.class, (sender, payload) -> sender.equals(receiver), (sender2, payload2) -> {
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
            }, scheduler);
        }
        else {
            LOG.debug("Got no complete confirmation from " + receiver + ". Giving up.");
            return failureBehavior.apply(new Exception("Got no complete confirmation from " + receiver));
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
