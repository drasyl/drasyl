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
package org.drasyl.handler.arq.gbn;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.PendingWriteQueue;

import java.util.LinkedList;
import java.util.List;

import static org.drasyl.util.Preconditions.requirePositive;

/**
 * This class does model a sliding window in the Go-Back-N ARQ protocol.
 * <p>
 * Every message that is currently unacknowledged influences the channel's writability.
 */
class PendingQueueWindow implements Window {
    private final PendingWriteQueue pendingWriteQueue; //required, because all the backpressure APIs of netty are not accessible for us
    private final List<Frame> queue;
    private final int capacity;

    /**
     * Creates a new window, that does increase the channels pending writes.
     *
     * @param ctx      the handler context
     * @param capacity the window size/capacity
     */
    public PendingQueueWindow(final ChannelHandlerContext ctx, final int capacity) {
        this.capacity = requirePositive(capacity);
        this.pendingWriteQueue = new PendingWriteQueue(ctx);
        this.queue = new LinkedList<>();
    }

    @Override
    public boolean add(final GoBackNArqData msg, final ChannelPromise promise) {
        if (queue.size() < capacity) {
            queue.add(new Frame(msg, promise));
            pendingWriteQueue.add(msg, promise);

            return true;
        }

        return false;
    }

    @Override
    public ChannelPromise remove() {
        if (!queue.isEmpty()) {
            queue.remove(0);
            return pendingWriteQueue.remove();
        }

        return null;
    }

    @Override
    public List<Frame> getQueue() {
        return List.copyOf(queue);
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public int getFreeSpace() {
        return capacity - queue.size();
    }

    @Override
    public void removeAndFailAll(final Throwable cause) {
        queue.clear();
        pendingWriteQueue.removeAndFailAll(cause);
    }
}
