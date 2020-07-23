package org.drasyl.util;

import io.netty.util.concurrent.Future;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Utility class for future-related operations.
 */
public class FutureUtil {
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
    public static <T> CompletableFuture<T> toFuture(Future<T> future) {
        requireNonNull(future);

        if (future.isDone() || future.isCancelled()) {
            if (future.isSuccess()) {
                return completedFuture(future.getNow());
            }
            else {
                return failedFuture(future.cause());
            }
        }

        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        future.addListener(f -> {
            if (f.isSuccess()) {
                completableFuture.complete(null);
            }
            else {
                completableFuture.completeExceptionally(f.cause());
            }
        });

        return completableFuture;
    }

    /**
     * Completes {@code future} exceptionally if one of the given {@code futures} are completed
     * exceptionally. If the given array of {@code futures} is empty, nothing happens.
     *
     * @param future  future that should be completed
     * @param futures futures that should be waited for
     * @throws NullPointerException if {@code future} or {@code futures} is null
     */
    public static void completeOnAnyOfExceptionally(CompletableFuture<?> future,
                                                    Collection<CompletableFuture<?>> futures) {
        completeOnAnyOfExceptionally(future, futures.toArray(CompletableFuture[]::new));
    }

    /**
     * Completes {@code future} exceptionally if one of the given {@code futures} are completed
     * exceptionally. If the given array of {@code futures} is empty, nothing happens.
     *
     * @param future  future that should be completed
     * @param futures futures that should be waited for
     * @throws NullPointerException if {@code future} or {@code futures} is null
     */
    public static void completeOnAnyOfExceptionally(CompletableFuture<?> future,
                                                    CompletableFuture<?>... futures) {
        requireNonNull(future);
        requireNonNull(futures);

        if (futures.length == 0) {
            return;
        }

        CompletableFuture.allOf(futures).exceptionally(e -> {
            future.completeExceptionally(e);

            return null;
        });
    }

    /**
     * Completes {@code future} if all of the given {@code futures} are completed. When one of the
     * given {@code futures} completes exceptionally, the given {@code future} will also complete
     * exceptionally. If the given collection of {@code futures} is empty, the {@code future} is
     * completed immediately.
     *
     * @param future  future that should be completed
     * @param futures futures that should be waited for
     * @throws NullPointerException if {@code future} or {@code futures} is null
     */
    public static void completeOnAllOf(CompletableFuture<Void> future,
                                       Collection<CompletableFuture<?>> futures) {
        completeOnAllOf(future, futures.toArray(CompletableFuture[]::new));
    }

    /**
     * Completes {@code future} if all of the given {@code futures} are completed. When one of the
     * given {@code futures} completes exceptionally, the given {@code future} will also complete
     * exceptionally. If the given array of {@code futures} is empty, the {@code future} is
     * completed immediately.
     *
     * @param future  future that should be completed
     * @param futures futures that should be waited for
     * @throws NullPointerException if {@code future} or {@code futures} is null
     */
    public static void completeOnAllOf(CompletableFuture<Void> future,
                                       CompletableFuture<?>... futures) {
        requireNonNull(future);
        requireNonNull(futures);

        if (futures.length == 0) {
            future.complete(null);
        }
        else {
            CompletableFuture.allOf(futures).whenComplete((t, e) -> {
                if (e != null) {
                    future.completeExceptionally(e);
                }
                else {
                    future.complete(null);
                }
            });
        }
    }
}
