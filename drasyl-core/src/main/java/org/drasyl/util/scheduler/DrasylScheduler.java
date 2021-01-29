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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Function;
import org.drasyl.annotation.NonNull;

import java.util.concurrent.TimeUnit;

public class DrasylScheduler extends Scheduler {
    private final String schedulerNamePrefix;
    private final Scheduler wrappedScheduler;

    private DrasylScheduler(final Scheduler scheduler, final String schedulerNamePrefix) {
        this.wrappedScheduler = scheduler;
        this.schedulerNamePrefix = schedulerNamePrefix;
    }

    /**
     * Wraps the given {@code scheduler} and returns a {@link DrasylScheduler} instance.
     *
     * @param scheduler           reactivex.rxjava3 scheduler
     * @param schedulerNamePrefix the scheduler name prefix
     * @return wrapped scheduler
     */
    public static DrasylScheduler wrap(final Scheduler scheduler,
                                       final String schedulerNamePrefix) {
        return new DrasylScheduler(scheduler, schedulerNamePrefix);
    }

    /**
     * This method returns {@code true} if the method was called from this scheduler.
     *
     * @return {@code} true if the method was called from this scheduler
     */
    public boolean isCalledFromThisScheduler() {
        return Thread.currentThread().getName().startsWith(schedulerNamePrefix);
    }

    @Override
    public @NonNull Worker createWorker() {
        return this.wrappedScheduler.createWorker();
    }

    @Override
    public long now(@NonNull final TimeUnit unit) {
        return this.wrappedScheduler.now(unit);
    }

    @Override
    public void start() {
        this.wrappedScheduler.start();
    }

    @Override
    public void shutdown() {
        this.wrappedScheduler.shutdown();
    }

    @Override
    public @NonNull Disposable scheduleDirect(@NonNull final Runnable run) {
        return this.wrappedScheduler.scheduleDirect(run);
    }

    @Override
    public @NonNull Disposable scheduleDirect(@NonNull final Runnable run,
                                              final long delay,
                                              @NonNull final TimeUnit unit) {
        return this.wrappedScheduler.scheduleDirect(run, delay, unit);
    }

    @Override
    public @NonNull Disposable schedulePeriodicallyDirect(@NonNull final Runnable run,
                                                          final long initialDelay,
                                                          final long period,
                                                          @NonNull final TimeUnit unit) {
        return this.wrappedScheduler.schedulePeriodicallyDirect(run, initialDelay, period, unit);
    }

    @Override
    public @NonNull <S extends Scheduler & Disposable> S when(@NonNull final Function<Flowable<Flowable<Completable>>, Completable> combine) {
        return this.wrappedScheduler.when(combine);
    }
}
