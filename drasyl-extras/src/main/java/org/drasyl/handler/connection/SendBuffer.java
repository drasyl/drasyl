package org.drasyl.handler.connection;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CoalescingBufferQueue;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Holds data enqueued by the application to be written to the network. This FIFO queue also updates
 * the {@link io.netty.channel.Channel} writability for the bytes it holds.
 */
class SendBuffer {
    private final CoalescingBufferQueue queue;
    private Channel ch;

    SendBuffer(final CoalescingBufferQueue queue) {
        this.queue = requireNonNull(queue);
        this.ch = null;
    }

    SendBuffer(final ChannelHandlerContext ctx) {
        this(new CoalescingBufferQueue(ctx.channel(), 4, true));
        this.ch = ctx.channel();
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
}
