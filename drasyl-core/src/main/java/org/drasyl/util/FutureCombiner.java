/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
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
