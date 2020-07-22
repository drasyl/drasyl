package org.drasyl.util;

import io.netty.util.concurrent.Future;

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
}
