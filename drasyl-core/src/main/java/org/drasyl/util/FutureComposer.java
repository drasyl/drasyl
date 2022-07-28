package org.drasyl.util;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.FutureUtil.chain2Future;
import static org.drasyl.util.FutureUtil.chainFuture;
import static org.drasyl.util.FutureUtil.mapFuture;

public final class FutureComposer<T> {
    private final Function<EventExecutor, Future<T>> futureResolver;

    FutureComposer(final Function<EventExecutor, Future<T>> futureResolver) {
        this.futureResolver = requireNonNull(futureResolver);
    }

    public Future<T> finish(final EventExecutor executor) {
        return futureResolver.apply(executor);
    }

    /**
     * transformiert das ergebnis des alten futures.
     */
    public <R> FutureComposer<R> map(final Function<T, R> mapper) {
        return new FutureComposer<>(executor -> mapFuture(futureResolver.apply(executor), executor, mapper));
    }

    /**
     * waits for future to be done before we continue.
     */
    public <R> FutureComposer<R> then(final Future<R> future) {
        return new FutureComposer<>(executor -> {
            futureResolver.apply(executor);
            return future;
        });
    }

    public <R> FutureComposer<R> chain(final Function<T, FutureComposer<R>> mapper) {
        return new FutureComposer<>(executor -> chainFuture(futureResolver.apply(executor), executor, future -> mapper.apply(future).futureResolver.apply(executor)));
    }

    public <R> FutureComposer<R> chain2(final Function<Future<T>, FutureComposer<R>> mapper) {
        return new FutureComposer<>(executor -> chain2Future(futureResolver.apply(executor), executor, future -> mapper.apply(future).futureResolver.apply(executor)));
    }

    public static <R> FutureComposer<R> composeFuture(final R result) {
        return new FutureComposer<>(executor -> executor.newSucceededFuture(result));
    }

    public static <R> FutureComposer<R> composeFuture() {
        return composeFuture(null);
    }
}
