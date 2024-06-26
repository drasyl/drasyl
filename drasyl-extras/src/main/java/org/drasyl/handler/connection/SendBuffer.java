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

import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireNonNegative;

/**
 * Represents the send buffer that holds outgoing segments waiting to be sent over a connection.
 * <p>
 * The send buffer is responsible for managing the buffer of outgoing segments that are waiting to
 * be sent over the network. The send buffer is distinct from the outgoing segment queue, which is a
 * separate data structure that holds segments that have been scheduled for transmission, but not
 * yet sent. The send buffer, on the other hand, stores segments that are waiting to be scheduled
 * for transmission.
 */
public class SendBuffer {
    // used to get ByteBufAllocator
    private final Channel channel;
    // only used for controlling the channel writability according to the bytes contained in this buffer
    private final CoalescingBufferQueue queue;
    private long pushMark;

    SendBuffer(final Channel channel,
               final CoalescingBufferQueue queue,
               final long pushMark) {
        this.channel = requireNonNull(channel);
        this.queue = requireNonNull(queue);
        this.pushMark = requireNonNegative(pushMark);
    }

    SendBuffer(final Channel channel,
               final long pushMark) {
        this(channel, new CoalescingBufferQueue(channel, 4, true), pushMark);
    }

    SendBuffer(final Channel channel) {
        this(channel, 0);
    }

    /**
     * Add a buffer to the end of the queue and associate a promise with it that should be completed
     * when all the buffer's bytes have been consumed from the queue and written.
     *
     * @param buf     to add to the tail of the queue
     * @param promise to complete when all the bytes have been consumed and written, can be void.
     */
    public void enqueue(final ByteBuf buf, final ChannelPromise promise) {
        queue.add(buf, promise);
    }

    public void enqueue(final ByteBuf buf) {
        enqueue(buf, channel.newPromise());
    }

    public void push() {
        pushMark = length();
    }

    public boolean doPush() {
        return pushMark > 0;
    }

    /**
     * Attempts to retrieve a maximum of {@code bytes} from this buffer. However, there are two
     * scenarios where fewer bytes than requested may be returned:
     * <br>
     * (1) If the buffer doesn't contain as many bytes as requested. In this case, the entire buffer
     * content is returned.
     * <br>
     * (2) If a byte read from the buffer has been marked for immediate delivery to the recipient's
     * application. This "push" mark is set once the sender invokes {@link Channel#flush()}. This
     * typically occurs when the sender has finished writing a complete message to the channel.
     *
     * @param bytes  bytes to read
     * @param doPush this {@link AtomicBoolean} is set to {@code true} if a byte that needs to be
     *               pushed has been read.
     */
    public final ByteBuf read(final long bytes,
                              final AtomicBoolean doPush,
                              final ChannelPromise promise) {
        long readableBytes = bytes;
        if (pushMark > 0 && readableBytes > pushMark) {
            // only read til push mark
            readableBytes = pushMark;
        }

        final ByteBuf toReturn = queue.remove((int) readableBytes, promise);

        if (pushMark > 0) {
            // update push mark
            pushMark -= toReturn.readableBytes();

            if (pushMark == 0) {
                // push mark reached, PSH flag must be set on SEG
                doPush.set(true);
            }
        }

        return toReturn;
    }

    /**
     * The number of readable bytes.
     */
    public long length() {
        return queue.readableBytes();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Release all buffers in the queue and complete all listeners and promises.
     */
    public void release() {
        fail(new ConnectionClosingException(channel));
    }

    public void fail(final ConnectionException e) {
        queue.releaseAndFailAll(e);
    }

    @Override
    public String toString() {
        return "SND.BUF(len: " + length() + ")";
    }
}
