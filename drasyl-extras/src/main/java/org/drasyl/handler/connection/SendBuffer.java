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
import io.netty.channel.ChannelPromise;
import io.netty.channel.CoalescingBufferQueue;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

import static io.netty.util.ReferenceCountUtil.safeRelease;
import static io.netty.util.internal.PlatformDependent.throwException;
import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireNonNegative;

/**
 * Holds data enqueued by the application to be written to the network. This FIFO queue also updates
 * the {@link io.netty.channel.Channel} writability for the bytes it holds.
 */
public class SendBuffer {
    private static final Logger LOG = LoggerFactory.getLogger(SendBuffer.class);
    // used to get ByteBufAllocator
    private final Channel channel;
    // only used for controlling the channel writability according to the bytes contained in this buffer
    private final CoalescingBufferQueue queue;
    // linked list of ByteBuf/Promise pairs of data to be sent
    SendBufferEntry head;
    SendBufferEntry tail;
    // indicates what element in our linked list and how many of it bytes have been read
    ReadMark readMark;
    // indicate how many bytes of linked list head have been ACKed
    int acknowledgementIndex;
    // number of entries in our linked list
    private int size;
    // number of bytes in our linked list
    private int bytes;
    private int acknowledgeableBytes;

    SendBuffer(final Channel channel,
               final CoalescingBufferQueue queue,
               final SendBufferEntry head,
               final SendBufferEntry tail,
               final ReadMark readMark,
               final int acknowledgementIndex,
               final int size,
               final int bytes,
               final int acknowledgeableBytes) {
        this.channel = requireNonNull(channel);
        this.head = head;
        this.tail = tail;
        this.readMark = readMark;
        this.acknowledgementIndex = requireNonNegative(acknowledgementIndex);
        this.size = requireNonNegative(size);
        this.bytes = requireNonNegative(bytes);
        this.acknowledgeableBytes = acknowledgeableBytes;
        this.queue = requireNonNull(queue);
    }

    SendBuffer(final Channel channel) {
        this(channel, new CoalescingBufferQueue(channel, 4, true), null, null, null, 0, 0, 0, 0);
    }

