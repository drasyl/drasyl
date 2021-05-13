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
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.behaviour.Behavior;
import org.drasyl.behaviour.Behaviors;
import org.drasyl.cli.command.perf.message.SessionRejection;
import org.drasyl.cli.command.perf.message.SessionRequest;
import org.drasyl.cli.command.perf.message.TestResults;
import org.drasyl.event.Event;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static org.drasyl.behaviour.Behaviors.same;
import static org.drasyl.cli.command.perf.PerfTestSender.PROBE_HEADER;
import static org.drasyl.cli.command.perf.message.TestResults.MICROSECONDS;

/**
 * Represents the receiving node in a performance test.
 *
 * @see PerfTestSender
 */
public class PerfTestReceiver {
    public static final Duration SESSION_PROGRESS_INTERVAL = ofSeconds(1);
    public static final Duration SESSION_TIMEOUT = ofSeconds(10);
    private static final Logger LOG = LoggerFactory.getLogger(PerfTestReceiver.class);
    private final SessionRequest session;
    private final Scheduler scheduler;
    private final IdentityPublicKey sender;
    private final PrintStream printStream;
    private final BiFunction<IdentityPublicKey, Object, CompletableFuture<Void>> sendMethod;
    private final Supplier<Behavior> successBehavior;
    private final Function<Exception, Behavior> failureBehavior;
    private final LongSupplier currentTimeSupplier;
    private TestResults intervalResults;

    @SuppressWarnings("java:S107")
    PerfTestReceiver(final IdentityPublicKey sender,
                     final SessionRequest session,
                     final Scheduler scheduler,
                     final PrintStream printStream,
                     final BiFunction<IdentityPublicKey, Object, CompletableFuture<Void>> sendMethod,
                     final Supplier<Behavior> successBehavior,
                     final Function<Exception, Behavior> failureBehavior,
                     final LongSupplier currentTimeSupplier) {
        this.sender = requireNonNull(sender);
        this.session = requireNonNull(session);
        this.scheduler = requireNonNull(scheduler);
        this.printStream = requireNonNull(printStream);
        this.sendMethod = requireNonNull(sendMethod);
        this.successBehavior = requireNonNull(successBehavior);
        this.failureBehavior = requireNonNull(failureBehavior);
        this.currentTimeSupplier = requireNonNull(currentTimeSupplier);
    }

    public PerfTestReceiver(final IdentityPublicKey sender,
                            final SessionRequest session,
                            final Scheduler scheduler,
                            final PrintStream printStream,
                            final BiFunction<IdentityPublicKey, Object, CompletableFuture<Void>> sendMethod,
                            final Supplier<Behavior> successBehavior,
                            final Function<Exception, Behavior> failureBehavior) {
        this(sender, session, scheduler, printStream, sendMethod, successBehavior, failureBehavior, System::nanoTime);
    }

