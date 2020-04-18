/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.core.server.session.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

/**
 * Thread safe class that stores the added elements at least for the given
 * amount of time.
 */
public class AutoDeletionBucket<T> {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(AutoDeletionBucket.class);
    private final ReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final HashMap<T, Long> bucket;
    private final long keepTime;
    private final Timer timer;

    /**
     * Creates a new AutoDeletionBucket.
     *
     * @param keepTime the minimum time, in milliseconds, that the object should be
     *                 kept for
     * @param interval the rate, in milliseconds at which the bucket should be
     *                 emptied. Set this value a high as possible for minimum
     *                 resource consumption.
     * @throws IllegalArgumentException if {@code keepTime} < 0 or if
     *                                  {@code interval} <= 0
     */
    public AutoDeletionBucket(long keepTime, long interval) {
        this.keepTime = Objects.requireNonNull(keepTime);
        Objects.requireNonNull(interval);
        if (keepTime < 0 || interval <= 0) {
            throw new IllegalArgumentException("Arguments cannot be negative.");
        }
        bucket = new HashMap<>();
        this.timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                cleanUp();
            }
        }, interval, interval);
    }

    /**
     * Adds a new element to the bucket or resets the timer if the element is
     * already in the bucket.
     *
     * @param e the element to add
     * @return true if the element wasn't already in the bucket
     */
    public boolean add(T e) {
        Objects.requireNonNull(e);
        try {
            lock.writeLock().lock();
            return bucket.put(e, System.currentTimeMillis() + keepTime) == null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Adds a collection of new elements to the bucket or resets the timer if the
     * element was already in the bucket.
     *
     * @param elements the element to add
     */
    public void addAll(Collection<T> elements) {
        Objects.requireNonNull(elements);
        try {
            lock.writeLock().lock();
            long delTime = System.currentTimeMillis() + keepTime;
            elements.forEach(e -> {
                if (e != null) {
                    bucket.put(e, delTime);
                }
            });
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns an immutable set of all bucket elements.
     *
     * @return the set of all bucket elements
     */
    public Set<T> getElements() {
        try {
            lock.readLock().lock();
            return ImmutableSet.copyOf(bucket.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns true if this bucket contains the specified element.
     *
     * @param e the element to test
     * @return true if this bucket contains the specified element
     */
    public boolean contains(T e) {
        Objects.requireNonNull(e);
        try {
            lock.readLock().lock();
            return bucket.containsKey(e);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Cancels the auto-deletion job.
     */
    public void cancelTimer() {
        if (timer != null) {
            timer.cancel();
        }
    }

    /**
     * Deletes all bucket entries that have been stored for longer than
     * {@code keepTime}.
     */
    private void cleanUp() {
        try {
            lock.writeLock().lock();
            long currentTime = System.currentTimeMillis();
            bucket.values().removeIf(entry -> entry < currentTime);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
