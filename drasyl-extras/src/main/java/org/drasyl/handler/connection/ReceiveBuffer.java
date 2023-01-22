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
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.SEQ_NO_SPACE;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.SerialNumberArithmetic.add;
import static org.drasyl.util.SerialNumberArithmetic.greaterThan;
import static org.drasyl.util.SerialNumberArithmetic.greaterThanOrEqualTo;
import static org.drasyl.util.SerialNumberArithmetic.lessThan;
import static org.drasyl.util.SerialNumberArithmetic.lessThanOrEqualTo;
import static org.drasyl.util.SerialNumberArithmetic.sub;

public class ReceiveBuffer {
    private static final Logger LOG = LoggerFactory.getLogger(ReceiveBuffer.class);
    private final Channel channel;
    // linked list of bufs we are unable to read as preceding bytes are missing
    ReceiveBufferEntry head;
    // cumulated buf if bytes we can read
    ByteBuf headBuf;
    // number of entries on our linked list
    private int size;
    // number of bytes in our linked list
    private int bytes;

    ReceiveBuffer(final Channel channel,
                  final ReceiveBufferEntry head,
                  final ByteBuf headBuf,
                  final int size,
                  final int bytes) {
        this.channel = requireNonNull(channel);
        this.headBuf = headBuf;
        this.head = head;
        this.size = requireNonNegative(size);
        this.bytes = requireNonNegative(bytes);
    }

    ReceiveBuffer(final Channel channel) {
        this(channel, null, null, 0, 0);
    }

