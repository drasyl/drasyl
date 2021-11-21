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

import io.netty.channel.ChannelPromise;

import java.util.LinkedList;
import java.util.List;

import static org.drasyl.util.Preconditions.requirePositive;

public class SimpleWindow implements Window {
    private final int capacity;
    private final List<Frame> queue;

    /**
     * Creates a new simple window.
     *
     * @param capacity the window size/capacity
     */
    public SimpleWindow(final int capacity) {
        this.capacity = requirePositive(capacity);
        this.queue = new LinkedList<>();
    }

    @Override
    public boolean add(final GoBackNArqData msg, final ChannelPromise promise) {
        if (queue.size() < capacity) {
            queue.add(new Frame(msg, promise));

            return true;
        }

        return false;
    }

    @Override
    public ChannelPromise remove() {
        if (!queue.isEmpty()) {
            final Frame f = queue.remove(0);
            f.getMsg().release();
            return f.getPromise();
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
        for (final Frame f : queue) {
            f.getMsg().release();
            f.getPromise().tryFailure(cause);
        }

        queue.clear();
    }
}
