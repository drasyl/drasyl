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

    ReceiveBuffer(final Channel channel, final ByteBuf headBuf, final ReceiveBufferEntry head) {
        this.channel = requireNonNull(channel);
        this.headBuf = headBuf;
        this.head = head;
    }

    ReceiveBuffer(final Channel channel) {
        this(channel, null, null);
    }

    /**
     * Release all buffers in the queue and complete all listeners and promises.
     */
    public void release() {
        if (headBuf != null) {
            headBuf.release();
        }

        while (head != null) {
            head.seg.release();
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
            // does SEG contain data for the left edge of our window?
            if (lessThanOrEqualTo(seg.seq(), tcb.rcvNxt(), SEQ_NO_SPACE) && greaterThan(seg.lastSeq(), tcb.rcvNxt(), SEQ_NO_SPACE)) {
                // ok ab wo lesen wir?
                final int index = (int) sub(tcb.rcvNxt(), seg.seq(), SEQ_NO_SPACE);
                // bis wohin lesen wir?
                final int l = head != null ? (int) sub(head.seq(), seg.seq(), SEQ_NO_SPACE) : seg.content().readableBytes();
                final int length = Math.min((int) tcb.rcvWnd(), l) - index;
                final ByteBuf next = content.slice(index, length);

                tcb.decrementRcvWnd(length);
                tcb.advanceRcvNxt(length);
                bytes += length;
                LOG.trace("{} Got SEG `{}`. Add {} bytes to RCV.BUF. Advance RCV.NXT to {}. Reduce RCV.WND to {}.", ctx.channel(), seg, length, tcb.rcvWnd(), tcb.rcvWnd());
                addToHeadBuf(ctx, next);

                while (head != null && head.seq() == tcb.rcvNxt()) {
                    bytes += head.len();
                    tcb.advanceRcvNxt(head.len());
                    addToHeadBuf(ctx, head.buf());
                    head = head.next;
                }
            }
            // does SEG contain data for our window?
            else if (greaterThan(seg.seq(), tcb.rcvNxt(), SEQ_NO_SPACE) && lessThan(seg.seq(), add(tcb.rcvNxt(), tcb.rcvWnd(), SEQ_NO_SPACE), SEQ_NO_SPACE)) {
                // add to fragments backlog
                if (head == null) {
                    final int index = 0;
                    final int length = Math.min((int) tcb.rcvWnd(), seg.content().readableBytes()) - index;
                    final ByteBuf next = content.slice(index, length);

                    tcb.decrementRcvWnd(length);
                    bytes += length;
                    LOG.trace("{} Got SEG `{}`. Add {} bytes to RCV.BUF. Advance RCV.NXT to {}. Reduce RCV.WND to {}.", ctx.channel(), seg, length, tcb.rcvWnd(), tcb.rcvWnd());
                    head = new ReceiveBufferEntry(seg, next);
                }
                else {
                    ReceiveBufferEntry current = head;
                    while (current != null) {
                        // add before?
                        if (lessThan(seg.seq(), current.seq(), SEQ_NO_SPACE)) { // FIXME: überschneidungen
                            final int index = 0;
                            final int l = (int) sub(current.seq(), seg.seq(), SEQ_NO_SPACE);
                            final int length = Math.min((int) tcb.rcvWnd(), l) - index;
                            final ByteBuf next = content.slice(index, length);

                            final ReceiveBufferEntry x = new ReceiveBufferEntry(seg, next);
                            x.next = head;
                            head = x;

                            tcb.decrementRcvWnd(length);
                            bytes += length;
                            LOG.trace("{} Added SEG `{}` to RCV.BUF ({} bytes). Reduce RCV.WND to {} bytes (-{}).", ctx.channel(), seg, bytes(), tcb.rcvWnd(), length);
                            break;
                        }

                        current = head.next;
                    }
                }
            }
            else {
                // not expected
            }
//
//            final int bytesToReceive = (int) Math.min(tcb.rcvWnd(), seg.len());
//            final ByteBuf next = content.slice(content.readerIndex(), bytesToReceive);
//
//            // left edge?
//            if (seg.seq() == tcb.rcvNxt()) {
//                tcb.decrementRcvWnd(bytesToReceive);
//                tcb.advanceRcvNxt(bytesToReceive);
//                bytes += bytesToReceive;
//                LOG.trace("{} Added SEG `{}` to RCV.BUF ({} bytes). Reduce RCV.WND to {} bytes (-{}).", ctx.channel(), seg, bytes(), tcb.rcvWnd(), bytesToReceive);
//
//                // advance RCV.NXT
//
//                compose(ctx, next);
//
//                // FIXME: was wenn im park der vordere teil bereits existiert?
//                // guck in unseren park
//                while (head != null && head.seg.seq() == tcb.rcvNxt()) {
//                    tcb.advanceRcvNxt(bytesToReceive);
//                    compose(ctx, next);
//
//                    head = head.next;
//                }
//            }
//            else {
//                // irgendwo anders parken yumad
//                LOG.warn("{} Got out-of-order SEG `{}`. Expected SEQ `{}` but got SEQ `{}`. Add to buffer.", ctx.channel(), seg, seg.seq(), tcb.rcvNxt());
//                final ReceiveBufferEntry receive = new ReceiveBufferEntry(seg);
//                ReceiveBufferEntry currentHead = head;
//                if (currentHead == null) {
//                    // erstes element
//                    head = receive;
//
//                    tcb.decrementRcvWnd(bytesToReceive);
//                    bytes += bytesToReceive;
//                    LOG.trace("{} Added SEG `{}` to RCV.BUF ({} bytes). Reduce RCV.WND to {} bytes (-{}).", ctx.channel(), seg, bytes(), tcb.rcvWnd(), bytesToReceive);
//                }
//                else {
//                    // n tes element. packe an die richtige stelle
//                    // FIXME: add support für sich überlappende SEGs
//                    // FIXME: add support for duplicate SEGs
//                    ReceiveBufferEntry current = currentHead;
//                    while (current != null) {
//                        // duplicate?
//                        if (seg.seq() == current.seg.seq()) {
//                            LOG.trace("{} Got duplicate SEG `{}`. Ignore.", ctx.channel(),seg);
//                            break;
//                        }
//                        // add before?
//                        if (lessThan(seg.seq(), current.seg.seq(), SEQ_NO_SPACE)) {
//                            final ReceiveBufferEntry x = new ReceiveBufferEntry(seg);
//                            x.next = head;
//                            head = x;
//
//                            tcb.decrementRcvWnd(bytesToReceive);
//                            bytes += bytesToReceive;
//                            LOG.trace("{} Added SEG `{}` to RCV.BUF ({} bytes). Reduce RCV.WND to {} bytes (-{}).", ctx.channel(), seg, bytes(), tcb.rcvWnd(), bytesToReceive);
//                            break;
//                        }
//                        // add after?
//                        else if (current.next == null || lessThan(seg.seq(), current.next.seg.seq(), SEQ_NO_SPACE)) {
//                            final ReceiveBufferEntry x = new ReceiveBufferEntry(seg);
//                            x.next = current.next;
//                            current.next = x;
//
//                            tcb.decrementRcvWnd(bytesToReceive);
//                            bytes += bytesToReceive;
//                            LOG.trace("{} Added SEG `{}` to RCV.BUF ({} bytes). Reduce RCV.WND to {} bytes (-{}).", ctx.channel(), seg, bytes(), tcb.rcvWnd(), bytesToReceive);
//                            break;
//                        }
//
//                        current = current.next;
//                    }
//                }
//            }
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
        private final ConnectionHandshakeSegment seg;
        private final ByteBuf buf;
        private ReceiveBufferEntry next;

        public ReceiveBufferEntry(ConnectionHandshakeSegment seg, ByteBuf buf) {
            this.seg = requireNonNull(seg);
            this.buf = requireNonNull(buf);
        }

        @Override
        public String toString() {
            return "ReceiveBufferEntry{" +
                    "seg=" + seg +
                    '}';
        }

        public long seq() {
            return seg.seq();
        }

        public ByteBuf buf() {
            return buf;
        }

        public int len() {
            return buf.readableBytes();
        }
    }
}
