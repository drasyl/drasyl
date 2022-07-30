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
package org.drasyl.util;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Utility class for future-related operations.
 */
public final class FutureUtil {
    private FutureUtil() {
        // util class
    }

    /**
     * Translates the Netty {@link Future} to a {@link CompletableFuture}.
     *
     * @param future The future to be translated
     * @param <T>    The result type of the future
     * @return The translated {@link CompletableFuture}
     */
    public static <T> CompletableFuture<T> toFuture(final Future<T> future) {
        requireNonNull(future);

        if (future.isDone() || future.isCancelled()) {
            if (future.isSuccess()) {
                return completedFuture(future.getNow());
            }
            else {
                return failedFuture(future.cause());
            }
        }

        final CompletableFuture<T> completableFuture = new CompletableFuture<>();
        future.addListener(f -> {
            if (f.isSuccess()) {
                @SuppressWarnings("unchecked") final T now = (T) f.getNow();
                completableFuture.complete(now);
            }
            else {
                completableFuture.completeExceptionally(f.cause());
            }
        });

        return completableFuture;
    }

    /**
     * Synchronizes {@code promise} and {@code future} in both directions.
     *
     * @param promise
     * @param future
     * @param <T>
     */
    public static <T> void synchronizeFutures(final Promise<T> promise,
                                              final CompletableFuture<T> future) {
        promise.addListener(f -> {
            if (f.isSuccess()) {
                future.complete((T) f.getNow());
            }
            else {
                future.completeExceptionally(f.cause());
            }
        });
        future.whenComplete((result, e) -> {
            if (e == null) {
                promise.setSuccess(result);
            }
            else {
                promise.tryFailure(e);
            }
        });
    }

    /**
     * Creates a <strong>new</strong> {@link Future} that will complete with the result of
     * {@code future} mapped through the given mapper function {@code mapper}.
     * <p>
     * If {@code future} fails, then the returned future will fail as well, with the same exception.
     * Cancellation of either future will cancel the other. If the mapper function throws, the
     * returned future will fail, but {@code future} will be unaffected.
     *
     * @param future   The future whose result should be passed to a mapper function.
     * @param executor The executor in which the returned future (and thus the mapper function)
     *                 should run.
     * @param mapper   The function that will convert the result of this future into the result of
     *                 the returned future.
     * @param <T>      The result type of the function to be mapped.
     * @param <R>      The result type of the mapper function, and of the returned future.
     * @return A new future instance that will complete with the mapped result of this future.
     */
    @SuppressWarnings("unchecked")
    public static <T, R> Future<R> mapFuture(final Future<T> future,
                                             final EventExecutor executor,
                                             final Function<T, R> mapper) {
        if (future.cause() != null) {
            // cast is safe because the result type is not used in failed futures.
            return (Future<R>) future;
        }
        else if (future.isSuccess()) {
            return executor.submit(new CallableMapper<>(future, mapper));
        }
        final Promise<R> promise = executor.newPromise();
        future.addListener(new MapperFutureListener<>(promise, mapper));
        promise.addListener(new PropagateCancelListener(future));
        return promise;
    }

    @SuppressWarnings("unchecked")
    public static <T, R> Future<R> chainFuture(final Future<T> predecessor,
                                               final EventExecutor executor,
                                               final Function<Future<T>, Future<R>> mapper) {
        if (predecessor.cause() != null) {
            // cast is safe because the result type is not used in failed futures.
            return (Future<R>) predecessor;
        }
        else if (predecessor.isSuccess()) {
            // FIXME: wird im falschen thread ausgeführt
            return mapper.apply(predecessor);
        }
        final Promise<R> promise = executor.newPromise();
        predecessor.addListener((FutureListener<T>) future -> PromiseNotifier.cascade(mapper.apply(future), promise));
        return promise;
    }

    private static final class CallableMapper<T, R> implements Callable<R> {
        private final Future<T> future;
        private final Function<T, R> mapper;

        CallableMapper(final Future<T> future,
                       final Function<T, R> mapper) {
            this.future = requireNonNull(future);
            this.mapper = requireNonNull(mapper);
        }

        @Override
        public R call() {
            return mapper.apply(future.getNow());
        }
    }

    private static class MapperFutureListener<T, R> implements FutureListener<T> {
        private final Function<T, R> mapper;
        private final Promise<R> mappedPromise;

        public MapperFutureListener(final Promise<R> mappedPromise, final Function<T, R> mapper) {
            this.mapper = requireNonNull(mapper);
            this.mappedPromise = requireNonNull(mappedPromise);
        }

        @Override
        public void operationComplete(final Future<T> future) {
            if (future.isSuccess()) {
                final T result = future.getNow();
                try {
                    final R mappedResult = mapper.apply(result);
                    mappedPromise.trySuccess(mappedResult);
                }
                catch (final Exception e) {
                    mappedPromise.tryFailure(e);
                }
            }
            else if (future.isCancelled()) {
                mappedPromise.cancel(false);
            }
            else {
                mappedPromise.tryFailure(future.cause());
            }
        }
    }

    private static class PropagateCancelListener implements FutureListener<Object> {
        private final Future<?> future;

        public PropagateCancelListener(final Future<?> future) {
            this.future = requireNonNull(future);
        }

        @Override
        public void operationComplete(final Future<Object> future) throws Exception {
            if (future.isCancelled()) {
                this.future.cancel(false);
            }
        }
    }

    private static class ChainingFutureListener<T, R> implements FutureListener<T> {
        private final Function<T, Future<R>> mapper;
        private final Promise<R> promise;

        public ChainingFutureListener(final Function<T, Future<R>> mapper,
                                      final Promise<R> promise) {
            this.mapper = mapper;
            this.promise = promise;
        }

        @Override
        public void operationComplete(final Future<T> future) throws Exception {
            if (future.isSuccess()) {
                PromiseNotifier.cascade(mapper.apply(future.getNow()), promise);
            }
            else {
                // cast is safe because the result type is not used in failed futures.
                ((Future<R>) future).addListener(new PromiseNotifier<>(promise));
            }
        }
    }
}
