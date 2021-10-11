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

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;
import org.drasyl.cli.command.perf.message.Probe;
import org.drasyl.cli.command.perf.message.SessionRejection;
import org.drasyl.cli.command.perf.message.SessionRequest;
import org.drasyl.cli.command.perf.message.TestResults;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.behaviour.Behavior;
import org.drasyl.node.behaviour.Behaviors;
import org.drasyl.node.event.Event;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.command.perf.message.TestResults.MICROSECONDS;
import static org.drasyl.node.behaviour.Behaviors.same;

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
    private final EventLoopGroup eventLoopGroup;
    private final IdentityPublicKey sender;
    private final PrintStream printStream;
    private final BiFunction<IdentityPublicKey, Object, CompletionStage<Void>> sendMethod;
    private final Supplier<Behavior> successBehavior;
    private final Function<Exception, Behavior> failureBehavior;
    private final LongSupplier currentTimeSupplier;
    private TestResults intervalResults;

    @SuppressWarnings("java:S107")
    PerfTestReceiver(final IdentityPublicKey sender,
                     final SessionRequest session,
                     final EventLoopGroup eventLoopGroup,
                     final PrintStream printStream,
                     final BiFunction<IdentityPublicKey, Object, CompletionStage<Void>> sendMethod,
                     final Supplier<Behavior> successBehavior,
                     final Function<Exception, Behavior> failureBehavior,
                     final LongSupplier currentTimeSupplier) {
        this.sender = requireNonNull(sender);
        this.session = requireNonNull(session);
        this.eventLoopGroup = requireNonNull(eventLoopGroup);
        this.printStream = requireNonNull(printStream);
        this.sendMethod = requireNonNull(sendMethod);
        this.successBehavior = requireNonNull(successBehavior);
        this.failureBehavior = requireNonNull(failureBehavior);
        this.currentTimeSupplier = requireNonNull(currentTimeSupplier);
    }

    public PerfTestReceiver(final IdentityPublicKey sender,
                            final SessionRequest session,
                            final EventLoopGroup eventLoopGroup,
                            final PrintStream printStream,
                            final BiFunction<IdentityPublicKey, Object, CompletionStage<Void>> sendMethod,
                            final Supplier<Behavior> successBehavior,
                            final Function<Exception, Behavior> failureBehavior) {
        this(sender, session, eventLoopGroup, printStream, sendMethod, successBehavior, failureBehavior, System::nanoTime);
    }

    public Behavior run() {
        return Behaviors.withScheduler(eventScheduler -> {
            final Future<?> sessionProgress = eventScheduler.schedulePeriodicallyEvent(new CheckTestStatus(), SESSION_PROGRESS_INTERVAL, SESSION_PROGRESS_INTERVAL);

            final int messageSize = session.getSize() + Long.BYTES + Long.BYTES;
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
                    .onMessage(Probe.class, (mySender, myPayload) -> mySender.equals(sender), (mySender, myPayload) -> {
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

                        sessionProgress.cancel(false);

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
                            sessionProgress.cancel(false);
                            return failureBehavior.apply(new Exception(String.format((Locale) null, "No message received for %.2fs. Abort session.%n", timeSinceLastMessage)));
                        }
                        else {
                            return same();
                        }
                    })
                    .onAnyEvent(event -> same())
                    .build();
        }, eventLoopGroup);
    }

    void handleProbeMessage(final AtomicLong lastMessageReceivedTime,
                            final AtomicLong lastReceivedMessageNo,
                            final AtomicLong lastOutOfOrderMessageNo,
                            final Probe probe) {
        // record prob message
        LOG.trace("Got probe message {} of {}", () -> probe.getMessageNo(), session::getMps);
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
