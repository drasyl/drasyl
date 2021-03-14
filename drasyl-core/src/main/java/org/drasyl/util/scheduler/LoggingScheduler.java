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

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.annotation.NonNull;
import org.drasyl.util.logging.LogLevel;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * A {@link DrasylScheduler} that logs all tasks via {@link Logger}. By default, all events are
 * logged at <tt>DEBUG</tt> level.
 */
@SuppressWarnings("java:S1192")
public class LoggingScheduler extends DrasylScheduler {
    @SuppressWarnings("java:S1312")
    private final Logger logger;
    private final LogLevel level;

    public LoggingScheduler(final Scheduler scheduler, final String schedulerNamePrefix) {
        super(scheduler, schedulerNamePrefix);
        this.logger = LoggerFactory.getLogger(this.getClass());
        this.level = LogLevel.DEBUG;
    }

    public static DrasylScheduler wrap(final Scheduler scheduler,
                                       final String schedulerNamePrefix) {
        return new LoggingScheduler(scheduler, schedulerNamePrefix);
    }

    @Override
    @NonNull
    public Disposable scheduleDirect(@NonNull final Runnable run) {
        logger.log(level, "[{}] Schedule directly: {}", schedulerNamePrefix, run);
        return super.scheduleDirect(() -> {
            logger.log(level, "[{}] Start {}", schedulerNamePrefix, run);
            run.run();
            logger.log(level, "[{}] Done {}", schedulerNamePrefix, run);
        });
    }

    @Override
    @NonNull
    public Disposable scheduleDirect(@NonNull final Runnable run,
                                     final long delay,
                                     @NonNull final TimeUnit unit) {
        logger.log(level, "[{}] Schedule with delay of {} {}: {}", schedulerNamePrefix, delay, unit, run);
        return super.scheduleDirect(() -> {
            logger.log(level, "[{}] Start {}", schedulerNamePrefix, run);
            run.run();
            logger.log(level, "[{}] Done {}", schedulerNamePrefix, run);
        }, delay, unit);
    }

    @Override
    @NonNull
    public Disposable schedulePeriodicallyDirect(@NonNull final Runnable run,
                                                 final long initialDelay,
                                                 final long period,
                                                 @NonNull final TimeUnit unit) {
        logger.log(level, "[{}] Schedule every {} {} with initial delay of {} {}: {}", schedulerNamePrefix, period, unit, initialDelay, unit, run);
        return super.schedulePeriodicallyDirect(() -> {
            logger.log(level, "[{}] Start {}", schedulerNamePrefix, run);
            run.run();
            logger.log(level, "[{}] Done {}", schedulerNamePrefix, run);
        }, initialDelay, period, unit);
    }
}
