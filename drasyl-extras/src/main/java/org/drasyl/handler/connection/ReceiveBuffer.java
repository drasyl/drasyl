/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.handler.connection.Segment.add;
import static org.drasyl.handler.connection.Segment.greaterThan;
import static org.drasyl.handler.connection.Segment.greaterThanOrEqualTo;
import static org.drasyl.handler.connection.Segment.lessThan;
import static org.drasyl.handler.connection.Segment.lessThanOrEqualTo;
import static org.drasyl.handler.connection.Segment.sub;
import static org.drasyl.util.NumberUtil.min;
import static org.drasyl.util.Preconditions.requireNonNegative;

/**
 * Represents the receive buffer that holds incoming data received over a connection.
 * <p>
 * The receive buffer is used by the receiver to hold incoming data that has been successfully
 * received from the sender. The buffer allows the receiver to temporarily store the received data
 * until it can be processed by the receiving application.
 */
@SuppressWarnings("java:S4274")
public class ReceiveBuffer {
    private static final Logger LOG = LoggerFactory.getLogger(ReceiveBuffer.class);
    // linked list of bufs we are unable to read as preceding bytes are missing
    ReceiveBufferBlock head;
    // cumulated buf of bytes we can read
    ByteBuf headBuf;
    // number of entries on our linked list
    private int size;
    // number of bytes in our linked list
    private int bytes;

    ReceiveBuffer(final ReceiveBufferBlock head,
                  final ByteBuf headBuf,
                  final int size,
                  final int bytes) {
        this.headBuf = headBuf;
        this.head = head;
        this.size = requireNonNegative(size);
        this.bytes = requireNonNegative(bytes);
    }

    ReceiveBuffer() {
        this(null, null, 0, 0);
    }

    /**
     * Release all buffers in the queue and complete all listeners and promises.
     */
    public void release() {
        if (headBuf != null) {
            ReferenceCountUtil.touch(headBuf, "ReceiveBuffer release headBuf " + headBuf);
            headBuf.release();
            headBuf = null;
        }

        while (head != null) {
            ReferenceCountUtil.touch(headBuf, "ReceiveBuffer release head " + head);
            head.release();
            head = head.next;
        }

        size = 0;
        bytes = 0;
    }

    /**
     * The number of readable bytes.
     */
    public int bytes() {
        return bytes;
    }

    public int size() {
        return size;
    }

    @Override
    public String toString() {
        final StringBuilder blocks = new StringBuilder();
        ReceiveBufferBlock current = head;
        while (current != null) {
            if (blocks.length() != 0) {
                blocks.append(",");
            }

            blocks.append(current.seq()).append("-").append(current.lastSeq());
            current = current.next;
        }

        return "RCV.BUF(len: " + bytes() + ", frg: " + blocks + ")";
    }

