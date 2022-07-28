package org.drasyl.util;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;

import static java.util.Objects.requireNonNull;

public final class FutureComposer<T> {
    final EventExecutor executor;
    final Future<T> future;

    FutureComposer(final EventExecutor executor,
                   final Future<T> future) {
        this.executor = requireNonNull(executor);
        this.future = requireNonNull(future);
    }

    public Future<T> toFuture() {
        return future;
    }

    public FutureComposer<T> compose(final EventExecutor executor) {
        // IGNORE
        return this;
    }

    public static <T> FutureComposer<T> composeFuture(final EventExecutor executor,
                                                      final Future<T> future) {
        return new FutureComposer<>(executor, future);
    }
}
