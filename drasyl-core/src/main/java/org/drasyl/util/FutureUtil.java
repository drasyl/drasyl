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

    @SuppressWarnings("unchecked")
    public static <T, R> Future<R> mapFuture(final io.netty.util.concurrent.Future<T> future, final
    EventExecutor executor, final Function<T, R> mapper) {
        if (future.cause() != null) {
            // Cast is safe because the result type is not used in failed futures.
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

    public static <T,R> Future<R> chainFuture(final Future<T> predecessor,
                                               final EventExecutor executor,
                                               final Function<T, Future<R>> chain) {
        final Promise<R> objectPromise = executor.newPromise();
        // FIXME: cause geht hier doch verloren!
        predecessor.addListener((FutureListener<T>) future -> chain.apply(future.getNow()).addListener(new PromiseNotifier<>(objectPromise)));
        return objectPromise;
    }

    private static final class CallableMapper<T, R> implements Callable<R> {
        private final io.netty.util.concurrent.Future<T> future;
        private final Function<T, R> mapper;

        CallableMapper(final io.netty.util.concurrent.Future<T> future,
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
            if (future.cause() == null) {
                final T result = future.getNow();
                try {
                    final R mappedResult = mapper.apply(result);
                    mappedPromise.trySuccess(mappedResult);
                }
                catch (final Exception e) {
                    mappedPromise.tryFailure(e);
                }
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
}
