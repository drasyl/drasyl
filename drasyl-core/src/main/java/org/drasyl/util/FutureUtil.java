/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

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
     * Completes {@code future} exceptionally if one of the given {@code futures} are completed
     * exceptionally. If the given array of {@code futures} is empty, nothing happens.
     *
     * @param future  future that should be completed
     * @param futures futures that should be waited for
     * @throws NullPointerException if {@code future} or {@code futures} is null
     */
    public static void completeOnAnyOfExceptionally(final CompletableFuture<?> future,
                                                    final Collection<CompletableFuture<?>> futures) {
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
    public static void completeOnAnyOfExceptionally(final CompletableFuture<?> future,
                                                    final CompletableFuture<?>... futures) {
        requireNonNull(future);
        requireNonNull(futures);

        if (futures.length == 0) {
            return;
        }

        CompletableFuture.anyOf(futures).exceptionally(e -> {
            future.completeExceptionally(e);

            return null;
        });
    }

    /**
     * Returns a completed future if all of the given {@code futures} are completed. When any of the
     * given {@code futures} completes exceptionally, the future will also completes exceptionally
     * immediately. If the given list of {@code futures} is empty, the future is completed
     * immediately.
     *
     * @param futures futures that should be waited for
     * @throws NullPointerException if {@code futures} is null
     */
    public static CompletableFuture<Void> getCompleteOnAllOf(final Collection<CompletableFuture<?>> futures) {
        return getCompleteOnAllOf(futures.toArray(CompletableFuture[]::new));
    }

    /**
     * Returns a completed future if all of the given {@code futures} are completed. When any of the
     * given {@code futures} completes exceptionally, the future will also completes exceptionally
     * immediately. If the given array of {@code futures} is empty, the future is completed
     * immediately.
     *
     * @param futures futures that should be waited for
     * @throws NullPointerException if {@code futures} is null
     */
    public static CompletableFuture<Void> getCompleteOnAllOf(final CompletableFuture<?>... futures) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        completeOnAllOf(future, futures);

        return future;
    }

    /**
     * Completes {@code future} if all of the given {@code futures} are completed. When any of the
     * given {@code futures} completes exceptionally, the given {@code future} will also completes
     * exceptionally immediately. If the given list of {@code futures} is empty, the {@code future}
     * is completed immediately.
     *
     * @param future  future that should be completed
     * @param futures futures that should be waited for
     * @throws NullPointerException if {@code future} or {@code futures} is null
     */
    public static void completeOnAllOf(final CompletableFuture<Void> future,
                                       final Collection<CompletableFuture<?>> futures) {
        completeOnAllOf(future, futures.toArray(CompletableFuture[]::new));
    }

    /**
     * Completes {@code future} if all of the given {@code futures} are completed. When any of the
     * given {@code futures} completes exceptionally, the given {@code future} will also completes
     * exceptionally immediately. If the given array of {@code futures} is empty, the {@code future}
     * is completed immediately.
     *
     * @param future  future that should be completed
     * @param futures futures that should be waited for
     * @throws NullPointerException if {@code future} or {@code futures} is null
     */
    public static void completeOnAllOf(final CompletableFuture<Void> future,
                                       final CompletableFuture<?>... futures) {
        requireNonNull(future);
        requireNonNull(futures);

        if (futures.length == 0) {
            future.complete(null);
        }
        else {
            completeOnAnyOfExceptionally(future, futures);

            CompletableFuture.allOf(futures).whenComplete((t, e) -> {
                if (e == null) {
                    future.complete(null);
                }
            });
        }
    }
}