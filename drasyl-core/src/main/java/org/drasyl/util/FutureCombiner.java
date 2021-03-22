/*
 * Copyright (c) 2020-2021.
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

/**
 * A future combiner monitors the outcome of a number of discrete futures, then notifies a final,
 * aggregate future when all of the combined futures are finished. The aggregate future will succeed
 * if and only if all of the combined futures succeed. If any of the combined futures fail, the
 * aggregate future will fail. The cause failure for the aggregate future will be the failure for
 * one of the failed combined futures; if more than one of the combined futures fails, exactly which
 * cause of failure will be assigned to the aggregate future is undefined.
 * <p>
 * Callers may populate a future combiner with any number of futures to be combined via the {@link
 * #add(CompletableFuture)} and {@link #addAll(CompletableFuture[])} methods. When all futures to be
 * combined have been added, callers must provide an aggregate future to be notified when all
 * combined futures have finished via the {@link #combine(CompletableFuture)} method.
 */
public class FutureCombiner {
    private final List<CompletableFuture<?>> futureList;

    private FutureCombiner() {
        futureList = new ArrayList<>();
    }

    /**
     * @return a new instance of the future combiner
     */
    public static FutureCombiner getInstance() {
        return new FutureCombiner();
    }

    /**
     * Adds a new {@code future} to be combined. New futures may be added until an aggregate future
     * is added via the {@link #combine(CompletableFuture)} method.
     *
     * @param future the future to add to this future combiner
     * @return this {@link FutureCombiner}
     */
    public synchronized FutureCombiner add(final CompletableFuture<?> future) {
        requireNonNull(future);

        futureList.add(future);

        return this;
    }

    /**
     * Adds a new {@code futures} to be combined. New futures may be added until an aggregate future
     * is added via the {@link #combine(CompletableFuture)} method.
     *
     * @param futures the futures to add to this future combiner
     * @return this {@link FutureCombiner}
     */
    public synchronized FutureCombiner addAll(final CompletableFuture<?>... futures) {
        if (futures.length == 0) {
            return this;
        }

        for (final CompletableFuture<?> future : futures) {
            add(future);
        }

        return this;
    }

    /**
     * Completes {@code future} if all of the added futures are completed. When any of the given
     * futures completes exceptionally, the given {@code future} will also completes exceptionally
     * immediately. If the combiner is empty, the {@code future} is completed immediately.
     *
     * @param future future that should be completed
     * @return the given {@code future}
     * @throws NullPointerException if {@code future}
     */
    public synchronized CompletableFuture<Void> combine(final CompletableFuture<Void> future) {
        requireNonNull(future);

        if (futureList.isEmpty() || future.isDone()) {
            return future;
        }

        final CompletableFuture<?>[] futuresArray = futureList.toArray(new CompletableFuture[0]);

        CompletableFuture.allOf(futuresArray).whenComplete((a, e) -> {
            if (e == null) {
                future.complete(null);
            }
        });

        for (final CompletableFuture<?> f : futureList) {
            f.exceptionally(e -> {
                future.completeExceptionally(e);

                return null;
            });
        }

        return future;
    }
}
