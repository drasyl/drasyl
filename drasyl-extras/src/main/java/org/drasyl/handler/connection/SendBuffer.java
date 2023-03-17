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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CoalescingBufferQueue;
import org.drasyl.util.NumberUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.util.ReferenceCountUtil.safeRelease;
import static io.netty.util.internal.PlatformDependent.throwException;
import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.ReliableTransportHandler.CONNECTION_CLOSING_ERROR;
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
    private long pushMark;
    private long segmentizedRemainingBytes;
    private ChannelPromise segmentizedFuture;

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

        final SendBufferEntry entry = new SendBufferEntry(buf, promise);
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
            if (!readMark.hasRemainingBytes()) {
                readMark = new ReadMark(readMark.next());
            }
        }

        this.size += 1;
        this.bytes += buf.readableBytes();
    }

    public void enqueue(final ByteBuf buf) {
        enqueue(buf, channel.newPromise());
    }

    public void push() {
        pushMark = readableBytes();
    }

    /**
     * FIXME: doPush parameter ist sehr hässlich gelöst
     */
    public final ByteBuf read(final ByteBufAllocator alloc, int bytes, final AtomicBoolean doPush) {
        ByteBuf toReturn = null;
        while (bytes > 0 && readMark != null && readMark.hasRemainingBytes()) {
            // read as many bytes as requested and available
            int bytesToRead = NumberUtil.min(bytes, readMark.remainingBytes());
            final ByteBuf buf = readMark.content().retainedSlice(readMark.index, bytesToRead);

            if (pushMark > 0) {
                if (pushMark - bytesToRead <= 0) {
                    doPush.set(true);
                    pushMark = 0;
                }
                else {
                    pushMark -= bytesToRead;
                }
            }

            // update counter
            readMark.index += bytesToRead;
            acknowledgeableBytes += bytesToRead;

            if (segmentizedRemainingBytes > 0) {
                if (segmentizedRemainingBytes - bytesToRead <= 0) {
                    segmentizedRemainingBytes = 0;
                    segmentizedFuture.trySuccess();
                }
                else {
                    segmentizedRemainingBytes -= bytesToRead;
                }
            }

            bytes -= bytesToRead;

            // compose buf
            toReturn = toReturn == null ? buf : compose(alloc, toReturn, buf);

            // if necessary, move ReadMark to next buf
            if (!readMark.hasRemainingBytes() && readMark.next() != null) {
                // whole buf read, move ReadMark to next buf
                readMark = new ReadMark(readMark.next());
            }
        }

        // if nothing was available to read, return empty buf
        if (toReturn == null) {
            toReturn = Unpooled.EMPTY_BUFFER;
        }

        return toReturn;
    }

    public ByteBuf read(final int bytes, final AtomicBoolean doPush) {
        return read(channel.alloc(), bytes, doPush);
    }

    public ByteBuf unacknowledged(final ByteBufAllocator alloc, int offset, int bytes) {
        // ensure that only as many bytes are requested as are available
        bytes = NumberUtil.min(bytes, acknowledgeableBytes);

        ByteBuf toReturn = null;
        SendBufferEntry currentEntry = head;
        while (bytes > 0 && currentEntry != null) {
            int index;
            int length;
            if (currentEntry == head) {
                // do not return bytes that have already been ACKed
                index = acknowledgementIndex;
            }
            else {
                // nothing ACKed in currentEntry, start from beginning
                index = 0;
            }

            if (readMark != null && readMark.content() == currentEntry.content()) {
                // do not return bytes that have not been read yet
                length = readMark.content().readableBytes() - readMark.remainingBytes() - index;
            }
            else {
                // whole buf has been read, return all bytes
                length = currentEntry.content().readableBytes() - index;
            }

            if (offset > 0) {
                if (offset >= length) {
                    // offset longer then current buf, skip hole buf
                    offset -= length;
                    continue;
                }
                else {
                    // we need to skip start of buf
                    index += offset;
                    length -= offset;
                    ;
                }
            }

            // do not return more bytes than requested
            if (length > bytes) {
                length = bytes;
            }

            final ByteBuf buf = currentEntry.content().retainedSlice(index, length);

            bytes -= length;

            // compose buf
            toReturn = toReturn == null ? buf : compose(alloc, toReturn, buf);

            // go to next entry
            currentEntry = currentEntry.next;
        }

        // if nothing unacknowledged is present, return empty buf
        if (toReturn == null) {
            toReturn = Unpooled.EMPTY_BUFFER;
        }

        return toReturn;
    }

    public ByteBuf unacknowledged(final int offset, final int bytes) {
        return unacknowledged(channel.alloc(), offset, bytes);
    }

    public ByteBuf unacknowledged(final int bytes) {
        return unacknowledged(0, bytes);
    }

    public void acknowledge(int bytes) {
        LOG.trace("ACKnowledgement of {} bytes requested ({} ACKnowledgable bytes available}.", bytes, acknowledgeableBytes);
        bytes = NumberUtil.min(bytes, acknowledgeableBytes);

        while (bytes > 0 && head != null) {
            final ByteBuf headBuf = head.content();
            final int readBytesInHead;
            if (readMark != null && readMark.content() == headBuf) {
                readBytesInHead = readMark.index;
            }
            else {
                readBytesInHead = headBuf.readableBytes();
            }
            LOG.trace("{} bytes in headBuf are ByteBuf-readable.", headBuf.readableBytes());
            LOG.trace("{} bytes in headBuf have been read.", readBytesInHead);
            LOG.trace("{} bytes in headBuf have already been ACKed.", acknowledgementIndex);
            final int acknowledgeableBytesInHead = readBytesInHead - acknowledgementIndex;
            final int bytesToAck = NumberUtil.min(acknowledgeableBytesInHead, bytes);
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

    public boolean hasOutstandingData() {
        return acknowledgeableBytes > 0;
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
        fail(CONNECTION_CLOSING_ERROR);
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

    public ChannelFuture allPrecedingDataHaveBeenSegmentized(final ChannelHandlerContext ctx) {
        segmentizedRemainingBytes = readableBytes();
        if (segmentizedRemainingBytes > 0) {
            assert segmentizedFuture == null;
            segmentizedFuture = ctx.newPromise();
            return segmentizedFuture;
        }
        else {
            return ctx.newSucceededFuture();
        }
    }

    public void fail(final ConnectionHandshakeException e) {
        while (head != null) {
            head.promise.tryFailure(e);
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
            return content().readableBytes() - index;
        }

        public boolean hasRemainingBytes() {
            return remainingBytes() > 0;
        }

        public ByteBuf content() {
            return entry.content();
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

        public SendBufferEntry next() {
            return entry.next;
        }
    }
}
