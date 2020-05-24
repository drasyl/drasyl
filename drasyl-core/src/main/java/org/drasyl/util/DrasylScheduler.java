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

import java.util.concurrent.*;

/**
 * It is an intentional behavior that this scheduler ensures that the JVM is not automatically
 * terminated once all sequential program flows have been processed. A corePoolSize of 0 would
 * prevent this behavior, but would also have a negative effect on performance. The ThreadPool would
 * not start to create a new thread to process until the workQueue limit is reached. Assuming that
 * the workQueue limit is never reached, the schedule would never start processing the already
 * submitted tasks.
 */
public class DrasylScheduler {
    private static final Scheduler scheduler;

    static {
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("Drasyl-ThreadPool-%d")
                .build();
        Executor executor = new ThreadPoolExecutor(
                Math.min(1, Math.max(1, Runtime.getRuntime().availableProcessors() / 3)),  //corePoolSize
                Math.min(2, Math.max(1, Runtime.getRuntime().availableProcessors() / 3)),  //maximumPoolSize
                60L, TimeUnit.MILLISECONDS, //keepAliveTime, unit
                new LinkedBlockingQueue<>(1000),  //workQueue
                threadFactory
        );
        scheduler = Schedulers.from(executor);
    }

    private DrasylScheduler() {
    }

    public static Scheduler getInstance() {
        return scheduler;
    }
}