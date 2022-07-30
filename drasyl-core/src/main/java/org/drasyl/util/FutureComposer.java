package org.drasyl.util;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;

import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.FutureUtil.chainFuture;

public final class FutureComposer<T> {
    private final Function<EventExecutor, Future<T>> futureResolver;

    FutureComposer(final Function<EventExecutor, Future<T>> futureResolver) {
        this.futureResolver = requireNonNull(futureResolver);
    }

    public Future<T> finish(final EventExecutor executor) {
        return futureResolver.apply(executor);
    }

    /**
     * waits for future to be done before we continue.
     */
    public <R> FutureComposer<R> chain(final Future<R> future) {
        return new FutureComposer<>(executor -> {
            futureResolver.apply(executor);
            return future;
        });
    }

    public <R> FutureComposer<R> chain(final Supplier<FutureComposer<R>> mapper) {
        return new FutureComposer<>(executor -> chainFuture(futureResolver.apply(executor), executor, future -> mapper.get().futureResolver.apply(executor)));
    }

    public <R> FutureComposer<R> chain(final Function<Future<T>, FutureComposer<R>> mapper) {
        return new FutureComposer<>(executor -> chainFuture(futureResolver.apply(executor), executor, future -> mapper.apply(future).futureResolver.apply(executor)));
    }

    public static <R> FutureComposer<R> composeFuture(final R result) {
        return new FutureComposer<>(executor -> executor.newSucceededFuture(result));
    }

    public static <R> FutureComposer<R> composeFuture() {
        return composeFuture(null);
    }

    public static <R> FutureComposer<R> composeFailedFuture(final Throwable cause) {
        return new FutureComposer<>(executor -> executor.newFailedFuture(cause));
    }
}
