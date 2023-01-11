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
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.nio.channels.ClosedChannelException;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.SEQ_NO_SPACE;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.SerialNumberArithmetic.add;
import static org.drasyl.util.SerialNumberArithmetic.greaterThan;
import static org.drasyl.util.SerialNumberArithmetic.lessThan;
import static org.drasyl.util.SerialNumberArithmetic.lessThanOrEqualTo;
import static org.drasyl.util.SerialNumberArithmetic.sub;

// FIXME: add support for out-of-order?
public class ReceiveBuffer {
    private static final Logger LOG = LoggerFactory.getLogger(ReceiveBuffer.class);
    private static final ClosedChannelException DUMMY_CAUSE = new ClosedChannelException();
    private final Channel channel;
    private ByteBuf headBuf = null;
    private ReceiveBufferEntry head;
    private int bytes;

    ReceiveBuffer(final Channel channel, final ByteBuf headBuf, final ReceiveBufferEntry head,
                  final int bytes) {
        this.channel = requireNonNull(channel);
        this.headBuf = headBuf;
        this.head = head;
        this.bytes = requireNonNegative(bytes);
    }

    ReceiveBuffer(final Channel channel) {
        this(channel, null, null, 0);
    }

    /**
     * Release all buffers in the queue and complete all listeners and promises.
     */
    public void release() {
        if (headBuf != null) {
            headBuf.release();
        }

        while (head != null) {
            head.buf().release();
            head = head.next;
        }
    }

