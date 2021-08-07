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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.channel.MigrationEvent;
import org.drasyl.channel.MigrationInboundMessage;
import org.drasyl.channel.MigrationOutboundMessage;
import org.drasyl.event.Event;
import org.drasyl.pipeline.Skip;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.HandlerAdapter;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.TokenBucket;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.Null.NULL;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Traffic shaping handler that limits the number of outgoing messages per second. For this purpose,
 * all outgoing messages are first consumed from the pipeline and queued in a FIFO queue. All queued
 * messages are then asynchronously dequeued with the given rate limit and finally re-added to the
 * pipeline.
 */
public class OutboundMessagesThrottlingHandler extends HandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(OutboundMessagesThrottlingHandler.class);
    private final RateLimitedQueue queue;

    OutboundMessagesThrottlingHandler(final RateLimitedQueue queue) {
        this.queue = requireNonNull(queue);
    }

    public OutboundMessagesThrottlingHandler(final long maxEventsPerSecond) {
        this(new RateLimitedQueue(maxEventsPerSecond));
    }

    @Skip
    @SuppressWarnings("java:S112")
    public void onOutbound(final ChannelHandlerContext ctx,
                           final Address recipient,
                           final Object msg,
                           final CompletableFuture<Void> future) {
        queue.add(ctx, () -> FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new MigrationOutboundMessage<>(msg, recipient)))).combine(future));
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof MigrationOutboundMessage) {
            final MigrationOutboundMessage<?, ?> migrationMsg = (MigrationOutboundMessage<?, ?>) msg;
            final CompletableFuture<Void> future = new CompletableFuture<>();
            FutureUtil.combine(future, promise);
            final Object payload = migrationMsg.message() == NULL ? null : migrationMsg.message();
            try {
                onOutbound(ctx, migrationMsg.address(), payload, future);
            }
            catch (final Exception e) {
                future.completeExceptionally(e);
                ctx.fireExceptionCaught(e);
                ReferenceCountUtil.safeRelease(migrationMsg.message());
            }
        }
        else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        onAdded(ctx);
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        onRemoved(ctx);
    }

    @Skip
    public void onException(final ChannelHandlerContext ctx, final Exception cause) {
        ctx.fireExceptionCaught(cause);
    }

    @SuppressWarnings("java:S112")
    @Skip
    public void onInbound(final ChannelHandlerContext ctx,
                          final Address sender,
                          final Object msg,
                          final CompletableFuture<Void> future) throws Exception {
        ctx.fireChannelRead(new MigrationInboundMessage<>(msg, sender, future));
    }

    @Skip
    public void onEvent(final ChannelHandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        ctx.fireUserEventTriggered(new MigrationEvent(event, future));
    }

    /**
     * Do nothing by default, sub-classes may override this method.
     */
    public void onAdded(final ChannelHandlerContext ctx) {
        // NOOP
    }

    /**
     * Do nothing by default, sub-classes may override this method.
     */
    public void onRemoved(final ChannelHandlerContext ctx) {
        // NOOP
    }

    public static class RateLimitedQueue {
        public final Queue<Runnable> queue;
        public final TokenBucket tokenBucket;
        private final AtomicBoolean queueConsumer;

        public RateLimitedQueue(final long maxEventsPerSecond) {
            queue = new LinkedList<>();
            final Duration refillInterval = Duration.ofSeconds(1).dividedBy(requirePositive(maxEventsPerSecond, "maxEventsPerSecond must be a positive number"));
            final boolean doBusyWait = refillInterval.toMillis() < 20;
            tokenBucket = new TokenBucket(1, refillInterval, doBusyWait);
            queueConsumer = new AtomicBoolean(false);
        }

        public synchronized void add(final ChannelHandlerContext ctx, final Runnable value) {
            queue.add(value);
            LOG.trace("New message has been enqueued. Messages in queue: {}", queue::size);
            if (queueConsumer.compareAndSet(false, true)) {
                ctx.executor().execute(new QueueConsumer(this));
            }
        }

        public boolean tryConsume() {
            final Runnable runnable = queue.poll();
            if (runnable != null) {
                tokenBucket.consume();
                LOG.trace("Consume message. Messages in queue: {}", queue::size);
                runnable.run();
                return true;
            }
            else {
                return false;
            }
        }
    }

    public static class QueueConsumer implements Runnable {
        private final RateLimitedQueue queue;

        QueueConsumer(final RateLimitedQueue queue) {
            this.queue = requireNonNull(queue);
        }

        @Override
        public void run() {
            LOG.trace("Queue Consumer started.");
            for (; ; ) {
                if (!queue.tryConsume()) {
                    break;
                }
            }
            queue.queueConsumer.set(false);
            LOG.trace("Queue Consumer is done.");
        }
    }
}
