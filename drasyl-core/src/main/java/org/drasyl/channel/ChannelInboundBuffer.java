/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.channel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static java.util.Objects.requireNonNull;

/**
 * (Transport implementors only) an internal data structure used by {@link DrasylChannel} to store
 * its pending inbound read requests.
 */
public final class ChannelInboundBuffer {
    private static final AtomicLongFieldUpdater<ChannelInboundBuffer> TOTAL_PENDING_SIZE_UPDATER =
            AtomicLongFieldUpdater.newUpdater(ChannelInboundBuffer.class, "totalPendingSize");
    private static final AtomicIntegerFieldUpdater<ChannelInboundBuffer> FULL_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(ChannelInboundBuffer.class, "full");
    @SuppressWarnings({ "FieldCanBeLocal", "UnusedDeclaration" })
    private final DrasylChannel channel;
    private final Queue<ByteBuf> queue = PlatformDependent.newMpscQueue();
    @SuppressWarnings("UnusedDeclaration")
    private volatile long totalPendingSize;
    @SuppressWarnings("UnusedDeclaration")
    private volatile int full;

    ChannelInboundBuffer(final DrasylChannel channel) {
        this.channel = requireNonNull(channel);
    }

    public void addMessage(final ByteBuf msg) {
        final int size = msg.readableBytes();
        queue.add(msg);
        incrementPendingInboundBytes(size);
    }

    /**
     * Increment the pending bytes which will be read at some point. This method is thread-safe!
     */
    void incrementPendingInboundBytes(final long size) {
        if (size == 0) {
            return;
        }

        final long newWriteBufferSize = TOTAL_PENDING_SIZE_UPDATER.addAndGet(this, size);
        if (newWriteBufferSize > channel.parent().config().getReadBufferWaterMark().high()) {
            setFull();
        }
    }

    /**
     * Decrement the pending bytes which will be read at some point. This method is thread-safe!
     */
    void decrementPendingInboundBytes(final long size) {
        if (size == 0) {
            return;
        }

        final long newWriteBufferSize = TOTAL_PENDING_SIZE_UPDATER.addAndGet(this, -size);
        if (newWriteBufferSize < channel.parent().config().getReadBufferWaterMark().low()) {
            setNotFull();
        }
    }

    private void setNotFull() {
        for (; ; ) {
            final int oldValue = full;
            final int newValue = oldValue & ~1;
            if (FULL_UPDATER.compareAndSet(this, oldValue, newValue)) {
                break;
            }
        }
    }

    private void setFull() {
        for (; ; ) {
            final int oldValue = full;
            final int newValue = oldValue | 1;
            if (FULL_UPDATER.compareAndSet(this, oldValue, newValue)) {
                break;
            }
        }
    }

    /**
     * Returns {@code true} if and only if the total number of pending bytes did not exceed the read
     * watermark of the {@link Channel}.
     */
    public boolean isNotFull() {
        return full == 0;
    }

    /**
     * Return the current message to read or {@code null} and removes it from the buffer or
     * {@code null} if nothing is there to read.
     */
    public ByteBuf remove() {
        final ByteBuf current = queue.poll();
        if (current != null) {
            final int size = current.readableBytes();
            decrementPendingInboundBytes(size);
        }
        return current;
    }

    /**
     * Returns {@code true} if there are no pending inbound read requests messages in this
     * {@link ChannelInboundBuffer} or {@code false} otherwise.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void close() {
        Object msg;
        while ((msg = queue.poll()) != null) {
            ReferenceCountUtil.release(msg);
        }
    }
}
