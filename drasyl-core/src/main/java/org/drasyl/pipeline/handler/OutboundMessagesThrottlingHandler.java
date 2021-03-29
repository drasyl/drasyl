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

import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.HandlerAdapter;
import org.drasyl.util.TokenBucket;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

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

    @Override
    public void onOutbound(final HandlerContext ctx,
                           final Address recipient,
                           final Object msg,
                           final CompletableFuture<Void> future) {
        queue.add(ctx, () -> ctx.passOutbound(recipient, msg, future));
    }

    public static class RateLimitedQueue {
        public final Queue<Runnable> queue;
        public final TokenBucket tokenBucket;
        private Disposable queueConsumer;

        public RateLimitedQueue(final long maxEventsPerSecond) {
            if (maxEventsPerSecond < 1) {
                throw new IllegalArgumentException("maxEventsPerSecond must be a positive number.");
            }
            queue = new LinkedList<>();
            final Duration refillInterval = Duration.ofSeconds(1).dividedBy(maxEventsPerSecond);
            final boolean doBusyWait = refillInterval.toMillis() < 20;
            tokenBucket = new TokenBucket(1, refillInterval, doBusyWait);
        }

        public synchronized void add(final HandlerContext ctx, final Runnable value) {
            queue.add(value);
            LOG.trace("New message has been enqueued. Messages in queue: {}", queue::size);
            if (queueConsumer == null) {
                this.queueConsumer = ctx.dependentScheduler().scheduleDirect(new QueueConsumer(this));
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
            queue.queueConsumer = null;
            LOG.trace("Queue Consumer is done.");
        }
    }
}
