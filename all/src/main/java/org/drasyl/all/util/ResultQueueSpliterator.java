/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.util;

import java.util.Objects;
import java.util.Queue;
import java.util.Spliterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Holds a {@link ConcurrentLinkedQueue} of intermediate results which will be
 * offered upon calling {@link ResultQueueSpliterator#tryAdvance tryAdvance}.
 */
public final class ResultQueueSpliterator<T> implements Spliterator<T> {
    private final Queue<T> results = new ConcurrentLinkedQueue<>();
    private final Timer timer;
    private volatile boolean completed;
    private volatile boolean onCompletion;

    /**
     * Creates a new {@link Spliterator} that queues intermediate results and
     * returns them upon calling {@link ResultQueueSpliterator#tryAdvance
     * tryAdvance}. Since no timer is scheduled,
     * {@link ResultQueueSpliterator#finish finish} must be called in order to
     * indicate that the intermediate result which was added last will be the final
     * element of the Spliterator.
     */
    public ResultQueueSpliterator() {
        this.timer = null;
    }

    /**
     * Creates a new {@link Spliterator} that queues intermediate results and
     * returns them upon calling {@link ResultQueueSpliterator#tryAdvance
     * tryAdvance}. Schedules a timer after which
     * {@link ResultQueueSpliterator#finish finish} will be called and no more
     * results can be added.
     * 
     * @param timeout how long to wait before calling
     *                {@link ResultQueueSpliterator#finish finish}, in milliseconds
     * @throws IllegalArgumentException if timeout is negative
     */
    public ResultQueueSpliterator(long timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("Timeout cannot be negative.");
        }
        this.timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                finish();
            }
        }, timeout);
    }

    @Override
    public boolean tryAdvance(Consumer<? super T> action) {
        T next = results.poll();
        while (next == null && !completed) {
            Thread.onSpinWait();
            next = results.poll();
        }
        if (next == null) {
            onCompletion = true;
            return false;
        }
        Objects.requireNonNull(action);
        action.accept(next);
        return true;
    }

    /**
     * Adds a new intermediate result to the queue.
     * 
     * @param result the result to add
     * @return true if the element was added to the intermediate results. That is,
     *         if {@link ResultQueueSpliterator#finish finish} hasn't been called,
     *         neither by the timer nor manually.
     * @throws NullPointerException if result is null
     */
    public boolean addIntermediateResult(T result) {
        Objects.requireNonNull(result);
        if (onCompletion) {
            return false;
        }
        return results.offer(result);
    }

    /**
     * If this method is called, no more elements can be added and the intermediate
     * result which was added last will be the final element of the Spliterator.
     * Calling this method more than once has no effect.
     */
    public void finish() {
        if (!onCompletion) {
            onCompletion = true;
            if (!results.isEmpty()) {
                new Thread(() -> {
                    while (!results.isEmpty() && !completed) {
                        Thread.onSpinWait();
                    }
                    doFinish();
                }).start();
            } else {
                doFinish();
            }
        }
    }

    /**
     * Returns true if this Spliterator can take more intermediate results. That is,
     * if {@link ResultQueueSpliterator#finish finish} hasn't been called, neither
     * by the timer nor manually.
     * 
     * @return true if this Spliterator can take more intermediate results
     */
    public boolean isFinished() {
        return onCompletion;
    }

    private void doFinish() {
        onCompletion = true;
        completed = true;
        cancelTimer();
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
        }
    }

    @Override
    public Spliterator<T> trySplit() {
        return null;
    }

    @Override
    public long estimateSize() {
        return Long.MAX_VALUE;
    }

    @Override
    public int characteristics() {
        return 0;
    }
}
