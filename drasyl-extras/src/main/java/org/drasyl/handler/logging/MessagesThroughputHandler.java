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
package org.drasyl.handler.logging;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.ScheduledFuture;

import java.io.PrintStream;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiPredicate;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Helper class to visualize the number of inbound and outbound messages per second. Can be added to
 * any position in a {@link io.netty.channel.ChannelPipeline}.
 */
@SuppressWarnings({ "java:S110", "java:S106", "unused" })
public class MessagesThroughputHandler extends ChannelDuplexHandler {
    public static final Duration INTERVAL = ofSeconds(1);
    private final BiPredicate<SocketAddress, Object> consumeOutbound;
    private final BiPredicate<SocketAddress, Object> consumeInbound;
    private final LongAdder outboundMessages;
    private final LongAdder inboundMessages;
    private final PrintStream printStream;
    private ScheduledFuture<?> scheduledFuture;

    MessagesThroughputHandler(final BiPredicate<SocketAddress, Object> consumeOutbound,
                              final BiPredicate<SocketAddress, Object> consumeInbound,
                              final LongAdder outboundMessages,
                              final LongAdder inboundMessages,
                              final PrintStream printStream,
                              final ScheduledFuture<?> scheduledFuture) {
        this.consumeOutbound = requireNonNull(consumeOutbound);
        this.consumeInbound = requireNonNull(consumeInbound);
        this.outboundMessages = requireNonNull(outboundMessages);
        this.inboundMessages = requireNonNull(inboundMessages);
        this.printStream = requireNonNull(printStream);
        this.scheduledFuture = scheduledFuture;
    }

    /**
     * Creates a new handler which visualizes the number of inbound and outbound messages per
     * second, consumes outbound message matching {@code consumeOutbound}, and inbound messages
     * matching {@code consumeInbound}.
     *
     * @param consumeOutbound predicate that consumes outbound messages on match
     * @param consumeInbound  predicate that consumes inbound messages on match
     * @param eventLoopGroup  eventLoopGroup on which this handler is executed
     */
    public MessagesThroughputHandler(final BiPredicate<SocketAddress, Object> consumeOutbound,
                                     final BiPredicate<SocketAddress, Object> consumeInbound,
                                     final EventLoopGroup eventLoopGroup) {
        this(consumeOutbound, consumeInbound, new LongAdder(), new LongAdder(), System.out, null);
    }

    /**
     * Creates a new handler which visualizes the number of inbound and outbound messages per
     * second, consumes outbound message matching {@code consumeOutbound}, and inbound messages
     * matching {@code consumeInbound}.
     *
     * @param consumeOutbound predicate that consumes outbound messages on match
     * @param consumeInbound  predicate that consumes inbound messages on match
     */
    public MessagesThroughputHandler(final BiPredicate<SocketAddress, Object> consumeOutbound,
                                     final BiPredicate<SocketAddress, Object> consumeInbound) {
        this(consumeOutbound, consumeInbound, new NioEventLoopGroup(1));
    }

    /**
     * Creates a new handler which visualizes the number of inbound and outbound messages per
     * second.
     */
    public MessagesThroughputHandler() {
        this((address, msg) -> false, (address, msg) -> false);
    }

    private void start(final ChannelHandlerContext ctx) {
        final long startTime = System.currentTimeMillis();
        final AtomicLong intervalTime = new AtomicLong(startTime);
        scheduledFuture = ctx.executor().scheduleWithFixedDelay(() -> {
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
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof AddressedEnvelope) {
            outboundMessages.increment();
            if (consumeOutbound.test(((AddressedEnvelope<?, ?>) msg).recipient(), ((AddressedEnvelope<?, ?>) msg).content())) {
                promise.setSuccess();
            }
            else {
                ctx.write(msg, promise);
            }
        }
        else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof AddressedEnvelope) {
            inboundMessages.increment();
            if (!consumeInbound.test(((AddressedEnvelope<?, ?>) msg).sender(), ((AddressedEnvelope<?, ?>) msg).content())) {
                ctx.fireChannelRead(msg);
            }
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        start(ctx);

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        stop();

        ctx.fireChannelInactive();
    }
}
