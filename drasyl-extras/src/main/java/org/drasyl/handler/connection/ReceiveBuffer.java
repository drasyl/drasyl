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

import java.nio.channels.ClosedChannelException;

import static java.util.Objects.requireNonNull;

// FIXME: add maximum capacity size?
// FIXME: add support for out-of-order?
class ReceiveBuffer {
    private static final ClosedChannelException DUMMY_CAUSE = new ClosedChannelException();
    private final Channel channel;
    private final CoalescingBufferQueue queue;

    ReceiveBuffer(final Channel channel,
                  final CoalescingBufferQueue queue) {
        this.channel = requireNonNull(channel);
        this.queue = requireNonNull(queue);
    }

    ReceiveBuffer(final Channel channel) {
        this(channel, new CoalescingBufferQueue(channel, 4, false));
    }

    /**
     * Add a buffer to the end of the queue and associate a promise with it that should be completed
     * when all the buffer's bytes have been consumed from the queue and written.
     *
     * @param buf to add to the tail of the queue
     */
    public void add(final ByteBuf buf) {
        queue.add(buf);
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
     * @param bytes the maximum number of readable bytes in the returned {@link ByteBuf}, if {@code
     *              bytes} is greater than {@link #readableBytes} then a buffer of length {@link
     *              #readableBytes} is returned.
     * @return a {@link ByteBuf} composed of the enqueued buffers.
     */
    public ByteBuf remove(final int bytes) {
        return queue.remove(bytes, channel.newPromise().setSuccess());
    }

    /**
     * Release all buffers in the queue and complete all listeners and promises.
     */
    public void release() {
        queue.releaseAndFailAll(DUMMY_CAUSE);
    }

    /**
     * The number of readable bytes.
     */
    public int readableBytes() {
        return queue.readableBytes();
    }
}
