/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.connection;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CoalescingBufferQueue;

import static java.util.Objects.requireNonNull;

/**
 * Holds data enqueued by the application to be written to the network. This FIFO queue also updates
 * the {@link io.netty.channel.Channel} writability for the bytes it holds.
 */
public class SendBuffer {
    private CoalescingBufferQueue queue;

    SendBuffer(final CoalescingBufferQueue queue) {
        this.queue = requireNonNull(queue);
    }

    SendBuffer(final Channel channel) {
        this(new CoalescingBufferQueue(channel, 4, true));
    }

    /**
     * Add a buffer to the end of the queue and associate a promise with it that should be completed
     * when all the buffer's bytes have been consumed from the queue and written.
     *
     * @param buf     to add to the tail of the queue
     * @param promise to complete when all the bytes have been consumed and written, can be void.
     */
    public void add(final ByteBuf buf, final ChannelPromise promise) {
        queue.add(buf, promise);
    }

    /**
     * Are there pending buffers in the queue.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Remove a {@link ByteBuf} from the queue with the specified number of bytes. Any added buffer
     * who's bytes are fully consumed during removal will have it's promise completed when the
     * passed aggregate {@link ChannelPromise} completes.
     *
     * @param bytes            the maximum number of readable bytes in the returned {@link ByteBuf},
     *                         if {@code bytes} is greater than {@link #readableBytes} then a buffer
     *                         of length {@link #readableBytes} is returned.
     * @param aggregatePromise used to aggregate the promises and listeners for the constituent
     *                         buffers.
     * @return a {@link ByteBuf} composed of the enqueued buffers.
     */
    public ByteBuf remove(final int bytes, final ChannelPromise aggregatePromise) {
        return queue.remove(bytes, aggregatePromise);
    }

    /**
     * Release all buffers in the queue and complete all listeners and promises.
     */
    public void releaseAndFailAll(final Throwable cause) {
        queue.releaseAndFailAll(cause);
    }

    /**
     * The number of readable bytes.
     */
    public int readableBytes() {
        return queue.readableBytes();
    }

    @Override
    public String toString() {
        return String.valueOf(readableBytes());
    }
}