    @SuppressWarnings({ "java:S1066", "java:S3776", "java:S6541" })
    public void receive(final ChannelHandlerContext ctx,
                        final TransmissionControlBlock tcb,
                        final Segment seg) {
        ReferenceCountUtil.touch(seg, "ReceiveBuffer receive " + seg.toString());
        final ByteBuf content = seg.content();
        if (content.isReadable()) {
            // (T/TCP or TCP Fast Open not implemented; SYN/FIN flag might require special attention)
            assert !seg.isSyn() && !seg.isFin() : "not supported (yet)";

            if (head == null) {
                // first SEG to be added to RCV.WND?
                // SEG is located at the left edge of our RCV.WND?
                if (lessThanOrEqualTo(seg.seq(), tcb.rcvNxt()) && greaterThanOrEqualTo(seg.lastSeq(), tcb.rcvNxt())) {
                    receiveFirstSegmentLocatedAtLeftEdgeOfWindow(ctx, tcb, seg, content);
                }
                // SEG is within RCV.WND, but not at the left edge
                else if (greaterThan(seg.seq(), tcb.rcvNxt()) && lessThan(seg.seq(), add(tcb.rcvNxt(), tcb.rcvWnd()))) {
                    receiveFirstSegmentLocatedWithingWindow(ctx, tcb, seg, content);
                }
                else {
                    // SEG contains no elements within RCV.WND. Drop!
                    return;
                }
            }
            else {
                // receive buffer contains already segments. Check if SEG contains data that are before existing segments
                if (lessThan(seg.seq(), head.seq())) {
                    // SEG is located at the left edge of our RCV.WND?
                    if (lessThanOrEqualTo(seg.seq(), tcb.rcvNxt()) && greaterThanOrEqualTo(seg.lastSeq(), tcb.rcvNxt())) {
                        receiveSegmentLocatedAtLeftEdgeOfWindowAndBeforeHead(ctx, tcb, seg, content);
                    }
                    // SEG is within RCV.WND, but not at the left edge
                    else if (greaterThan(seg.seq(), tcb.rcvNxt()) && lessThan(seg.seq(), add(tcb.rcvNxt(), tcb.rcvWnd()))) {
                        receiveSegmentLocatedWithingWindowAndBeforeHead(ctx, tcb, seg, content);
                    }
                    else {
                        // SEG contains no elements within RCV.WND. Drop!
                        return;
                    }
                }
            }

            // does SEG contain something we can add after the header (or other fragments)
            ReceiveBufferBlock current = head;
            while (current != null && unallocatedBytes(tcb) > 0) {
                // first, check if there is space between current and any next fragment
                if (current.next == null || lessThan(add(current.seq(), current.len()), current.next.seq())) {
                    // second, does SEQ contain data that can be placed after current AND is SEG before any present next fragment?
                    if (lessThan(current.lastSeq(), seg.lastSeq()) && (current.next == null || lessThan(seg.seq(), current.next.seq()))) {
                        // does SEG overlap with current?
                        if (lessThan(current.lastSeq(), seg.seq())) {
                            receiveNonOverlappingSegmentLocatedAfterHead(ctx, tcb, seg, current, content);
                        }
                        else {
                            receiveOverlappingSegmentLocatedAfterHead(ctx, tcb, seg, current, content);
                        }
                    }
                }

                current = current.next;
            }

            // check if we can cumulate received segments
            LOG.trace("head = {}; RCV.NXT = {}", () -> head, tcb::rcvNxt);
            while (head != null && head.seq() == tcb.rcvNxt()) {
                // consume head
                if (LOG.isTraceEnabled()) {
                    LOG.trace(
                            "{} Head fragment `{}` is located at left edge of RCV.WND [{},{}]. Consume it, advance RCV.NXT by {}, and set head to {}.",
                            ctx::channel,
                            () -> head,
                            tcb::rcvNxt,
                            () -> add(tcb.rcvNxt(), tcb.rcvWnd() - 1),
                            head::len,
                            () -> head.next
                    );
                }
                tcb.rcvNxt(ctx, add(tcb.rcvNxt(), head.len()));
                addToHeadBuf(ctx, head.content());
                head = head.next;
                size--;
                assert head == null || lessThanOrEqualTo(tcb.rcvNxt(), head.seq()) : tcb.rcvNxt() + " must be less than or equal to " + head;
            }
            tcb.updateRcvWnd(ctx);
        }
        else {
            tcb.rcvNxt(ctx, seg.nxtSeq());
        }
    }

    private void receiveFirstSegmentLocatedAtLeftEdgeOfWindow(final ChannelHandlerContext ctx,
                                                              final TransmissionControlBlock tcb,
                                                              final Segment seg,
                                                              final ByteBuf content) {
        final int index;
        final long seq;
        final int length;
        // as SEG might start before RCV.NXT we should start reading RCV.NXT
        seq = tcb.rcvNxt();
        index = (int) sub(tcb.rcvNxt(), seg.seq());
        // ensure that we do not exceed RCV.WND
        length = min(unallocatedBytes(tcb), seg.len()) - index;
        final ReceiveBufferBlock block = new ReceiveBufferBlock(seq, content.retainedSlice(content.readerIndex() + index, length));
        if (LOG.isTraceEnabled()) {
            LOG.trace(
                    "{} Received SEG `{}`. SEG contains data [{},{}] and is located at left edge of RCV.WND [{},{}]. Use data [{},{}]: {}.",
                    ctx::channel,
                    () -> seg,
                    seg::seq,
                    seg::lastSeq,
                    tcb::rcvNxt,
                    () -> add(tcb.rcvNxt(), tcb.rcvWnd() - 1),
                    () -> seq,
                    () -> add(seq, ((long) length) - 1),
                    () -> block
            );
        }
        head = block;
        size++;
        bytes += length;
    }

