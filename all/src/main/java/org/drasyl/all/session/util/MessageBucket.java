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

package org.drasyl.all.session.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.EvictingQueue;

/**
 * A thread-safe, size-limited fifo-queue that stores the IDs of the latest
 * incoming messages.
 */
public class MessageBucket {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(MessageBucket.class);
    private final ReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final EvictingQueue<String> messages;

    /**
     * Creates a new message bucket with a given size limit.
     * 
     * @param limit the size limit
     * @throws IllegalArgumentException if the limit is <= 0
     */
    public MessageBucket(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("The limit must be positive.");
        }
        messages = EvictingQueue.create(limit);
    }

    /**
     * Returns a copy of the message bucket as a list.
     * 
     * @return the message bucket contents
     */
    public List<String> getMessages() {
        try {
            lock.readLock().lock();
            return new ArrayList<>(messages);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Adds the message ID to the message bucket if it is not already present.
     * Otherwise moves that element to the head of the queue.
     * 
     * @param messageID the message ID
     * @return true if the message ID wasn't already present in the bucket
     * @throws NullPointerException if the message ID is null
     */
    public boolean add(String messageID) {
        Objects.requireNonNull(messageID);
        try {
            lock.writeLock().lock();
            boolean rtn = !messages.remove(messageID);
            messages.add(messageID);
            return rtn;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns true if the message bucket contains the specified message ID.
     * 
     * @param messageID the message ID
     * @return true if the message bucket contains the specified message ID
     */
    public boolean contains(String messageID) {
        Objects.requireNonNull(messageID);
        try {
            lock.readLock().lock();
            return messages.contains(messageID);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns true if the capacity limit of the message bucket has been reached.
     * 
     * @return true if the capacity limit of the message bucket has been reached
     */
    public boolean isAtFullCapacity() {
        try {
            lock.readLock().lock();
            return messages.remainingCapacity() <= 0;
        } finally {
            lock.readLock().unlock();
        }
    }
}
