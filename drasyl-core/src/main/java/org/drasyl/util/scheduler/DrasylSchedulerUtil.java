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
package org.drasyl.util.scheduler;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.util.internal.SystemPropertyUtil;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * It is an intentional behavior that this scheduler ensures that the JVM is not automatically
 * terminated once all sequential program flows have been processed. A corePoolSize of 0 would
 * prevent this behavior, but would also have a negative effect on performance. The ThreadPool would
 * not start to create a new thread to process until the workQueue limit is reached. Assuming that
 * the workQueue limit is never reached, the schedule would never start processing the already
 * submitted tasks.
 */
public final class DrasylSchedulerUtil {
    // 10s until the schedulers are stopped immediately
    public static final Duration SHUTDOWN_TIMEOUT = ofSeconds(10);
    private static final Logger LOG = LoggerFactory.getLogger(DrasylSchedulerUtil.class);
    static volatile boolean lightSchedulerCreated;
    static volatile boolean heavySchedulerCreated;

    static {
        RxJavaPlugins.setErrorHandler(error -> {
            if (!(error.getCause() instanceof RejectedExecutionException)) {
                LOG.warn(error);
            }
        });
    }

    private DrasylSchedulerUtil() {
        // util class
    }

    /**
     * Use this {@link DrasylScheduler} for fast and light task that does not do heavy
     * computations.
     *
     * @return a {@link DrasylScheduler} for fast and light tasks
     */
    public static DrasylScheduler getInstanceLight() {
        return LazyLightSchedulerHolder.INSTANCE.scheduler;
    }

    /**
     * Use this {@link DrasylScheduler} for slow and heavy task that does do longer computations.
     *
     * @return a {@link DrasylScheduler} for slow and heavy tasks
     */
    public static DrasylScheduler getInstanceHeavy() {
        return LazyHeavySchedulerHolder.INSTANCE.scheduler;
    }

    /**
     * Shutdown the two schedulers.
     *
     * <p>
     * <b>This operation cannot be undone. After performing this operation, no new task can
     * be submitted!</b>
     * </p>
     */
    public static CompletableFuture<Void> shutdown() {
        final FutureCombiner combiner = FutureCombiner.getInstance();

        if (lightSchedulerCreated) {
            combiner.add(LazyLightSchedulerHolder.INSTANCE.shutdown());
        }

        if (heavySchedulerCreated) {
            combiner.add(LazyHeavySchedulerHolder.INSTANCE.shutdown());
        }

        return combiner.combine(new CompletableFuture<>());
    }

    private static final class LazyLightSchedulerHolder {
        static final String BASE_NAME = "drasyl-L-";
        static final int SIZE;

        // pool should have at least all available processors minus two threads
        static {
            SIZE = SystemPropertyUtil.getInt("org.drasyl.scheduler.light", Math.max(2, Runtime.getRuntime().availableProcessors() - 2));
            LOG.debug("Light scheduler pool size: {}", SIZE);
        }

        static final DrasylExecutor INSTANCE = new DrasylExecutor(BASE_NAME, SIZE, SIZE);
        @SuppressWarnings("unused")
        static final boolean LOCK = lightSchedulerCreated = true;

        private LazyLightSchedulerHolder() {
        }
    }

    private static final class LazyHeavySchedulerHolder {
        static final String BASE_NAME = "drasyl-H-";
        static final int CORE_SIZE = 1;
        static final int MAX_SIZE;

        // pool should have at least 1 and max 10% of available processors
        static {
            MAX_SIZE = SystemPropertyUtil.getInt("org.drasyl.scheduler.heavy", Math.max(2, (int) Math.ceil(Runtime.getRuntime().availableProcessors() * 0.1)));
            LOG.debug("Heavy scheduler max pool size: {}", MAX_SIZE);
        }

        static final DrasylExecutor INSTANCE = new DrasylExecutor(BASE_NAME, CORE_SIZE, MAX_SIZE);
        @SuppressWarnings("unused")
        static final boolean LOCK = heavySchedulerCreated = true;

        private LazyHeavySchedulerHolder() {
        }
    }

    @SuppressWarnings("java:S2972")
    public static final class DrasylExecutor {
        private static final int QUEUE_SIZE = 10_000;
        final DrasylScheduler scheduler;
        final ThreadPoolExecutor executor;

        /**
         * Generates an executor and scheduler.
         *
         * @param basename the basename for the created threads
         */
        @SuppressWarnings("java:S139")
        public DrasylExecutor(final String basename,
                              final int corePoolSize,
                              final int maxPoolSize) {
            final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                    .setNameFormat(basename + "%d")
                    .build();
            // pool should have at least "corePoolSize of available processors" threads (but at
            // least one) and a maximum of "maxPoolSize of available processors" (but at least
            // two) threads
            this.executor = new ThreadPoolExecutor(
                    Math.max(1, corePoolSize), // corePoolSize
                    Math.max(2, maxPoolSize), //maximumPoolSize
                    60, SECONDS, //keepAliveTime, unit
                    new LinkedBlockingQueue<>(QUEUE_SIZE),  //workQueue
                    threadFactory
            );
            this.scheduler = DrasylScheduler.wrap(Schedulers.from(executor), basename);
        }

        public CompletableFuture<Void> shutdown() {
            final CompletableFuture<Void> future = new CompletableFuture<>();

            new Thread(() -> {
                executor.shutdown();

                try {
                    if (!executor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), MILLISECONDS)) {
                        executor.shutdownNow();
                    }
                }
                catch (final InterruptedException e) {
                    executor.shutdownNow();
                    LOG.debug(e);
                    Thread.currentThread().interrupt();
                }

                future.complete(null);
            }).start();

            return future;
        }

        public static int getQueueSize() {
            return QUEUE_SIZE;
        }

        public DrasylScheduler getScheduler() {
            return scheduler;
        }

        public ThreadPoolExecutor getExecutor() {
            return executor;
        }
    }
}
