package org.drasyl.util;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseCombiner;
import io.netty.util.concurrent.PromiseNotifier;

import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public final class FutureComposer<T> {
    private final Function<EventExecutor, Future<T>> futureResolver;

    FutureComposer(final Function<EventExecutor, Future<T>> futureResolver) {
        this.futureResolver = requireNonNull(futureResolver);
    }

    public Future<T> finish(final EventExecutor executor) {
        final Promise<T> composedPromise = executor.newPromise();
        executor.execute(() -> PromiseNotifier.cascade(futureResolver.apply(executor), composedPromise));
        return composedPromise;
    }

    /**
     * Returns a new {@link FutureComposer} that will complete if all previous composed futures and
     * {@code future} have been completed.
     */
    public <R> FutureComposer<R> chain(final Future<R> future) {
        return new FutureComposer<>(executor -> {
            // create combined future that complete if existing and new future complete
            final PromiseCombiner combiner = new PromiseCombiner(executor);
            combiner.add(futureResolver.apply(executor));
            combiner.add(future);
            final Promise<Void> combinedFuture = executor.newPromise();
            combiner.finish(combinedFuture);

            // create future that complete if combined future complete but with the value of the new future
            final Promise<R> returnFuture = executor.newPromise();
            combinedFuture.addListener((FutureListener<Void>) f -> {
                if (f.isSuccess()) {
                    returnFuture.setSuccess(future.getNow());
                }
                else if (f.isCancelled()) {
                    returnFuture.cancel(false);
                }
                else {
                    returnFuture.setFailure(f.cause());
                }
            });

            return returnFuture;
        });
    }

    /**
     * Returns a new {@link FutureComposer} that will complete if all previous composed futures and
     * the {@link FutureComposer} returned by {@code mapper} have been completed.
     */
    public <R> FutureComposer<R> chain(final Function<Future<T>, FutureComposer<R>> mapper) {
        return new FutureComposer<>(executor -> {
            final Future<T> existingFuture = futureResolver.apply(executor);

            // create return future that is liked to the future returned by the other future composer
            final Promise<R> returnFuture = executor.newPromise();
            existingFuture.addListener((FutureListener<T>) future -> {
                final Future<R> newFuture = mapper.apply(existingFuture).finish(executor);
                PromiseNotifier.cascade(newFuture, returnFuture);
            });

            return returnFuture;
        });
    }

    /**
     * Returns a new {@link FutureComposer} that will complete if all previous composed futures and
     * the {@link FutureComposer} returned by {@code mapper} have been completed.
     */
    public <R> FutureComposer<R> chain(final Supplier<FutureComposer<R>> mapper) {
        return new FutureComposer<>(executor -> {
            final Future<T> existingFuture = futureResolver.apply(executor);

            // create return future that is liked to the future returned by the other future composer
            final Promise<R> returnFuture = executor.newPromise();
            existingFuture.addListener((FutureListener<T>) future -> {
                final Future<R> newFuture = mapper.get().finish(executor);
                PromiseNotifier.cascade(newFuture, returnFuture);
            });

            return returnFuture;
        });
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
