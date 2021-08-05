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
package org.drasyl.channel;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ProgressivePromise;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.concurrent.SucceededFuture;
import io.reactivex.rxjava3.annotations.NonNull;
import org.drasyl.pipeline.Handler;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.util.Objects.requireNonNull;

/**
 * A wrapper used to add {@link Handler} to a {@link io.netty.channel.Channel}.
 */
public class MigrationScheduler implements EventExecutor {
    private final EventExecutor executor;

    public MigrationScheduler(final EventExecutor executor) {
        this.executor = requireNonNull(executor);
    }

    @Override
    public boolean isShuttingDown() {
        return executor.isShuttingDown();
    }

    @Override
    public Future<?> shutdownGracefully() {
        return executor.shutdownGracefully();
    }

    @Override
    public Future<?> shutdownGracefully(final long quietPeriod,
                                        final long timeout,
                                        final TimeUnit unit) {
        return executor.shutdownGracefully(quietPeriod, timeout, unit);
    }

    @Override
    public Future<?> terminationFuture() {
        return executor.terminationFuture();
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return executor.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return executor.isTerminated();
    }

    @Override
    public boolean awaitTermination(final long timeout,
                                    final TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    @Override
    public EventExecutor next() {
        return executor.next();
    }

    @Override
    public Iterator<EventExecutor> iterator() {
        return executor.iterator();
    }

    @Override
    public Future<?> submit(final Runnable task) {
        return executor.submit(task);
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return executor.invokeAll(tasks);
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks,
                                                              final long timeout,
                                                              final TimeUnit unit) throws InterruptedException {
        return executor.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return executor.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks,
                           final long timeout,
                           final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return executor.invokeAny(tasks, timeout, unit);
    }

    @Override
    public <T> Future<T> submit(final Runnable task, final T result) {
        return executor.submit(task, result);
    }

    @Override
    public <T> Future<T> submit(final Callable<T> task) {
        return executor.submit(task);
    }

    @Override
    public ScheduledFuture<?> schedule(final Runnable command,
                                       final long delay,
                                       final TimeUnit unit) {
        return executor.schedule(command, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(final Callable<V> callable,
                                           final long delay,
                                           final TimeUnit unit) {
        return executor.schedule(callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command,
                                                  final long initialDelay,
                                                  final long period,
                                                  final TimeUnit unit) {
        return executor.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command,
                                                     final long initialDelay,
                                                     final long delay,
                                                     final TimeUnit unit) {
        return executor.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    @Override
    public EventExecutorGroup parent() {
        return executor.parent();
    }

    @Override
    public boolean inEventLoop() {
        return executor.inEventLoop();
    }

    @Override
    public boolean inEventLoop(final Thread thread) {
        return executor.inEventLoop(thread);
    }

    @Override
    public <V> Promise<V> newPromise() {
        return executor.newPromise();
    }

    @Override
    public <V> ProgressivePromise<V> newProgressivePromise() {
        return executor.newProgressivePromise();
    }

    @Override
    public <V> Future<V> newSucceededFuture(final V result) {
        return executor.newSucceededFuture(result);
    }

    @Override
    public <V> Future<V> newFailedFuture(final Throwable cause) {
        return executor.newFailedFuture(cause);
    }

    @Override
    public void execute(@NonNull final Runnable command) {
        executor.execute(command);
    }

    public @NonNull Future scheduleDirect(@NonNull final Runnable run) {
        execute(run);
        return new SucceededFuture<>(null, null);
    }

    public @NonNull Future<?> scheduleDirect(@NonNull final Runnable run,
                                             final long delay,
                                             @NonNull final TimeUnit unit) {
        return schedule(run, delay, unit);
    }

    public @NonNull Future<?> schedulePeriodicallyDirect(@NonNull final Runnable run,
                                                         final long initialDelay,
                                                         final long period,
                                                         @NonNull final TimeUnit unit) {
        return scheduleAtFixedRate(run, initialDelay, period, unit);
    }
}
