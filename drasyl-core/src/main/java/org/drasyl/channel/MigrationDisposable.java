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

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.drasyl.annotation.NonNull;
import org.drasyl.pipeline.Handler;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.util.Objects.requireNonNull;

/**
 * A wrapper used to add {@link Handler} to a {@link io.netty.channel.Channel}.
 */
public class MigrationDisposable<V> implements Future<V> {
    private final Future<V> future;

    public MigrationDisposable(final Future<V> future) {
        this.future = requireNonNull(future);
    }

    @Override
    public boolean isSuccess() {
        return future.isSuccess();
    }

    @Override
    public boolean isCancellable() {
        return future.isCancellable();
    }

    @Override
    public Throwable cause() {
        return future.cause();
    }

    @Override
    public Future<V> addListener(final GenericFutureListener<? extends Future<? super V>> listener) {
        return future.addListener(listener);
    }

    @Override
    public Future<V> addListeners(final GenericFutureListener<? extends Future<? super V>>... listeners) {
        return future.addListeners(listeners);
    }

    @Override
    public Future<V> removeListener(final GenericFutureListener<? extends Future<? super V>> listener) {
        return future.removeListener(listener);
    }

    @Override
    public Future<V> removeListeners(final GenericFutureListener<? extends Future<? super V>>... listeners) {
        return future.removeListeners(listeners);
    }

    @Override
    public Future<V> sync() throws InterruptedException {
        return future.sync();
    }

    @Override
    public Future<V> syncUninterruptibly() {
        return future.syncUninterruptibly();
    }

    @Override
    public Future<V> await() throws InterruptedException {
        return future.await();
    }

    @Override
    public Future<V> awaitUninterruptibly() {
        return future.awaitUninterruptibly();
    }

    @Override
    public boolean await(final long timeout, final TimeUnit unit) throws InterruptedException {
        return future.await(timeout, unit);
    }

    @Override
    public boolean await(final long timeoutMillis) throws InterruptedException {
        return future.await(timeoutMillis);
    }

    @Override
    public boolean awaitUninterruptibly(final long timeout, final TimeUnit unit) {
        return future.awaitUninterruptibly(timeout, unit);
    }

    @Override
    public boolean awaitUninterruptibly(final long timeoutMillis) {
        return future.awaitUninterruptibly(timeoutMillis);
    }

    @Override
    public V getNow() {
        return future.getNow();
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return future.isCancelled();
    }

    @Override
    public boolean isDone() {
        return future.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    @Override
    public V get(final long timeout,
                 @NonNull final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }
}