    private void receiveFirstSegmentLocatedWithingWindow(final ChannelHandlerContext ctx,
                                                         final TransmissionControlBlock tcb,
                                                         final Segment seg,
                                                         final ByteBuf content) {
        final int length;
        final int index;
        final long seq;
        // start SEG as from the beginning
        seq = seg.seq();
        index = 0;
        // ensure that we do not exceed RCV.WND
        final int offsetRcvNxtToSeq = (int) sub(seg.seq(), tcb.rcvNxt());
        length = min(unallocatedBytes(tcb) - offsetRcvNxtToSeq, seg.len());
        final ReceiveBufferBlock block = new ReceiveBufferBlock(seq, content.retainedSlice(content.readerIndex() + index, length));
        if (LOG.isTraceEnabled()) {
            LOG.trace(
                    "{} Received SEG `{}`. SEG contains data [{},{}] is within RCV.WND [{},{}] but creates a hole of {} bytes. Use data [{},{}]: {}.",
                    ctx::channel,
                    () -> seg,
                    seg::seq,
                    seg::lastSeq,
                    tcb::rcvNxt,
                    () -> add(tcb.rcvNxt(), tcb.rcvWnd() - 1),
                    () -> sub(seg.seq(), tcb.rcvNxt()),
                    () -> seq,
                    () -> add(seq, ((long) length) - 1),
                    () -> block
            );
        }
        head = block;
        size++;
        bytes += length;
    }

    private void receiveSegmentLocatedAtLeftEdgeOfWindowAndBeforeHead(final ChannelHandlerContext ctx,
                                                                      final TransmissionControlBlock tcb,
                                                                      final Segment seg,
                                                                      final ByteBuf content) {
        final int index;
        final long seq;
        final int length;
        // as SEG might start before RCV.NXT we should start reading RCV.NXT
        seq = tcb.rcvNxt();
        index = (int) sub(tcb.rcvNxt(), seg.seq());
        // ensure that we do not exceed RCV.WND or read data already contained in head
        final long offsetSegToHead = sub(head.seq(), seg.seq());
        length = (int) (min(unallocatedBytes(tcb), offsetSegToHead, seg.len()) - index);
        final ReceiveBufferBlock block = new ReceiveBufferBlock(seq, content.retainedSlice(content.readerIndex() + index, length));
        assert lessThan(block.seq(), head.seq());
        block.next = head;
        if (LOG.isTraceEnabled()) {
            LOG.trace(
                    "{} Received SEG `{}`. SEG contains data [{},{}] and is located at left edge of RCV.WND [{},{}] and is located before current head fragment [{},{}]. Use data [{},{}]: {}.",
                    ctx::channel,
                    () -> seg,
                    seg::seq,
                    seg::lastSeq,
                    tcb::rcvNxt,
                    () -> add(tcb.rcvNxt(), tcb.rcvWnd() - 1),
                    head::seq,
                    head::lastSeq,
                    () -> add(seq, index),
                    () -> add(seq, add(index, ((long) length) - 1)),
                    () -> block
            );
        }
        head = block;
        size++;
        bytes += length;
    }

