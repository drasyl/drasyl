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
