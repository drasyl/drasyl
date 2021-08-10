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
package org.drasyl.channel;

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
 * any position in a {@link Pipeline}.
 */
@SuppressWarnings({ "java:S110", "java:S106", "unused" })
public class MessagesThroughputHandler extends ChannelDuplexHandler {
    public static final Duration INTERVAL = ofSeconds(1);
    private final BiPredicate<SocketAddress, Object> consumeOutbound;
    private final BiPredicate<SocketAddress, Object> consumeInbound;
    private final LongAdder outboundMessages;
    private final LongAdder inboundMessages;
    private final EventLoopGroup eventLoopGroup;
    private final PrintStream printStream;
    private ScheduledFuture<?> disposable;

    MessagesThroughputHandler(final BiPredicate<SocketAddress, Object> consumeOutbound,
                              final BiPredicate<SocketAddress, Object> consumeInbound,
                              final LongAdder outboundMessages,
                              final LongAdder inboundMessages,
                              final EventLoopGroup eventLoopGroup,
                              final PrintStream printStream,
                              final ScheduledFuture<?> disposable) {
        this.consumeOutbound = requireNonNull(consumeOutbound);
        this.consumeInbound = requireNonNull(consumeInbound);
        this.outboundMessages = requireNonNull(outboundMessages);
        this.inboundMessages = requireNonNull(inboundMessages);
        this.eventLoopGroup = requireNonNull(eventLoopGroup);
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
     * @param eventLoopGroup  eventLoopGroup on which this handler is executed
     */
    public MessagesThroughputHandler(final BiPredicate<SocketAddress, Object> consumeOutbound,
                                     final BiPredicate<SocketAddress, Object> consumeInbound,
                                     final EventLoopGroup eventLoopGroup) {
        this(consumeOutbound, consumeInbound, new LongAdder(), new LongAdder(), eventLoopGroup, System.out, null);
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

    private void start() {
        final long startTime = System.currentTimeMillis();
        final AtomicLong intervalTime = new AtomicLong(startTime);
        disposable = eventLoopGroup.scheduleAtFixedRate(() -> {
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
            disposable.cancel(false);
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) throws Exception {
        if (msg instanceof AddressedMessage) {
            outboundMessages.increment();
            if (consumeOutbound.test(((AddressedMessage<?, ?>) msg).address(), ((AddressedMessage<?, ?>) msg).message())) {
                promise.setSuccess();
            }
            else {
                ctx.writeAndFlush(msg, promise);
            }
        }
        else {
            super.write(ctx, msg, promise);
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof AddressedMessage) {
            inboundMessages.increment();
            if (!consumeInbound.test(((AddressedMessage<?, ?>) msg).address(), ((AddressedMessage<?, ?>) msg).message())) {
                ctx.fireChannelRead(msg);
            }
        }
        else {
            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        start();

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        stop();

        super.channelInactive(ctx);
    }
}