    private void receiveSegmentLocatedWithingWindowAndBeforeHead(final ChannelHandlerContext ctx,
                                                                 final TransmissionControlBlock tcb,
                                                                 final Segment seg,
                                                                 final ByteBuf content) {
        final int length;
        final int index;
        final long seq;
        // start SEG as from the beginning
        seq = seg.seq();
        index = 0;
        // ensure that we do not exceed RCV.WND or read data already contained in head
        final int offsetRcvNxtToSeq = (int) sub(seg.seq(), tcb.rcvNxt());
        final int offsetSeqHead = (int) sub(head.seq(), seg.seq());
        length = min(unallocatedBytes(tcb) - offsetRcvNxtToSeq, offsetSeqHead, seg.len());
        assert length >= 0 : "length must be non-negative but is " + length + "; TCB=" + tcb + "; unallocatedBytes(tcb)=" + unallocatedBytes(tcb) + "; offsetRcvNxtToSeq=" + offsetRcvNxtToSeq + "; offsetSeqHead=" + offsetSeqHead + "; seg.len()=" + seg.len();
        final ReceiveBufferBlock block = new ReceiveBufferBlock(seq, content.retainedSlice(content.readerIndex() + index, length));
        assert lessThan(block.seq(), head.seq());
        block.next = head;
        if (LOG.isTraceEnabled()) {
            LOG.trace(
                    "{} Received SEG `{}`. SEG contains data [{},{}] and is within RCV.WND [{},{}] and is located before current head fragment [{},{}]. Use data [{},{}]: {}.",
                    ctx::channel,
                    () -> seg,
                    seg::seq,
                    seg::lastSeq,
                    tcb::rcvNxt,
                    () -> add(tcb.rcvNxt(), tcb.rcvWnd() - 1),
                    head::seq,
                    head::lastSeq,
                    () -> add(seq, index),
                    () -> add(seq, add(index, ((long) length) - 1)),
                    () -> block
            );
        }
        head = block;
        size++;
        bytes += length;
    }

    private void receiveNonOverlappingSegmentLocatedAfterHead(final ChannelHandlerContext ctx,
                                                              final TransmissionControlBlock tcb,
                                                              final Segment seg,
                                                              final ReceiveBufferBlock current,
                                                              final ByteBuf content) {
        final long seq;
        final int length;
        final int index;
        // not overlapping
        seq = seg.seq();
        index = (int) sub(seq, seg.seq());
        if (current.next != null) {
            length = min(unallocatedBytes(tcb), seg.len(), (int) sub(current.next.seq(), seg.seq())) - index;
        }
        else {
            length = min(unallocatedBytes(tcb), seg.len() - index);
        }
        final ReceiveBufferBlock block = new ReceiveBufferBlock(seq, content.retainedSlice(content.readerIndex() + index, length));
        block.next = current.next;
        if (LOG.isTraceEnabled()) {
            LOG.trace(
                    "{} Received SEG `{}`. SEG contains data [{},{}] that can be placed between current fragment [{},{}] and next fragment [{},{}]. RCV.WND [{},{}]. Use data [{},{}]: {}.",
                    ctx::channel,
                    () -> seg,
                    seg::seq,
                    seg::lastSeq,
                    current::seq,
                    current::lastSeq,
                    () -> current.next != null ? current.next.seq() : "null",
                    () -> current.next != null ? current.next.lastSeq() : "null",
                    tcb::rcvNxt,
                    () -> add(tcb.rcvNxt(), tcb.rcvWnd() - 1),
                    () -> seq,
                    () -> add(seq, ((long) length) - 1),
                    () -> block
            );
        }
        current.next = block;
        size++;
        bytes += length;
    }

    private void receiveOverlappingSegmentLocatedAfterHead(final ChannelHandlerContext ctx,
                                                           final TransmissionControlBlock tcb,
                                                           final Segment seg,
                                                           final ReceiveBufferBlock current,
                                                           final ByteBuf content) {
        final long seq;
        final int length;
        final int index;
        // overlapping
        seq = add(current.lastSeq(), 1);
        index = (int) sub(seq, seg.seq());
        if (current.next != null) {
            length = min(unallocatedBytes(tcb), seg.len(), (int) sub(current.next.seq(), seg.seq())) - index;
        }
        else {
            length = min(unallocatedBytes(tcb), seg.len() - index);
        }
        final ReceiveBufferBlock block = new ReceiveBufferBlock(seq, content.retainedSlice(content.readerIndex() + index, length));
        assert current.next == null || lessThan(block.seq(), current.next.seq());
        block.next = current.next;
        if (LOG.isTraceEnabled()) {
            LOG.trace(
                    "{} Received SEG `{}`. SEG contains data [{},{}] that can be placed directly after current fragment [{},{}] and before next fragment [{},{}]. RCV.WND [{},{}]. Use data [{},{}]: {}.",
                    ctx::channel,
                    () -> seg,
                    seg::seq,
                    seg::lastSeq,
                    current::seq,
                    current::lastSeq,
                    () -> current.next != null ? current.next.seq() : "null",
                    () -> current.next != null ? current.next.lastSeq() : "null",
                    tcb::rcvNxt,
                    () -> add(tcb.rcvNxt(), tcb.rcvWnd() - 1),
                    () -> seq,
                    () -> add(seq, ((long) length) - 1),
                    () -> block
            );
        }
        current.next = block;
        size++;
        bytes += length;
    }

