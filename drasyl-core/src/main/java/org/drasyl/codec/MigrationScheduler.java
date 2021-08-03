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
package org.drasyl.codec;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.SucceededFuture;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.pipeline.Handler;

import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * A wrapper used to add {@link Handler} to a {@link io.netty.channel.Channel}.
 */
public class MigrationScheduler extends Scheduler {
    private final EventExecutor executor;

    public MigrationScheduler(final EventExecutor executor) {
        this.executor = requireNonNull(executor);
    }

    @Override
    public @NonNull Worker createWorker() {
        throw new RuntimeException("not implemented yet"); // NOSONAR
    }

    @Override
    public @NonNull Disposable scheduleDirect(@NonNull final Runnable run) {
        executor.execute(run);
        return new MigrationDisposable(new SucceededFuture<>(null, null));
    }

    @Override
    public @NonNull Disposable scheduleDirect(@NonNull final Runnable run,
                                              final long delay,
                                              @NonNull final TimeUnit unit) {
        return new MigrationDisposable(executor.schedule(run, delay, unit));
    }

    @Override
    public @NonNull Disposable schedulePeriodicallyDirect(@NonNull final Runnable run,
                                                          final long initialDelay,
                                                          final long period,
                                                          @NonNull final TimeUnit unit) {
        return new MigrationDisposable(executor.scheduleAtFixedRate(run, initialDelay, period, unit));
    }
}
