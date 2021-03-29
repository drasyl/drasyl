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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Function;
import org.drasyl.annotation.NonNull;

import java.util.concurrent.TimeUnit;

public class DrasylScheduler extends Scheduler {
    protected final String schedulerNamePrefix;
    private final Scheduler wrappedScheduler;

    protected DrasylScheduler(final Scheduler scheduler, final String schedulerNamePrefix) {
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
    public @NonNull
    Worker createWorker() {
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
    public @NonNull
    Disposable scheduleDirect(@NonNull final Runnable run) {
        return this.wrappedScheduler.scheduleDirect(run);
    }

    @Override
    public @NonNull
    Disposable scheduleDirect(@NonNull final Runnable run,
                              final long delay,
                              @NonNull final TimeUnit unit) {
        return this.wrappedScheduler.scheduleDirect(run, delay, unit);
    }

    @Override
    public @NonNull
    Disposable schedulePeriodicallyDirect(@NonNull final Runnable run,
                                          final long initialDelay,
                                          final long period,
                                          @NonNull final TimeUnit unit) {
        return this.wrappedScheduler.schedulePeriodicallyDirect(run, initialDelay, period, unit);
    }

    @Override
    public @NonNull
    <S extends Scheduler & Disposable> S when(@NonNull final Function<Flowable<Flowable<Completable>>, Completable> combine) {
        return this.wrappedScheduler.when(combine);
    }
}
