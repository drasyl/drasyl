/*
 * Copyright (c) 2021.
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
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * It is an intentional behavior that this scheduler ensures that the JVM is not automatically
 * terminated once all sequential program flows have been processed. A corePoolSize of 0 would
 * prevent this behavior, but would also have a negative effect on performance. The ThreadPool would
 * not start to create a new thread to process until the workQueue limit is reached. Assuming that
 * the workQueue limit is never reached, the schedule would never start processing the already
 * submitted tasks.
 */
public class DrasylSchedulerUtil {
    public static final long SHUTDOWN_TIMEOUT = 10_000L; // 10s until the schedulers are stopped immediately
    private static final Logger LOG = LoggerFactory.getLogger(DrasylSchedulerUtil.class);
    protected static volatile boolean lightSchedulerCreated = false;
    protected static volatile boolean heavySchedulerCreated = false;

    static {
        RxJavaPlugins.setErrorHandler(error -> {
            if (!(error.getCause() instanceof RejectedExecutionException)) {
                LOG.warn("", error);
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

        return FutureUtil.getCompleteOnAllOf(lightSchedulerFuture, heavySchedulerFuture);
    }

    private static class LazyLightSchedulerHolder {
        static final String BASE_NAME = "drasyl-L-";
        static final DrasylExecutor INSTANCE = new DrasylExecutor(BASE_NAME);
        static final boolean LOCK = lightSchedulerCreated = true;

        private LazyLightSchedulerHolder() {
        }
    }

    private static class LazyHeavySchedulerHolder {
        static final String BASE_NAME = "drasyl-H-";
        static final DrasylExecutor INSTANCE = new DrasylExecutor(BASE_NAME);
        static final boolean LOCK = heavySchedulerCreated = true;

        private LazyHeavySchedulerHolder() {
        }
    }

    public static final class DrasylExecutor {
        final DrasylScheduler scheduler;
        final ThreadPoolExecutor executor;

        /**
         * Generates an executor and scheduler.
         *
         * @param basename the basename for the created threads
         */
        DrasylExecutor(final String basename) {
            final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                    .setNameFormat(basename + "%d")
                    .build();
            this.executor = new ThreadPoolExecutor(
                    Math.min(1, Math.max(1, (Runtime.getRuntime().availableProcessors() / 2) - 1)),  //corePoolSize
                    Math.min(2, Math.max(1, (Runtime.getRuntime().availableProcessors() / 2) - 1)),  //maximumPoolSize
                    60L, TimeUnit.MILLISECONDS, //keepAliveTime, unit
                    new LinkedBlockingQueue<>(10_000),  //workQueue
                    threadFactory
            );
            this.scheduler = DrasylScheduler.wrap(Schedulers.from(executor), basename);
        }

        private CompletableFuture<Void> shutdown() {
            final CompletableFuture<Void> future = new CompletableFuture<>();

            new Thread(() -> {
                executor.shutdown();

                try {
                    if (!executor.awaitTermination(SHUTDOWN_TIMEOUT, TimeUnit.MILLISECONDS)) {
                        executor.shutdownNow();
                    }
                }
                catch (final InterruptedException e) {
                    executor.shutdownNow();
                    LOG.debug("", e);
                    Thread.currentThread().interrupt();
                }

                future.complete(null);
            }).start();

            return future;
        }
    }
}