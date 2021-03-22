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
        final CompletableFuture<Void> lightSchedulerFuture;
        final CompletableFuture<Void> heavySchedulerFuture;

        if (lightSchedulerCreated) {
            lightSchedulerFuture = LazyLightSchedulerHolder.INSTANCE.shutdown();
        }
        else {
            lightSchedulerFuture = CompletableFuture.completedFuture(null);
        }

        if (heavySchedulerCreated) {
            heavySchedulerFuture = LazyHeavySchedulerHolder.INSTANCE.shutdown();
        }
        else {
            heavySchedulerFuture = CompletableFuture.completedFuture(null);
        }

        return FutureCombiner.getInstance()
                .addAll(lightSchedulerFuture, heavySchedulerFuture)
                .combine(new CompletableFuture<>());
    }

    private static final class LazyLightSchedulerHolder {
        static final String BASE_NAME = "drasyl-L-";
        static final int SIZE;

        static {
            SIZE = SystemPropertyUtil.getInt("org.drasyl.scheduler.light", Math.max(2, Runtime.getRuntime().availableProcessors() - 2));
            LOG.debug("Light scheduler pool size: {}", SIZE);
        }

        // pool should have at least all available processors minus two threads
        static final DrasylExecutor INSTANCE = new DrasylExecutor(BASE_NAME, SIZE, SIZE);
        static final boolean LOCK = lightSchedulerCreated = true;

        private LazyLightSchedulerHolder() {
        }
    }

    private static final class LazyHeavySchedulerHolder {
        static final String BASE_NAME = "drasyl-H-";
        static final int CORE_SIZE = 1;
        static final int MAX_SIZE;

        static {
            MAX_SIZE = SystemPropertyUtil.getInt("org.drasyl.scheduler.heavy", Math.max(2, (int) Math.ceil(Runtime.getRuntime().availableProcessors() * 0.1)));
            LOG.debug("Heavy scheduler max pool size: {}", MAX_SIZE);
        }

        // pool should have at least 1 and max 10% of available processors
        static final DrasylExecutor INSTANCE = new DrasylExecutor(BASE_NAME, CORE_SIZE, MAX_SIZE);
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
        DrasylExecutor(final String basename, final int corePoolSize, final int maxPoolSize) {
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

        private CompletableFuture<Void> shutdown() {
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
    }
}
