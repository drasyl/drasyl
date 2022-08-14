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

/**
 * This utility class lat you chain up {@link Future}s.
 * <p>
 * A common need is to execute two or more asynchronous operations back to back, where each
 * subsequent operation starts when the previous operation succeeds, with the result from the
 * previous step.
 *
 * @param <T> result type of the current step's {@link Future}
 */
public final class FutureComposer<T> {
    private final Function<EventExecutor, Future<T>> futureResolver;

    FutureComposer(final Function<EventExecutor, Future<T>> futureResolver) {
        this.futureResolver = requireNonNull(futureResolver);
    }

    /**
     * Binds all {@link Future}s in this {@link FutureComposer} to {@code executor}. Returns {@link
     * Future} that completes if all previous {@link Future}s in this {@link FutureComposer} have
     * been completed.
     *
     * @param executor {@link EventExecutor} to bind all {@link Future}s in this {@link
     *                 FutureComposer} to
     * @return {@link Future} that completes if all previous {@link Future}s in this {@link
     * FutureComposer} have been completed
     */
    public Future<T> finish(final EventExecutor executor) {
        final Promise<T> composedPromise = executor.newPromise();
        executor.execute(() -> PromiseNotifier.cascade(futureResolver.apply(executor), composedPromise));
        return composedPromise;
    }

    /**
     * Returns a new {@link FutureComposer} that will complete if all previous {@link Future}s and
     * {@code future} have been completed.
     */
    public <R> FutureComposer<R> then(final Future<R> future) {
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
     * Returns a new {@link FutureComposer} that will complete if all previous {@link Future}s and
     * the {@link FutureComposer} returned by {@code mapper} have been completed. {@link Future}
     * will be passed to {@code mapper}.
     */
    public <R> FutureComposer<R> then(final Function<Future<T>, FutureComposer<R>> mapper) {
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

    public <R> FutureComposer<R> then(final Supplier<FutureComposer<R>> mapper) {
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

    /**
     * Creates a new {@link FutureComposer} whose first {@link Future} is {@code future}.
     *
     * @param future the initial {@link Future}
     * @param <R>    type of {@code future}
     * @return new {@link FutureComposer} whose first {@link Future} is {@code future}
     */
    public static <R> FutureComposer<R> composeFuture(final Future<R> future) {
        return composeSucceededFuture().then(future);
    }

    /**
     * Creates a new {@link FutureComposer} whose first {@link Future} was successfully completed
     * with {@code result}.
     *
     * @param result result of the initial {@link Future}
     * @param <R>    type of {@code result}
     * @return new {@link FutureComposer} whose first {@link Future} was successfully completed with
     * {@code result}
     */
    public static <R> FutureComposer<R> composeSucceededFuture(final R result) {
        return new FutureComposer<>(executor -> executor.newSucceededFuture(result));
    }

    /**
     * Creates a new {@link FutureComposer} whose first {@link Future} was successfully completed
     * with {@code null}.
     *
     * @param result result of the initial {@link Future}
     * @return new {@link FutureComposer} whose first {@link Future} was successfully completed with
     * {@code null}
     */
    public static FutureComposer<Void> composeSucceededFuture() {
        return composeSucceededFuture(null);
    }

    /**
     * Creates a new {@link FutureComposer} whose first {@link Future} was failed with {@code
     * cause}.
     *
     * @param cause cause that failed the {@link Future}
     * @param <R>   type of {@link Future}
     * @return new {@link FutureComposer} whose first {@link Future} was failed with {@code cause}
     */
    public static <R> FutureComposer<R> composeFailedFuture(final Throwable cause) {
        return new FutureComposer<>(executor -> executor.newFailedFuture(cause));
    }
}
