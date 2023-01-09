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
import io.netty.channel.CoalescingBufferQueue;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.nio.channels.ClosedChannelException;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.SEQ_NO_SPACE;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.advanceSeq;
import static org.drasyl.util.SerialNumberArithmetic.lessThan;

// FIXME: add support for out-of-order?
public class ReceiveBuffer {
    private static final Logger LOG = LoggerFactory.getLogger(ReceiveBuffer.class);
    private static final ClosedChannelException DUMMY_CAUSE = new ClosedChannelException();
    private final Channel channel;
    // head and tail pointers for the linked-list structure. If empty head and tail are null.
    private ReceiveBufferEntry head;
    private int bytes;
    private ByteBuf cumulation = null;

    ReceiveBuffer(final Channel channel,
                  final CoalescingBufferQueue queue) {
        this.channel = requireNonNull(channel);
    }

    ReceiveBuffer(final Channel channel) {
        this(channel, new CoalescingBufferQueue(channel, 4, false));
    }

    /**
     * Release all buffers in the queue and complete all listeners and promises.
     */
    public void release() {
        if (cumulation != null) {
            cumulation.release();
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
                        final ConnectionHandshakeSegment seg,
                        final TransmissionControlBlock tcb) {
        final ByteBuf content = seg.content();
        if (content.isReadable()) {
            final int bytesToReceive = (int) Math.min(tcb.rcvWnd, seg.len());
            final ByteBuf next = content.slice(content.readerIndex(), bytesToReceive);

            // left edge?
            if (seg.seq() == tcb.rcvNxt) {
                tcb.rcvWnd -= bytesToReceive;
                bytes += bytesToReceive;
                LOG.trace("{} Added SEG `{}` to RCV.BUF ({} bytes). Reduce RCV.WND to {} bytes (-{}).", ctx.channel(), seg, bytes(), tcb.rcvWnd(), bytesToReceive);

                // advance RCV.NXT
                tcb.rcvNxt = advanceSeq(tcb.rcvNxt(), bytesToReceive);

                compose(ctx, next);

                // guck in unseren park
                while (head != null && head.seg.seq() == tcb.rcvNxt) {
                    tcb.rcvNxt = advanceSeq(tcb.rcvNxt(), bytesToReceive);
                    compose(ctx, next);

                    head = head.next;
                }
            }
            else {
                // irgendwo anders parken yumad
                LOG.warn("{} Got out-of-order SEG `{}`. Expected SEQ {} but got SEQ {}. Add to buffer.", ctx.channel(), seg, seg.seq(), tcb.rcvNxt);
                final ReceiveBufferEntry receive = new ReceiveBufferEntry(seg);
                ReceiveBufferEntry currentHead = head;
                if (currentHead == null) {
                    // erstes element
                    head = receive;

                    tcb.rcvWnd -= bytesToReceive;
                    bytes += bytesToReceive;
                    LOG.trace("{} Added SEG `{}` to RCV.BUF ({} bytes). Reduce RCV.WND to {} bytes (-{}).", ctx.channel(), seg, bytes(), tcb.rcvWnd(), bytesToReceive);
                }
                else {
                    // n tes element. packe an die richtige stelle
                    // FIXME: add support für sich überlappende SEGs
                    // FIXME: add support for duplicate SEGs
                    ReceiveBufferEntry current = currentHead;
                    while (current != null) {
                        // duplicate?
                        if (seg.seq() == current.seg.seq()) {
                            LOG.trace("{} Got duplicate SEG `{}`. Ignore.", ctx.channel(),seg);
                            break;
                        }
                        // add before?
                        if (lessThan(seg.seq(), current.seg.seq(), SEQ_NO_SPACE)) {
                            final ReceiveBufferEntry x = new ReceiveBufferEntry(seg);
                            x.next = head;
                            head = x;

                            tcb.rcvWnd -= bytesToReceive;
                            bytes += bytesToReceive;
                            LOG.trace("{} Added SEG `{}` to RCV.BUF ({} bytes). Reduce RCV.WND to {} bytes (-{}).", ctx.channel(), seg, bytes(), tcb.rcvWnd(), bytesToReceive);
                            break;
                        }
                        // add after?
                        else if (current.next == null || lessThan(seg.seq(), current.next.seg.seq(), SEQ_NO_SPACE)) {
                            final ReceiveBufferEntry x = new ReceiveBufferEntry(seg);
                            x.next = current.next;
                            current.next = x;

                            tcb.rcvWnd -= bytesToReceive;
                            bytes += bytesToReceive;
                            LOG.trace("{} Added SEG `{}` to RCV.BUF ({} bytes). Reduce RCV.WND to {} bytes (-{}).", ctx.channel(), seg, bytes(), tcb.rcvWnd(), bytesToReceive);
                            break;
                        }

                        current = current.next;
                    }
                }
            }
        }
        else if (seg.len() > 0) {
            tcb.rcvNxt = advanceSeq(tcb.rcvNxt(), seg.len());
        }
    }

    private void compose(ChannelHandlerContext ctx, ByteBuf next) {
        if (cumulation == null) {
            // create new cumulation
            cumulation = next;
        }
        else if (cumulation instanceof CompositeByteBuf) {
            // add component
            CompositeByteBuf composite = (CompositeByteBuf) cumulation;
            composite.addComponent(true, next);
        }
        else {
            // create composite
            final CompositeByteBuf composite = ctx.alloc().compositeBuffer();
            composite.addComponent(true, cumulation);
            composite.addComponent(true, next);
            cumulation = composite;
        }
    }

    public void fireRead(final ChannelHandlerContext ctx, final TransmissionControlBlock tcb) {
        if (cumulation != null) {
            tcb.rcvWnd += cumulation.readableBytes();
            bytes -= cumulation.readableBytes();
            LOG.trace("{} Pass RCV.BUF ({} bytes) inbound to channel. Increase RCV.WND to {} bytes (+{})", ctx.channel(), bytes, tcb.rcvWnd(), bytes);
            ctx.fireChannelRead(cumulation);
            cumulation = null;
        }
    }

    private static class ReceiveBufferEntry {
        private final ConnectionHandshakeSegment seg;
        private ReceiveBufferEntry next;

        public ReceiveBufferEntry(final ConnectionHandshakeSegment seg) {
            this.seg = requireNonNull(seg);
        }

        @Override
        public String toString() {
            return "ReceiveBufferEntry{" +
                    "seg=" + seg +
                    '}';
        }
    }
}