    public Behavior run() {
        return Behaviors.withScheduler(eventScheduler -> {
            final Disposable sessionProgress = eventScheduler.schedulePeriodicallyEvent(new CheckTestStatus(), SESSION_PROGRESS_INTERVAL, SESSION_PROGRESS_INTERVAL);

            final int messageSize = session.getSize() + PROBE_HEADER.length + Long.BYTES;
            final long startTime = currentTimeSupplier.getAsLong();
            final AtomicLong lastMessageReceivedTime = new AtomicLong(startTime);
            final AtomicLong lastReceivedMessageNo = new AtomicLong(-1);
            final AtomicLong lastOutOfOrderMessageNo = new AtomicLong(-1);
            final TestResults totalResults = new TestResults(messageSize, startTime, startTime);
            intervalResults = new TestResults(messageSize, totalResults.getTestStartTime(), totalResults.getTestStartTime());
            printStream.println("Test parameters: " + session);
            printStream.println("Interval                 Transfer     Bitrate          Lost/Total Messages");

            // new behavior
            return Behaviors.receive()
                    .onMessage(byte[].class, (mySender, myPayload) -> mySender.equals(sender), (mySender, myPayload) -> {
                        handleProbeMessage(lastMessageReceivedTime, lastReceivedMessageNo, lastOutOfOrderMessageNo, myPayload);
                        return same();
                    })
                    .onMessage(TestResults.class, (mySender, myPayload) -> mySender.equals(sender), (mySender, otherResults) -> {
                        // sender has sent his results
                        LOG.debug("Got complete request from {}", mySender);

                        if (intervalResults != null && intervalResults.getTotalMessages() > 0) {
                            intervalResults.stop(lastMessageReceivedTime.get());
                            printStream.println(intervalResults.print());
                            totalResults.add(intervalResults);
                            intervalResults = null;
                        }

                        totalResults.stop(lastMessageReceivedTime.get());
                        totalResults.adjustResults(otherResults);

                        // print final results
                        printStream.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
                        printStream.println("Sender:");
                        printStream.println(otherResults.print());
                        printStream.println("Receiver:");
                        printStream.println(totalResults.print());
                        printStream.println();

                        sessionProgress.dispose();

                        return replyResults(mySender, totalResults);
                    })
                    .onMessage(SessionRequest.class, (mySender, myPayload) -> {
                        // already in an active session -> decline further requests
                        sendMethod.apply(mySender, new SessionRejection());
                        return same();
                    })
                    .onEvent(CheckTestStatus.class, event -> {
                        // print interim results and kill dead session
                        intervalResults.stop(currentTimeSupplier.getAsLong());
                        printStream.println(intervalResults.print());
                        totalResults.add(intervalResults);
                        intervalResults = new TestResults(messageSize, startTime, intervalResults.getStopTime());

                        final double currentTime = currentTimeSupplier.getAsLong();
                        if (lastMessageReceivedTime.get() < currentTime - SESSION_TIMEOUT.toNanos()) {
                            final double timeSinceLastMessage = (currentTime - lastMessageReceivedTime.get()) / MICROSECONDS;
                            printStream.printf((Locale) null, "No message received for %.2fs. Abort session.%n", timeSinceLastMessage);
                            sessionProgress.dispose();
                            return failureBehavior.apply(new Exception(String.format((Locale) null, "No message received for %.2fs. Abort session.%n", timeSinceLastMessage)));
                        }
                        else {
                            return same();
                        }
                    })
                    .onAnyEvent(event -> same())
                    .build();
        }, scheduler);
    }

    void handleProbeMessage(final AtomicLong lastMessageReceivedTime,
                            final AtomicLong lastReceivedMessageNo,
                            final AtomicLong lastOutOfOrderMessageNo,
                            final byte[] payload) {
        final ByteArrayInputStream byteArrayStream = new ByteArrayInputStream(payload);
        try (final DataInputStream inputStream = new DataInputStream(byteArrayStream)) {
            // is probe message?
            final byte[] probeHeader = inputStream.readNBytes(PROBE_HEADER.length);
            if (Arrays.equals(PROBE_HEADER, probeHeader)) {
                final long messageNo = inputStream.readLong();

                // record prob message
                LOG.trace("Got probe message {} of {}", () -> messageNo, session::getMps);
                lastMessageReceivedTime.set(currentTimeSupplier.getAsLong());
                intervalResults.incrementTotalMessages();
                final long expectedMessageNo = lastReceivedMessageNo.get() + 1;
                if (expectedMessageNo != messageNo && lastReceivedMessageNo.get() > messageNo && lastOutOfOrderMessageNo.get() != expectedMessageNo) {
                    intervalResults.incrementOutOfOrderMessages();
                    lastOutOfOrderMessageNo.set(expectedMessageNo);
                }
                if (messageNo > lastReceivedMessageNo.get()) {
                    lastReceivedMessageNo.set(messageNo);
                }
            }
        }
        catch (final IOException e) {
            LOG.warn("Unable to parse message:", e);
        }
    }

    private Behavior replyResults(final IdentityPublicKey sender,
                                  final TestResults totalResults) {
        return Behaviors.withScheduler(eventScheduler -> {
            // reply our results
            LOG.debug("Send complete confirmation to {}", sender);
            sendMethod.apply(sender, totalResults).thenRun(() -> eventScheduler.scheduleEvent(new ResultsReplied()));

            return Behaviors.receive()
                    .onEvent(ResultsReplied.class, m -> successBehavior.get())
                    .onAnyEvent(event -> same())
                    .build();
        });
    }

    /**
     * Signals that the the test status should be checked.
     */
    static class CheckTestStatus implements Event {
    }

    /**
     * Signals that the results have been replied.
     */
    static class ResultsReplied implements Event {
    }
}
