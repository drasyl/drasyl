/*
 * Copyright (c) 2020.
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
package org.drasyl.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
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
public class DrasylScheduler {
    public static final long SHUTDOWN_TIMEOUT = 10_000L; // 10s until the schedulers are stopped immediately
    private static final Logger LOG = LoggerFactory.getLogger(DrasylScheduler.class);
    private static Scheduler lightScheduler;
    private static Scheduler heavyScheduler;
    private static ThreadPoolExecutor lightExecutor;
    private static ThreadPoolExecutor heavyExecutor;

    static {
        start();
    }

    private DrasylScheduler() {
        // util class
    }

    /**
     * Use this {@link Scheduler} for fast and light task that does not do heavy computations.
     *
     * @return a {@link Scheduler} for fast and light tasks
     */
    public static Scheduler getInstanceLight() {
        return lightScheduler;
    }

    /**
     * Use this {@link Scheduler} for slow and heavy task that does not do longer computations.
     *
     * @return a {@link Scheduler} for slow and heavy tasks
     */
    public static Scheduler getInstanceHeavy() {
        return heavyScheduler;
    }

    /**
     * Shutdown the two schedulers.
     */
    public static void shutdown() {
        shutdownThreadPoolExecutor(lightExecutor);
        shutdownThreadPoolExecutor(heavyExecutor);
    }

    private static void shutdownThreadPoolExecutor(ThreadPoolExecutor executor) {
        executor.shutdown();

        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        }
        catch (InterruptedException e) {
            executor.shutdownNow();
            LOG.debug("", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Starts the two schedulers.
     */
    public static void start() {
        Pair<ThreadPoolExecutor, Scheduler> light = generate("Drasyl-Light-Task-ThreadPool-%d");
        lightExecutor = light.first();
        lightScheduler = light.second();

        Pair<ThreadPoolExecutor, Scheduler> heavy = generate("Drasyl-Heavy-Task-ThreadPool-%d");
        heavyExecutor = heavy.first();
        heavyScheduler = heavy.second();
    }

    /**
     * Generates an executor and scheduler.
     *
     * @param name the name format for the created threads
     * @return an executor and the corresponding scheduler
     */
    private static Pair<ThreadPoolExecutor, Scheduler> generate(String name) {
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(name)
                .build();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                Math.min(1, Math.max(1, Runtime.getRuntime().availableProcessors() / 3)),  //corePoolSize
                Math.min(2, Math.max(1, Runtime.getRuntime().availableProcessors() / 3)),  //maximumPoolSize
                60L, TimeUnit.MILLISECONDS, //keepAliveTime, unit
                new LinkedBlockingQueue<>(1000),  //workQueue
                threadFactory
        );
        Scheduler scheduler = Schedulers.from(executor);

        return Pair.of(executor, scheduler);
    }
}