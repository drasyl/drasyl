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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CoalescingBufferQueue;
import io.netty.channel.DelegatingChannelPromiseNotifier;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static io.netty.util.ReferenceCountUtil.safeRelease;
import static io.netty.util.internal.PlatformDependent.throwException;
import static java.util.Objects.requireNonNull;

/**
 * Holds data enqueued by the application to be written to the network. This FIFO queue also updates
 * the {@link io.netty.channel.Channel} writability for the bytes it holds.
 */
public class SendBuffer {
    private static final Logger LOG = LoggerFactory.getLogger(SendBuffer.class);
    private final Channel channel;
    private SendBufferEntry head;
    private SendBufferEntry tail;
    private ReadMark readMark;
    private int acknowledgementIndex;
    private int size;
    private int bytes;
    private int acknowledgeableBytes;
    private CoalescingBufferQueue queue;

    SendBuffer(final Channel channel,
               final CoalescingBufferQueue queue) {
        this.channel = requireNonNull(channel);
        this.queue = requireNonNull(queue);
    }

    SendBuffer(final Channel channel) {
        this(channel, new CoalescingBufferQueue(channel, 4, true));
    }

    private static ChannelFutureListener toChannelFutureListener(ChannelPromise promise) {
        return promise.isVoid() ? null : new DelegatingChannelPromiseNotifier(promise);
    }

    /**
     * Add a buffer to the end of the queue and associate a promise with it that should be completed
     * when all the buffer's bytes have been consumed from the queue and written.
     *
     * @param buf     to add to the tail of the queue
     * @param promise to complete when all the bytes have been consumed and written, can be void.
     */
    public void add(final ByteBuf buf, final ChannelPromise promise) {
        queue.add(buf.retainedSlice(), promise);

        SendBufferEntry entry = new SendBufferEntry(buf, promise);
        if (head == null) {
            // first entry
            head = tail = entry;
            readMark = new ReadMark(head);
        }
        else {
            // add to end
            tail.next = entry;
            tail = entry;

            if (readMark.remainingBytes() == 0) {
                readMark = new ReadMark(readMark.entry.next);
            }
        }

        this.size += 1;
        this.bytes += buf.readableBytes();
    }

    public final ByteBuf read(final ByteBufAllocator alloc, int bytes) {
        ByteBuf toReturn = null;
        while (bytes > 0 && readMark != null && readMark.remainingBytes() > 0) {
            final SendBufferEntry currentEntry = readMark.entry;
            ByteBuf currentBuf = currentEntry.content();
            final int remainingBytes = readMark.remainingBytes();
            if (bytes < remainingBytes) {
                // take part of buf
                currentBuf = currentBuf.retainedSlice(readMark.index, bytes);
                readMark.index += bytes;
                acknowledgeableBytes += bytes;
                bytes = 0;
                toReturn = toReturn == null ? currentBuf : compose(alloc, toReturn, currentBuf);
            }
            else {
                // read whole buf
                currentBuf = currentBuf.retainedSlice(readMark.index, remainingBytes);
                readMark.index += remainingBytes;
                acknowledgeableBytes += remainingBytes;
                bytes -= remainingBytes;
                toReturn = toReturn == null ? currentBuf : compose(alloc, toReturn, currentBuf);

                if (currentEntry.next != null) {
                    readMark = new ReadMark(currentEntry.next);
                }
            }
        }

        if (toReturn == null) {
            toReturn = Unpooled.EMPTY_BUFFER;
        }

        return toReturn;
    }

    public ByteBuf read(int bytes) {
        return read(channel.alloc(), bytes);
    }

    public void acknowledge(int bytes) {
        bytes = Math.min(bytes, acknowledgeableBytes);

        // FIXME: check that we do not ack more then we read
        while (bytes > 0 && head != null) {
            final ByteBuf headBuf = head.content();
            final int remainingBytes = readMark != null && readMark.entry.content() == head.content() ? readMark.index - acknowledgementIndex : headBuf.readableBytes() - acknowledgementIndex;
            if (bytes < remainingBytes) {
                // ack part of buf
                acknowledgementIndex += bytes;
                this.acknowledgeableBytes -= bytes;
                queue.remove(bytes, channel.newPromise());
                this.bytes -= bytes;
                bytes = 0;
            }
            else {
                // ack whole buf
                if (head.promise != null) {
                    head.promise.trySuccess();
                }
                head = head.next;
                if (head == null) {
                    tail = null;
                    readMark = null;
                }
                acknowledgementIndex = 0;
                bytes -= remainingBytes;

                this.size -= 1;
                this.acknowledgeableBytes -= remainingBytes;
                queue.remove(remainingBytes, channel.newPromise());
                this.bytes -= remainingBytes;
            }
        }
    }

    protected ByteBuf compose(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf next) {
        if (cumulation instanceof CompositeByteBuf) {
            CompositeByteBuf composite = (CompositeByteBuf) cumulation;
            composite.addComponent(true, next);
            return composite;
        }
        return composeIntoComposite(alloc, cumulation, next);
    }

    protected final ByteBuf composeIntoComposite(ByteBufAllocator alloc,
                                                 ByteBuf cumulation,
                                                 ByteBuf next) {
        // Create a composite buffer to accumulate this pair and potentially all the buffers
        // in the queue. Using +2 as we have already dequeued current and next.
        CompositeByteBuf composite = alloc.compositeBuffer(size() + 2);
        try {
            composite.addComponent(true, cumulation);
            composite.addComponent(true, next);
        }
        catch (Throwable cause) {
            composite.release();
            safeRelease(next);
            throwException(cause);
        }
        return composite;
    }

    public final int size() {
        return size;
    }

    public final long bytes() {
        return bytes;
    }

    /**
     * The number of readable bytes.
     */
    public long readableBytes() {
        return bytes - acknowledgeableBytes;
    }

    public long acknowledgeableBytes() {
        return acknowledgeableBytes;
    }

    /**
     * Are there pending buffers in the queue.
     */
    public boolean isEmpty() {
        return head == null;
    }

    /**
     * Release all buffers in the queue and complete all listeners and promises.
     */
    public void releaseAndFailAll(final Throwable cause) {
        while (head != null) {
            head.release();
            head = head.next;
        }

        head = tail = null;
        readMark = null;
        acknowledgementIndex = 0;
        size = 0;
        bytes = 0;
        acknowledgeableBytes = 0;
    }

    @Override
    public String toString() {
        return "SND.BUF(rd: " + readableBytes() + ", ack: " + acknowledgeableBytes + ", len: " + bytes + ")";
    }

    private static class SendBufferEntry extends DefaultByteBufHolder {
        protected final ChannelPromise promise;
        private SendBufferEntry next;

        public SendBufferEntry(final ByteBuf buf, final ChannelPromise promise) {
            super(buf);
            this.promise = promise;
        }
    }

    private static class ReadMark {
        private final SendBufferEntry entry;
        private int index;

        public ReadMark(final SendBufferEntry entry, final int index) {
            this.entry = requireNonNull(entry);
            this.index = index;
        }

        public ReadMark(final SendBufferEntry entry) {
            this(entry, 0);
        }

        public int remainingBytes() {
            return entry.content().readableBytes() - index;
        }
    }
}
