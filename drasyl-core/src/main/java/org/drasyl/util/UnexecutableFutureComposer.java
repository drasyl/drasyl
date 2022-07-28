package org.drasyl.util;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.FutureUtil.chain2Future;
import static org.drasyl.util.FutureUtil.chainFuture;
import static org.drasyl.util.FutureUtil.mapFuture;

public final class UnexecutableFutureComposer<T> {
    private final Function<EventExecutor, Future<T>> future;

    UnexecutableFutureComposer(final Function<EventExecutor, Future<T>> future) {
        this.future = requireNonNull(future);
    }

    public Future<T> toFuture(final EventExecutor executor) {
        return future.apply(executor);
    }

    public FutureComposer<T> compose(final EventExecutor executor) {
        return new FutureComposer<>(executor, future.apply(executor));
    }

    public <R> UnexecutableFutureComposer<R> map(final Function<T, R> mapper) {
        return new UnexecutableFutureComposer<>(executor -> mapFuture(future.apply(executor), executor, mapper));
    }

    public <R> UnexecutableFutureComposer<R> then(final Function<T, R> mapper) {
        return new UnexecutableFutureComposer<>(executor -> mapFuture(future.apply(executor), executor, mapper));
    }

    public <R> UnexecutableFutureComposer<R> then(final UnexecutableFutureComposer<R> mapper) {
        return new UnexecutableFutureComposer<>(executor -> chainFuture(future.apply(executor), executor, t -> mapper.toFuture(executor)));
    }

    public <R> UnexecutableFutureComposer<R> thenUnexecutable(final EventExecutor e2,
                                                              final Future<R> f2) {
        return new UnexecutableFutureComposer<>(executor -> new FutureComposer<>(e2, chainFuture(future.apply(e2), e2, t -> f2)).toFuture());
    }

    public <R> UnexecutableFutureComposer<R> thenUnexecutable(final FutureComposer<R> then) {
        return thenUnexecutable(then.executor, then.future);
    }

    public <R> UnexecutableFutureComposer<R> chain(final Function<T, UnexecutableFutureComposer<R>> chaining) {
        return new UnexecutableFutureComposer<>(executor -> chainFuture(future.apply(executor), executor, future -> chaining.apply(future).future.apply(executor)));
    }

    public <R> UnexecutableFutureComposer<R> chain2(final Function<Future<T>, UnexecutableFutureComposer<R>> chaining) {
        return new UnexecutableFutureComposer<>(executor -> chain2Future(future.apply(executor), executor, future -> chaining.apply(future).future.apply(executor)));
    }

    public static <R> UnexecutableFutureComposer<R> composeUnexecutableFuture(final R result) {
        return new UnexecutableFutureComposer<>(executor -> executor.newSucceededFuture(result));
    }

    public static UnexecutableFutureComposer<Void> composeUnexecutableFuture() {
        return composeUnexecutableFuture((Void) null);
    }
}
