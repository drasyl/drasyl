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
package org.drasyl.pipeline.handler;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.util.ReferenceCountUtil;

import java.io.PrintStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiPredicate;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Helper class to visualize the number of inbound and outbound messages per second. Can be added to
 * any position in a {@link Pipeline}.
 */
@SuppressWarnings({ "java:S110", "java:S106", "unused" })
public class MessagesThroughputHandler extends SimpleDuplexHandler<Object, Object, Address> {
    public static final Duration INTERVAL = ofSeconds(1);
    private final BiPredicate<Address, Object> consumeOutbound;
    private final BiPredicate<Address, Object> consumeInbound;
    private final LongAdder outboundMessages;
    private final LongAdder inboundMessages;
    private final Scheduler scheduler;
    private final PrintStream printStream;
    private Disposable disposable;

    MessagesThroughputHandler(final BiPredicate<Address, Object> consumeOutbound,
                              final BiPredicate<Address, Object> consumeInbound,
                              final LongAdder outboundMessages,
                              final LongAdder inboundMessages,
                              final Scheduler scheduler,
                              final PrintStream printStream,
                              final Disposable disposable) {
        this.consumeOutbound = requireNonNull(consumeOutbound);
        this.consumeInbound = requireNonNull(consumeInbound);
        this.outboundMessages = requireNonNull(outboundMessages);
        this.inboundMessages = requireNonNull(inboundMessages);
        this.scheduler = requireNonNull(scheduler);
        this.printStream = requireNonNull(printStream);
        this.disposable = disposable;
    }

    /**
     * Creates a new handler which visualizes the number of inbound and outbound messages per
     * second, consumes outbound message matching {@code consumeOutbound}, and inbound messages
     * matching {@code consumeInbound}.
     *
     * @param consumeOutbound predicate that consumes outbound messages on match
     * @param consumeInbound  predicate that consumes inbound messages on match
     * @param scheduler       scheduler on which this handler is executed
     */
    public MessagesThroughputHandler(final BiPredicate<Address, Object> consumeOutbound,
                                     final BiPredicate<Address, Object> consumeInbound,
                                     final Scheduler scheduler) {
        this(consumeOutbound, consumeInbound, new LongAdder(), new LongAdder(), scheduler, System.out, null);
    }

    /**
     * Creates a new handler which visualizes the number of inbound and outbound messages per
     * second, consumes outbound message matching {@code consumeOutbound}, and inbound messages
     * matching {@code consumeInbound}.
     *
     * @param consumeOutbound predicate that consumes outbound messages on match
     * @param consumeInbound  predicate that consumes inbound messages on match
     */
    public MessagesThroughputHandler(final BiPredicate<Address, Object> consumeOutbound,
                                     final BiPredicate<Address, Object> consumeInbound) {
        this(consumeOutbound, consumeInbound, Schedulers.single());
    }

    /**
     * Creates a new handler which visualizes the number of inbound and outbound messages per
     * second.
     */
    public MessagesThroughputHandler() {
        this((address, msg) -> false, (address, msg) -> false, Schedulers.single());
    }

    @Override
    public void onEvent(final HandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        if (event instanceof NodeUpEvent && disposable == null) {
            start();
        }
        else if ((event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) && disposable != null) {
            stop();
        }

        // passthrough event
        ctx.passEvent(event, future);
    }

    private void start() {
        final long startTime = System.currentTimeMillis();
        final AtomicLong intervalTime = new AtomicLong(startTime);
        disposable = scheduler.schedulePeriodicallyDirect(() -> {
            final long currentTime = System.currentTimeMillis();

            final double relativeIntervalStartTime = (intervalTime.get() - startTime) / 1_000.;
            final double relativeIntervalEndTime = (currentTime - startTime) / 1_000.;
            final long intervalDuration = currentTime - intervalTime.get();
            final double outboundMps = outboundMessages.sumThenReset() / 1_000. * intervalDuration;
            final double inboundMps = inboundMessages.sumThenReset() / 1_000. * intervalDuration;
            inboundMessages.reset();
            printStream.printf("%,6.2f - %,6.2f s; Tx: %,8.1f m/s; Rx: %,8.1f m/s;%n", relativeIntervalStartTime, relativeIntervalEndTime, outboundMps, inboundMps);
            intervalTime.set(currentTime);
        }, 0, INTERVAL.toMillis(), MILLISECONDS);
    }

    private void stop() {
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    protected void matchedOutbound(final HandlerContext ctx,
                                   final Address recipient,
                                   final Object msg,
                                   final CompletableFuture<Void> future) {
        outboundMessages.increment();
        if (consumeOutbound.test(recipient, msg)) {
            future.complete(null);
        }
        else {
            ctx.passOutbound(recipient, msg, future);
        }
    }

    @Override
    protected void matchedInbound(final HandlerContext ctx,
                                  final Address sender,
                                  final Object msg,
                                  final CompletableFuture<Void> future) {
        inboundMessages.increment();
        if (consumeInbound.test(sender, msg)) {
            try {
                future.complete(null);
            }
            finally {
                ReferenceCountUtil.safeRelease(msg);
            }
        }
        else {
            ctx.passInbound(sender, msg, future);
        }
    }
}
