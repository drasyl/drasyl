package org.drasyl.handler.connection;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CoalescingBufferQueue;

import java.nio.channels.ClosedChannelException;

import static java.util.Objects.requireNonNull;

// FIXME: add maximum capacity size?
// FIXME: add support for out-of-order?
class ReceiveBuffer {
    private static final ClosedChannelException DUMMY_CAUSE = new ClosedChannelException();
    private final CoalescingBufferQueue queue;
    private final ChannelHandlerContext ctx;

    ReceiveBuffer(final ChannelHandlerContext ctx,
                  final CoalescingBufferQueue queue) {
        this.ctx = requireNonNull(ctx);
        this.queue = requireNonNull(queue);
    }

    ReceiveBuffer(final ChannelHandlerContext ctx) {
        this(ctx, new CoalescingBufferQueue(ctx.channel(), 4, false));
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
        return queue.remove(bytes, ctx.newPromise().setSuccess());
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
