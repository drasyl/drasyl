package org.drasyl.util;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.FutureUtil.chain2Future;
import static org.drasyl.util.FutureUtil.chainFuture;
import static org.drasyl.util.FutureUtil.mapFuture;

public final class FutureComposer<T> {
    final EventExecutor executor;
    private final Future<T> future;

    FutureComposer(final EventExecutor executor,
                   final Future<T> future) {
        this.executor = requireNonNull(executor);
        this.future = requireNonNull(future);
    }

    public <R> FutureComposer<R> map(final Function<T, R> mapper) {
        return new FutureComposer<>(executor, mapFuture(future, executor, mapper));
    }

    public <R> FutureComposer<R> chain(final Function<T, FutureComposer<R>> chaining) {
        return new FutureComposer<>(executor, chainFuture(future, executor, t -> chaining.apply(t).toFuture()));
    }

    public <R> FutureComposer<R> chain2(final Function<Future<T>, FutureComposer<R>> chaining) {
        return new FutureComposer<>(executor, chain2Future(future, executor, t -> chaining.apply(t).toFuture()));
    }

    public <R> UnexecutableFutureComposer<R> chain2Unexecutable(final Function<Future<T>, FutureComposer<R>> chaining) {
        return new UnexecutableFutureComposer<>(executor -> new FutureComposer<>(executor, chain2Future(future, executor, t -> chaining.apply(t).toFuture())).toFuture());
    }

    public <R> FutureComposer<R> then(final FutureComposer<R> then) {
        return new FutureComposer<>(executor, chainFuture(future, executor, t -> then.toFuture()));
    }

    public Future<T> toFuture() {
        return future;
    }

    public static <T> FutureComposer<T> composeFuture(final EventExecutor executor,
                                                      final Future<T> future) {
        return new FutureComposer<>(executor, future);
    }

    public static <R> FutureComposer<R> composeFuture(final EventExecutor executor,
                                                      final R result) {
        return new FutureComposer<>(executor, executor.newSucceededFuture(result));
    }

    public static FutureComposer<Void> composeFuture(final EventExecutor executor) {
        return composeFuture(executor, (Void) null);
    }
}