    /**
     * Add a buffer to the end of the queue and associate a promise with it that should be completed
     * when all the buffer's bytes have been consumed from the queue and written.
     *
     * @param buf     to add to the tail of the queue
     * @param promise to complete when all the bytes have been consumed and written, can be void.
     */
    public void enqueue(final ByteBuf buf, final ChannelPromise promise) {
        incrementPendingOutboundBytes(buf);

        SendBufferEntry entry = new SendBufferEntry(buf, promise);
        if (head == null) {
            // first entry, set as head
            head = tail = entry;
            readMark = new ReadMark(head);
        }
        else {
            // add as tail
            tail.next = entry;
            tail = entry;

            // if previous tail has been fully read, we need to update the ReadMark
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

    public ByteBuf unacknowledged(final ByteBufAllocator alloc, int bytes) {
        bytes = Math.min(bytes, acknowledgeableBytes);

        ByteBuf toReturn = null;
        SendBufferEntry currentEntry = head;
        while (bytes > 0 && currentEntry != null) {
            ByteBuf currentBuf = currentEntry.content();
            final int index;
            int length;
            if (currentEntry == head) {
                index = acknowledgementIndex;
            }
            else {
                index = 0;
            }
            if (readMark != null && readMark.entry.content() == currentBuf) {
                length = readMark.entry.content().readableBytes() - readMark.remainingBytes() - index;
            }
            else {
                length = currentBuf.readableBytes() - index;
            }

            if (length > bytes) {
                length = bytes;
            }

            currentBuf = currentBuf.retainedSlice(index, length);
            bytes -= length;
            toReturn = toReturn == null ? currentBuf : compose(alloc, toReturn, currentBuf);

            currentEntry = currentEntry.next;
        }

        if (toReturn == null) {
            toReturn = Unpooled.EMPTY_BUFFER;
        }

        return toReturn;
    }

    public ByteBuf unacknowledged(final int bytes) {
        return unacknowledged(channel.alloc(), bytes);
    }

    public void acknowledge(int bytes) {
        LOG.trace("ACKnowledgement of {} bytes requested ({} ACKnowledgable bytes available}.", bytes, acknowledgeableBytes);
        bytes = Math.min(bytes, acknowledgeableBytes);

        while (bytes > 0 && head != null) {
            final ByteBuf headBuf = head.content();
            final int readBytesInHead;
            if (readMark != null && readMark.entry.content() == headBuf) {
                readBytesInHead = readMark.index;
            }
            else {
                readBytesInHead = headBuf.readableBytes();
            }
            LOG.trace("{} bytes in headBuf are ByteBuf-readable.", headBuf.readableBytes());
            LOG.trace("{} bytes in headBuf have been read.", readBytesInHead);
            LOG.trace("{} bytes in headBuf have already been ACKed.", acknowledgementIndex);
            final int acknowledgeableBytesInHead = readBytesInHead - acknowledgementIndex;
            final int bytesToAck = Math.min(acknowledgeableBytesInHead, bytes);
            LOG.trace("{} bytes in headBuf can be ACKed.", acknowledgeableBytesInHead);
            final int totalBytesAcked = acknowledgementIndex + bytesToAck;
            LOG.trace("{} bytes in headBuf are ACKed after this call.", totalBytesAcked);

            if (totalBytesAcked < headBuf.readableBytes()) {
                // ack part of buf
                LOG.trace("First {} bytes of entry {} have been ACKed.", bytesToAck, head);
                acknowledgementIndex += bytesToAck;
                acknowledgeableBytes -= bytesToAck;
                decrementPendingOutboundBytes(bytesToAck);
                this.bytes -= bytesToAck;
                bytes = 0;
            }
            else {
                // ack whole buf
                LOG.trace("All {} bytes of entry {} have been ACKed.", bytes, head);
                if (head.promise != null) {
                    head.promise.trySuccess();
                    head.release();
                }
                head = head.next;
                if (head == null) {
                    tail = null;
                    readMark = null;
                }
                acknowledgementIndex = 0;
                bytes -= bytesToAck;

                this.size -= 1;
                this.acknowledgeableBytes -= bytesToAck;
                decrementPendingOutboundBytes(bytesToAck);
                this.bytes -= bytesToAck;
                assert head != null || this.bytes == 0;
            }
        }
    }

    protected ByteBuf compose(final ByteBufAllocator alloc,
                              final ByteBuf cumulation,
                              final ByteBuf next) {
        if (cumulation instanceof CompositeByteBuf) {
            final CompositeByteBuf composite = (CompositeByteBuf) cumulation;
            composite.addComponent(true, next);
            return composite;
        }
        return composeIntoComposite(alloc, cumulation, next);
    }

    protected final ByteBuf composeIntoComposite(final ByteBufAllocator alloc,
                                                 final ByteBuf cumulation,
                                                 final ByteBuf next) {
        // Create a composite buffer to accumulate this pair and potentially all the buffers
        // in the queue. Using +2 as we have already dequeued current and next.
        final CompositeByteBuf composite = alloc.compositeBuffer(size() + 2);
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
    public void release() {
        while (head != null) {
            head.release();
            head = head.next;
        }

        head = tail = null;
        readMark = null;
        acknowledgementIndex = 0;
        size = 0;
        decrementPendingOutboundBytes(bytes);
        bytes = 0;
        acknowledgeableBytes = 0;
    }

    @Override
    public String toString() {
        return "SND.BUF(rd: " + readableBytes() + ", ack: " + acknowledgeableBytes() + ", len: " + bytes() + ")";
    }

    private void incrementPendingOutboundBytes(final ByteBuf buf) {
        queue.add(buf.retainedSlice(), channel.newPromise());
    }

    private void decrementPendingOutboundBytes(final int bytes) {
        queue.remove(bytes, channel.newPromise()).release();
    }

    static class SendBufferEntry extends DefaultByteBufHolder {
        protected final ChannelPromise promise;
        private SendBufferEntry next;

        SendBufferEntry(final ByteBuf buf,
                        final ChannelPromise promise,
                        final SendBufferEntry next) {
            super(buf);
            this.promise = requireNonNull(promise);
            this.next = next;
        }

        SendBufferEntry(final ByteBuf buf, final ChannelPromise promise) {
            this(buf, promise, null);
        }

        @Override
        public String toString() {
            return "SendBufferEntry{" +
                    "content=" + content() +
                    ", promise=" + promise +
                    ", next=" + (next != null ? next.content() : "null") +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            SendBufferEntry that = (SendBufferEntry) o;
            return Objects.equals(promise, that.promise) && Objects.equals(next, that.next);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), promise, next);
        }
    }

    static class ReadMark {
        private final SendBufferEntry entry;
        private int index;

        ReadMark(final SendBufferEntry entry, final int index) {
            this.entry = requireNonNull(entry);
            this.index = requireNonNegative(index);
        }

        ReadMark(final SendBufferEntry entry) {
            this(entry, 0);
        }

        public int remainingBytes() {
            return entry.content().readableBytes() - index;
        }

        @Override
        public String toString() {
            return "ReadMark{" +
                    "entry=" + entry +
                    ", index=" + index +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ReadMark readMark = (ReadMark) o;
            return index == readMark.index && Objects.equals(entry, readMark.entry);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entry, index);
        }
    }
}