    /**
     * The number of readable bytes.
     */
    public int bytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return String.valueOf(bytes());
    }

    public void receive(final ChannelHandlerContext ctx,
                        final TransmissionControlBlock tcb,
                        final ConnectionHandshakeSegment seg) {
        final ByteBuf content = seg.content();
        if (content.isReadable()) {
            if (head == null) {
                // vorne am
                final long seq;
                final int index;
                final int length;
                if (lessThanOrEqualTo(seg.seq(), tcb.rcvNxt(), SEQ_NO_SPACE) && greaterThan(seg.lastSeq(), tcb.rcvNxt(), SEQ_NO_SPACE)) {
                    seq = tcb.rcvNxt();
                    // ok ab wo lesen wir?
                    index = (int) sub(tcb.rcvNxt(), seg.seq(), SEQ_NO_SPACE);
                    // wie viel lesen wir?
                    length = Math.min((int) tcb.rcvWnd(), seg.len()) - index;
                }
                else if (greaterThan(seg.seq(), tcb.rcvNxt(), SEQ_NO_SPACE) && lessThan(seg.seq(), add(tcb.rcvNxt(), tcb.rcvWnd(), SEQ_NO_SPACE), SEQ_NO_SPACE)) {
                    seq = seg.seq();
                    // ok ab wo lesen wir?
                    index = 0;
                    // wie viel lesen wir?
                    length = Math.min((int) (tcb.rcvWnd() - sub(seg.seq(), tcb.rcvNxt(), SEQ_NO_SPACE)), seg.len());
                }
                else {
                    // drop!
                    return;
                }

                final ReceiveBufferEntry entry = new ReceiveBufferEntry(seq, seg.content().slice(index, length));
                entry.next = head;
                head = entry;

                tcb.decrementRcvWnd(length);
                bytes += length;
            }
            else {
                // something to add before the head?
                final long seq;
                final int index;
                final int length;
                if (lessThan(seg.seq(), head.seq(), SEQ_NO_SPACE)) {
                    if (lessThanOrEqualTo(seg.seq(), tcb.rcvNxt(), SEQ_NO_SPACE) && greaterThan(seg.lastSeq(), tcb.rcvNxt(), SEQ_NO_SPACE)) {
                        seq = tcb.rcvNxt();
                        // ok ab wo lesen wir?
                        index = (int) sub(tcb.rcvNxt(), seg.seq(), SEQ_NO_SPACE);
                        // wie viel lesen wir?
                        length = Math.min((int) tcb.rcvWnd(), Math.min((int) sub(head.seq(), seg.seq(), SEQ_NO_SPACE), seg.len())) - index;
                    }
                    else if (greaterThan(seg.seq(), tcb.rcvNxt(), SEQ_NO_SPACE) && lessThan(seg.seq(), add(tcb.rcvNxt(), tcb.rcvWnd(), SEQ_NO_SPACE), SEQ_NO_SPACE)) {
                        seq = seg.seq();
                        // ok ab wo lesen wir?
                        index = 0;
                        // wie viel lesen wir?
                        length = Math.min((int) (tcb.rcvWnd() - sub(seg.seq(), tcb.rcvNxt(), SEQ_NO_SPACE)), Math.min((int) sub(head.seq(), seg.seq(), SEQ_NO_SPACE), seg.len()));
                    }
                    else {
                        // drop!
                        return;
                    }

                    final ReceiveBufferEntry entry = new ReceiveBufferEntry(seq, seg.content().slice(index, length));
                    entry.next = head;
                    head = entry;

                    tcb.decrementRcvWnd(length);
                    bytes += length;
                }
            }

            ReceiveBufferEntry current = head;
            while (current != null) {
                // gibt es in SEQ etwas, was HINTER current gepackt werden kann?
                // something to add after?
                if (lessThanOrEqualTo(seg.seq(), current.lastSeq(), SEQ_NO_SPACE) && greaterThan(seg.lastSeq(), current.lastSeq(), SEQ_NO_SPACE) && (current.next == null || lessThan(add(current.lastSeq(), 1, SEQ_NO_SPACE), current.next.seq(), SEQ_NO_SPACE))) {
                    final long seq = add(current.seq(), current.len(), SEQ_NO_SPACE);
                    // ok ab wo lesen wir? // 120
                    final int index = (int) sub(add(current.seq(), current.len(), SEQ_NO_SPACE), seg.seq(), SEQ_NO_SPACE);
                    // wie viel lesen wir? // 80
                    final int length = seg.len() - index;

                    final ReceiveBufferEntry entry = new ReceiveBufferEntry(seq, seg.content().slice(index, length));
                    entry.next = current.next;
                    current.next = entry;

                    tcb.decrementRcvWnd(length);
                    bytes += length;

                    break;
                }

                current = current.next;
            }

            // aggregieren?
            while (head != null && head.seq() == tcb.rcvNxt()) {
                // consume head
                addToHeadBuf(ctx, head.buf());
                tcb.advanceRcvNxt(head.len());
                head = head.next;
            }
        }
        else if (seg.len() > 0) {
            tcb.advanceRcvNxt(seg.len());
        }
    }

    private void addToHeadBuf(ChannelHandlerContext ctx, ByteBuf next) {
        if (headBuf == null) {
            // create new cumulation
            headBuf = next;
        }
        else if (headBuf instanceof CompositeByteBuf) {
            // add component
            CompositeByteBuf composite = (CompositeByteBuf) headBuf;
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

    public void fireRead(final ChannelHandlerContext ctx, final TransmissionControlBlock tcb) {
        if (headBuf != null) {
            tcb.incrementRcvWnd(headBuf.readableBytes());
            bytes -= headBuf.readableBytes();
            LOG.trace("{} Pass RCV.BUF ({} bytes) inbound to channel. Increase RCV.WND to {} bytes (+{})", ctx.channel(), bytes, tcb.rcvWnd(), bytes);
            ctx.fireChannelRead(headBuf);
            headBuf = null;
        }
    }

    public int readableBytes() {
        return headBuf != null ? headBuf.readableBytes() : 0;
    }

    static class ReceiveBufferEntry {
        private final long seq;
        private final ByteBuf buf;
        private ReceiveBufferEntry next;

        public ReceiveBufferEntry(long seq, ByteBuf buf) {
            this.seq = seq;
            this.buf = requireNonNull(buf);
        }

        public ReceiveBufferEntry(ConnectionHandshakeSegment seg, ByteBuf buf) {
            this(seg.seq(), buf);
        }

        @Override
        public String toString() {
            return "ReceiveBufferEntry{" +
                    "seq=" + seq +
                    ", len=" + len() +
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