    /**
     * Release all buffers in the queue and complete all listeners and promises.
     */
    public void release() {
        if (headBuf != null) {
            headBuf.release();
            headBuf = null;
        }

        while (head != null) {
            head.buf().release();
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
        return "RCV.BUF(len: " + bytes() + ", frg: " + size() + ")";
    }

    public void receive(final ChannelHandlerContext ctx,
                        final TransmissionControlBlock tcb,
                        final ConnectionHandshakeSegment seg) {
        ReferenceCountUtil.touch(seg, "ReceiveBuffer receive " + seg.toString());
        final ByteBuf content = seg.content();
        if (content.isReadable()) {
            if (head == null) {
                final long seq;
                final int index;
                final int length;

                // first SEG to be added to RCV.WND?
                // SEG is located at the left edge of our RCV.WND?
                if (lessThanOrEqualTo(seg.seq(), tcb.rcvNxt(), SEQ_NO_SPACE) && greaterThanOrEqualTo(seg.lastSeq(), tcb.rcvNxt(), SEQ_NO_SPACE)) {
                    // as SEG might start before RCV.NXT we should start reading RCV.NXT
                    seq = tcb.rcvNxt();
                    index = (int) sub(tcb.rcvNxt(), seg.seq(), SEQ_NO_SPACE);
                    // ensure that we do not exceed RCV.WND
                    length = Math.min((int) tcb.rcvWnd(), seg.len()) - index;
                    final ReceiveBufferEntry entry = new ReceiveBufferEntry(seq, content.retainedSlice(content.readerIndex() + index, length));
                    LOG.trace(
                            "{} Received SEG `{}`. SEG contains data [{},{}] and is located at left edge of RCV.WND [{},{}]. Use data [{},{}]: {}.",
                            channel,
                            seg,
                            seg.seq(),
                            seg.lastSeq(),
                            tcb.rcvNxt(),
                            add(tcb.rcvNxt(), tcb.rcvWnd(), SEQ_NO_SPACE),
                            seq,
                            add(seq, length - 1, SEQ_NO_SPACE),
                            entry
                    );
                    head = entry;
                    tcb.decrementRcvWnd(length);
                    size += 1;
                    bytes += length;
                }
                // SEG is within RCV.WND, but not at the left edge
                else if (greaterThan(seg.seq(), tcb.rcvNxt(), SEQ_NO_SPACE) && lessThan(seg.seq(), add(tcb.rcvNxt(), tcb.rcvWnd(), SEQ_NO_SPACE), SEQ_NO_SPACE)) {
                    // start SEG as from the beginning
                    seq = seg.seq();
                    index = 0;
                    // ensure that we do not exceed RCV.WND
                    final long offsetRcvNxtToSeq = sub(seg.seq(), tcb.rcvNxt(), SEQ_NO_SPACE);
                    length = Math.min((int) (tcb.rcvWnd() - offsetRcvNxtToSeq), seg.len());
                    final ReceiveBufferEntry entry = new ReceiveBufferEntry(seq, content.retainedSlice(content.readerIndex() + index, length));
                    LOG.error(
                            "{} Received SEG `{}`. SEG contains data [{},{}] and is within RCV.WND [{},{}]. Use data [{},{}]: {}.",
                            channel,
                            seg,
                            seg.seq(),
                            seg.lastSeq(),
                            tcb.rcvNxt(),
                            add(tcb.rcvNxt(), tcb.rcvWnd(), SEQ_NO_SPACE),
                            seq,
                            add(seq, length - 1, SEQ_NO_SPACE),
                            entry
                    );
                    head = entry;
                    tcb.decrementRcvWnd(length);
                    size += 1;
                    bytes += length;
                } else {
                    // SEG contains no elements within RCV.WND. Drop!
                    return;
                }
            } else {
                // buffer contains already segments. Check if SEG contains data that are before existing segments
                if (lessThan(seg.seq(), head.seq(), SEQ_NO_SPACE)) {
                    final long seq;
                    final int index;
                    final int length;

                    // SEG is located at the left edge of our RCV.WND?
                    if (lessThanOrEqualTo(seg.seq(), tcb.rcvNxt(), SEQ_NO_SPACE) && greaterThanOrEqualTo(seg.lastSeq(), tcb.rcvNxt(), SEQ_NO_SPACE)) {
                        // as SEG might start before RCV.NXT we should start reading RCV.NXT
                        seq = tcb.rcvNxt();
                        index = (int) sub(tcb.rcvNxt(), seg.seq(), SEQ_NO_SPACE);
                        // ensure that we do not exceed RCV.WND or read data already contained in head
                        final int offsetSegToHead = (int) sub(head.seq(), seg.seq(), SEQ_NO_SPACE);
                        length = Math.min((int) tcb.rcvWnd(), Math.min(offsetSegToHead, seg.len())) - index;
                        final ReceiveBufferEntry entry = new ReceiveBufferEntry(seq, content.retainedSlice(content.readerIndex() + index, length));
                        assert lessThan(entry.seq(), head.seq(), SEQ_NO_SPACE);
                        entry.next = head;
                        LOG.trace(
                                "{} Received SEG `{}`. SEG contains data [{},{}] and is located at left edge of RCV.WND [{},{}] and is located before current head fragment [{},{}]. Use data [{},{}]: {}.",
                                channel,
                                seg,
                                seg.seq(),
                                seg.lastSeq(),
                                tcb.rcvNxt(),
                                add(tcb.rcvNxt(), tcb.rcvWnd(), SEQ_NO_SPACE),
                                head.seq(),
                                head.lastSeq(),
                                add(seq, index, SEQ_NO_SPACE),
                                add(seq, add(index, length - 1, SEQ_NO_SPACE), SEQ_NO_SPACE),
                                entry
                        );
                        head = entry;
                        tcb.decrementRcvWnd(length);
                        size += 1;
                        bytes += length;
                    }
                    // SEG is within RCV.WND, but not at the left edge
                    else if (greaterThan(seg.seq(), tcb.rcvNxt(), SEQ_NO_SPACE) && lessThan(seg.seq(), add(tcb.rcvNxt(), tcb.rcvWnd(), SEQ_NO_SPACE), SEQ_NO_SPACE)) {
                        // start SEG as from the beginning
                        seq = seg.seq();
                        index = 0;
                        // ensure that we do not exceed RCV.WND or read data already contained in head
                        final long offsetRcvNxtToSeq = sub(seg.seq(), tcb.rcvNxt(), SEQ_NO_SPACE);
                        final int offsetSeqHead = (int) sub(head.seq(), seg.seq(), SEQ_NO_SPACE);
                        length = Math.min((int) (tcb.rcvWnd() - offsetRcvNxtToSeq), Math.min(offsetSeqHead, seg.len()));
                        final ReceiveBufferEntry entry = new ReceiveBufferEntry(seq, content.retainedSlice(content.readerIndex() + index, length));
                        assert lessThan(entry.seq(), head.seq(), SEQ_NO_SPACE);
                        entry.next = head;
                        LOG.error(
                                "{} Received SEG `{}`. SEG contains data [{},{}] and is within RCV.WND [{},{}] and is located before current head fragment [{},{}]. Use data [{},{}]: {}.",
                                channel,
                                seg,
                                seg.seq(),
                                seg.lastSeq(),
                                tcb.rcvNxt(),
                                add(tcb.rcvNxt(), tcb.rcvWnd(), SEQ_NO_SPACE),
                                head.seq(),
                                head.lastSeq(),
                                add(seq, index, SEQ_NO_SPACE),
                                add(seq, add(index, length - 1, SEQ_NO_SPACE), SEQ_NO_SPACE),
                                entry
                        );
                        head = entry;
                        tcb.decrementRcvWnd(length);
                        size += 1;
                        bytes += length;
                    } else {
                        // SEG contains no elements within RCV.WND. Drop!
                        return;
                    }
                }
            }

            // does SEG contain something we can add after the header (or other fragments)
            ReceiveBufferEntry current = head;
            while (current != null && tcb.rcvWnd() > 0) {
                // first, check if there is space between current and any next fragment
                if (current.next == null || lessThan(add(current.seq(), current.len(), SEQ_NO_SPACE), current.next.seq(), SEQ_NO_SPACE)) {
                    // second, does SEQ contain data that can be placed after current AND is SEG before any present next fragment?
                    if (lessThan(current.lastSeq(), seg.lastSeq(), SEQ_NO_SPACE) && (current.next == null || lessThan(seg.seq(), current.next.seq(), SEQ_NO_SPACE))) {
                        final long seq;
                        final int index;
                        final int length;
                        // überschneidung von SEG mit current?
                        if (lessThan(current.lastSeq(), seg.seq(), SEQ_NO_SPACE)) {
                            // keine Überschneidung
                            seq = seg.seq();
                            index = (int) sub(seq, seg.seq(), SEQ_NO_SPACE);
                            if (current.next != null) {
                                length = Math.min(seg.len(), (int) sub(current.next.seq(), seg.seq(), SEQ_NO_SPACE)) - index;
                            } else {
                                length = seg.len() - index;
                            }
                            final ReceiveBufferEntry entry = new ReceiveBufferEntry(seq, content.retainedSlice(content.readerIndex() + index, length));
//                            assert current.next == null || lessThan(entry.seq(), current.next.seq(), SEQ_NO_SPACE);
                            entry.next = current.next;
                            LOG.error(
                                    "{} 1 Received SEG `{}`. SEG contains data [{},{}] that can be placed between current fragment [{},{}] and next fragment [{},{}]. RCV.WND [{},{}]. Use data [{},{}]: {}.",
                                    channel,
                                    seg,
                                    seg.seq(),
                                    seg.lastSeq(),
                                    current.seq(),
                                    current.lastSeq(),
                                    current.next != null ? current.next.seq() : "null",
                                    current.next != null ? current.next.lastSeq() : "null",
                                    tcb.rcvNxt(),
                                    add(tcb.rcvNxt(), tcb.rcvWnd(), SEQ_NO_SPACE),
                                    seq,
                                    add(seq, length - 1, SEQ_NO_SPACE),
                                    entry
                            );
                            current.next = entry;
                            tcb.decrementRcvWnd(length);
                            size += 1;
                            bytes += length;
                        } else {
                            // Überschneidung
                            seq = add(current.lastSeq(), 1, SEQ_NO_SPACE);
                            index = (int) sub(seq, seg.seq(), SEQ_NO_SPACE);
                            if (current.next != null) {
                                length = Math.min(seg.len(), (int) sub(current.next.seq(), seg.seq(), SEQ_NO_SPACE)) - index;
                            } else {
                                length = seg.len() - index;
                            }
                            final ReceiveBufferEntry entry = new ReceiveBufferEntry(seq, content.retainedSlice(content.readerIndex() + index, length));
                            assert current.next == null || lessThan(entry.seq(), current.next.seq(), SEQ_NO_SPACE);
                            entry.next = current.next;
                            LOG.error(
                                    "{} 2 Received SEG `{}`. SEG contains data [{},{}] that can be placed between current fragment [{},{}] and next fragment [{},{}]. RCV.WND [{},{}]. Use data [{},{}]: {}.",
                                    channel,
                                    seg,
                                    seg.seq(),
                                    seg.lastSeq(),
                                    current.seq(),
                                    current.lastSeq(),
                                    current.next != null ? current.next.seq() : "null",
                                    current.next != null ? current.next.lastSeq() : "null",
                                    tcb.rcvNxt(),
                                    add(tcb.rcvNxt(), tcb.rcvWnd(), SEQ_NO_SPACE),
                                    seq,
                                    add(seq, length - 1, SEQ_NO_SPACE),
                                    entry
                            );
                            current.next = entry;
                            tcb.decrementRcvWnd(length);
                            size += 1;
                            bytes += length;
                        }
                    }
                }

                LOG.trace("Go to next fragment {}.", current.next);
                current = current.next;
            }

            // aggregieren?
            LOG.trace("head = {}; RCV.NXT = {}", head, tcb.rcvNxt());
            while (head != null && head.seq() == tcb.rcvNxt()) {
                // consume head
                LOG.trace(
                        "{} Head fragment `{}` is located at left edge of RCV.WND [{},{}]. Consume it, advance RCV.NXT by {}, and set head to {}.",
                        channel,
                        head,
                        tcb.rcvNxt(),
                        add(tcb.rcvNxt(), tcb.rcvWnd(), SEQ_NO_SPACE),
                        head.len(),
                        head.next
                );
                addToHeadBuf(ctx, head.buf());
                tcb.advanceRcvNxt(ctx, head.len());
                head = head.next;
                assert head == null || lessThanOrEqualTo(tcb.rcvNxt(), head.seq(), SEQ_NO_SPACE) : tcb.rcvNxt() + " must be less than or equal to " + head;
            }
        } else if (seg.len() > 0) {
            tcb.advanceRcvNxt(ctx, seg.len());
        }
    }

    private void addToHeadBuf(ChannelHandlerContext ctx, ByteBuf next) {
        if (headBuf == null) {
            // create new cumulation
            headBuf = next;
        } else if (headBuf instanceof CompositeByteBuf) {
            // add component
            CompositeByteBuf composite = (CompositeByteBuf) headBuf;
            composite.addComponent(true, next);
        } else {
            // create composite
            final CompositeByteBuf composite = ctx.alloc().compositeBuffer();
            composite.addComponent(true, headBuf);
            composite.addComponent(true, next);
            headBuf = composite;
        }
    }

    public void fireRead(final ChannelHandlerContext ctx, final TransmissionControlBlock tcb) {
        if (headBuf != null) {
            final int readableBytes = headBuf.readableBytes();
            if (readableBytes > 0) {
                bytes -= readableBytes;
                final ByteBuf headBuf1 = headBuf;
                headBuf = null;
                tcb.incrementRcvWnd(ctx);
                LOG.trace("{} Pass RCV.BUF ({} bytes) inbound to channel. {} bytes remain in RCV.WND. Increase RCV.WND to {} bytes.", ctx.channel(), readableBytes, bytes, tcb.rcvWnd());
                ctx.fireChannelRead(headBuf1);
            }
        }
    }

    public int readableBytes() {
        return headBuf != null ? headBuf.readableBytes() : 0;
    }

    static class ReceiveBufferEntry {
        private final long seq;
        private final ByteBuf buf;
        ReceiveBufferEntry next;

        public ReceiveBufferEntry(long seq, ByteBuf buf) {
            this.seq = seq;
            this.buf = requireNonNull(buf);
        }

        @Override
        public String toString() {
            return "ReceiveBufferEntry{" +
                    "seq=" + seq +
                    ", len=" + len() +
                    ", lastSeq=" + lastSeq() +
                    ", next=" + (next != null ? next.seq() : "null") +
                    '}';
        }

        public long seq() {
            return seq;
        }

        public ByteBuf buf() {
            return buf;
        }

        public int len() {
            return buf.readableBytes();
        }

        public long lastSeq() {
            return add(seq(), len() - 1L, SEQ_NO_SPACE);
        }
    }
}
