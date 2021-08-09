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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
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
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Traffic shaping handler that limits the number of outgoing messages per second. For this purpose,
 * all outgoing messages are first consumed from the pipeline and queued in a FIFO queue. All queued
 * messages are then asynchronously dequeued with the given rate limit and finally re-added to the
 * pipeline.
 */
public class OutboundMessagesThrottlingHandler extends ChannelOutboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(OutboundMessagesThrottlingHandler.class);
    private final RateLimitedQueue queue;

    OutboundMessagesThrottlingHandler(final RateLimitedQueue queue) {
        this.queue = requireNonNull(queue);
    }

    public OutboundMessagesThrottlingHandler(final long maxEventsPerSecond) {
        this(new RateLimitedQueue(maxEventsPerSecond));
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof AddressedMessage) {
            final AddressedMessage<?, ? extends org.drasyl.pipeline.address.Address> migrationMsg = (AddressedMessage<?, ? extends org.drasyl.pipeline.address.Address>) msg;
            final CompletableFuture<Void> future = new CompletableFuture<>();
            FutureUtil.combine(future, promise);
            try {
                queue.add(ctx, () -> FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(migrationMsg))).combine(future));
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