    private void addToHeadBuf(final ChannelHandlerContext ctx, final ByteBuf next) {
        if (headBuf == null) {
            // create new cumulation
            headBuf = next;
        }
        else if (headBuf instanceof CompositeByteBuf) {
            // add component
            final CompositeByteBuf composite = (CompositeByteBuf) headBuf;
            composite.addComponent(true, next);
        }
        else {
            // create composite
            final CompositeByteBuf composite = ctx.alloc().compositeBuffer();
            composite.addComponent(true, headBuf);
            composite.addComponent(true, next);
            headBuf = composite;
        }
    }

    /**
     * Passes, if any, readable bytes that this buffer contains to the receiving application.
     *
     * @param ctx the {@link ConnectionHandler}'s context
     * @param tcb the transmission control block this
     */
    public void fireRead(final ChannelHandlerContext ctx, final TransmissionControlBlock tcb) {
        assert tcb.receiveBuffer() == this : "this RCV.BUF does not belong to given TCB";
        final int readableBytes = readableBytes();
        if (readableBytes > 0) {
            bytes -= readableBytes;
            final ByteBuf headBuf1 = headBuf;
            headBuf = null;

            // receiver's SWS avoidance algorithms
            // RFC 9293, Section 3.8.6.2.2
            // https://www.rfc-editor.org/rfc/rfc9293.html#section-3.8.6.2.2

            // total receive buffer space is RCV.BUFF
            // RCV.USER octets of this total may be tied up with data that has been received and acknowledged but that the user process has not yet consumed
            final double fr = 0.5; // Fr is a fraction whose recommended value is 1/2
            final long availableSpace = tcb.rcvBuff() - tcb.rcvUser() - tcb.rcvWnd();
            if (availableSpace >= min((long) (fr * tcb.rcvBuff()), tcb.effSndMss())) {
                tcb.updateRcvWnd(ctx);
            }
            else if (LOG.isTraceEnabled()) {
                LOG.trace("{} Receiver's SWS avoidance: Keep RCV.WND fixed to {} and do not advertise space available of {} bytes.", ctx.channel(), tcb.rcvWnd(), availableSpace);
            }

            LOG.trace("{} Pass RCV.BUF ({} bytes) inbound to channel. {} bytes remain in RCV.WND. Increase RCV.WND to {} bytes.", ctx::channel, () -> readableBytes, () -> bytes, tcb::rcvWnd);
            ctx.fireChannelRead(headBuf1);
            ctx.fireChannelReadComplete();
        }
    }

    /**
     * Returns the amount of bytes that this buffer can pass to the receiving application.
     *
     * @return the amount of bytes that this buffer can pass to the receiving application
     */
    public int readableBytes() {
        return headBuf != null ? headBuf.readableBytes() : 0;
    }

    /**
     * Returns {@code true} if this buffer contains at least one byte ready to pass to the receiving
     * application.
     *
     * @return {@code true} if this buffer contains at least one byte ready to pass to the receiving
     * application
     */
    public boolean isReadable() {
        return headBuf != null && headBuf.isReadable();
    }

    int unallocatedBytes(final TransmissionControlBlock tcb) {
        return tcb.rcvBuff() - bytes;
    }

    @SuppressWarnings("java:S2160")
    static class ReceiveBufferBlock extends DefaultByteBufHolder {
        private final long seq;
        ReceiveBufferBlock next;

        public ReceiveBufferBlock(final long seq, final ByteBuf buf) {
            super(buf);
            this.seq = seq;
        }

        @Override
        public String toString() {
            return "ReceiveBufferBlock{" +
                    "seq=" + seq +
                    ", len=" + len() +
                    ", lastSeq=" + lastSeq() +
                    ", next=" + (next != null ? next.seq() : "null") +
                    '}';
        }

        public long seq() {
            return seq;
        }

        public int len() {
            return content().readableBytes();
        }

        public long lastSeq() {
            return add(seq(), len() - 1L);
        }
    }
}
