/*
 * Copyright (c) 2020-2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.pipeline;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
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
    public static final String MESSAGES_THROUGHPUT_HANDLER = "MESSAGES_THROUGHPUT_HANDLER";
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
    public void eventTriggered(final HandlerContext ctx,
                               final Event event,
                               final CompletableFuture<Void> future) {
        if (event instanceof NodeUpEvent && disposable == null) {
            start();
        }
        else if ((event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) && disposable != null) {
            stop();
        }

        // passthrough event
        ctx.fireEventTriggered(event, future);
    }

    private void start() {
        final long startTime = System.currentTimeMillis();
        final AtomicLong intervalTime = new AtomicLong(startTime);
        disposable = scheduler.schedulePeriodicallyDirect(() -> {
            final long currentTime = System.currentTimeMillis();

            final double relativeIntervalStartTime = (intervalTime.get() - startTime) / 1_000f;
            final double relativeIntervalEndTime = (currentTime - startTime) / 1_000f;
            final long intervalDuration = currentTime - intervalTime.get();
            final double outboundMps = outboundMessages.sumThenReset() / 1_000f * intervalDuration;
            final double inboundMps = inboundMessages.sumThenReset() / 1_000f * intervalDuration;
            inboundMessages.reset();
            printStream.printf("%,6.2f - %,6.2f s; Tx: %,8.1f m/s; Rx: %,8.1f m/s;", relativeIntervalStartTime, relativeIntervalEndTime, outboundMps, inboundMps);
            intervalTime.set(currentTime);
        }, 0, INTERVAL.toMillis(), MILLISECONDS);
    }

    private void stop() {
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final Address recipient,
                                final Object msg,
                                final CompletableFuture<Void> future) {
        outboundMessages.increment();
        if (consumeOutbound.test(recipient, msg)) {
            future.complete(null);
        }
        else {
            ctx.write(recipient, msg, future);
        }
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
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
            ctx.fireRead(sender, msg, future);
        }
    }
}
