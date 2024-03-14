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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.PromiseNotifier;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.ArrayDeque;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.Segment.greaterThanOrEqualTo;

/**
 * Represents the outgoing segment queue that holds segments to be sent over a connection.
 * <p>
 * This class manages the queue of segments that are waiting to be sent over a connection. The queue
 * implements a first-in, first-out (FIFO) ordering, where segments are added to the end of the
 * queue and retrieved from the front of the queue. The queue is used by the sender to hold segments
 * that are waiting to be sent to the receiver. Segments are added to the queue as they are
 * generated by the application, and they are sent out by the sender according to the congestion
 * control algorithm and the available network resources.
 */
public class OutgoingSegmentQueue {
    private static final Logger LOG = LoggerFactory.getLogger(OutgoingSegmentQueue.class);
    private final ArrayDeque<Object> queue;

    OutgoingSegmentQueue(final ArrayDeque<Object> queue) {
        this.queue = requireNonNull(queue);
    }

    OutgoingSegmentQueue() {
        this(new ArrayDeque<>());
    }

    void add(final ChannelHandlerContext ctx, final Segment seg, final ChannelPromise promise) {
        LOG.trace("{} Add SEG `{}` to the outgoing segment queue.", ctx.channel(), seg);

        // check if we can replace head
        if (seg.isAck() || seg.isPsh()) {
            final Segment head = (Segment) queue.peek();
            if (head != null && head.isOnlyAck() && head.seq() == seg.seq() && greaterThanOrEqualTo(seg.ack(), head.ack()) && head.len() == 0) {
                    LOG.trace("{} Replace SEG `{}` in queue with SEG `{}`.", ctx.channel(), head, seg);
                    ((Segment) queue.remove()).release();
                    promise.addListener(new PromiseNotifier<>((ChannelPromise) queue.remove()));

            }
        }

        queue.add(seg);
        queue.add(promise);
    }

    void add(final ChannelHandlerContext ctx, final Segment seg) {
        add(ctx, seg, ctx.newPromise());
    }

    public void flush(final ChannelHandlerContext ctx, final TransmissionControlBlock tcb) {
        LOG.trace("{} Flush outgoing segment queue ({} elements).", ctx.channel(), size());
        final boolean doFlush = !queue.isEmpty();
        Segment seg;
        while ((seg = (Segment) queue.poll()) != null) {
            if (seg.wnd() != tcb.rcvWnd()) {
                // ensure SEG.WND is up-to-date (in processing of arrivals, we first queue SEGs to be sent, then read from the RCV.BUF, before flushing enqueued SEGs)
                seg = new Segment(seg.srcPort(), seg.dstPort(), seg.seq(), seg.ack(), seg.ctl(), tcb.rcvWnd(), seg.cks(), seg.options(), seg.content());
            }

            LOG.trace("{} Write SEG `{}` to network.", ctx.channel(), seg);

            if (seg.mustBeAcked()) {
                // ACKnowledgement necessary. Add SEG to retransmission queue
                tcb.retransmissionQueue().add(ctx, seg, tcb);
            }

            // write SEQ to network
            ctx.write(seg, (ChannelPromise) queue.poll());
        }

        if (doFlush) {
            LOG.trace("{} Flush channel after at least one SEG has been written to network.", ctx.channel());
            ctx.flush();
        }
    }

    /**
     * Returns the number of elements in this deque.
     *
     * @return the number of elements in this deque
     */
    public int size() {
        return queue.size() / 2;
    }

    /**
     * Returns {@code true} if this deque contains no elements.
     *
     * @return {@code true} if this deque contains no elements
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public String toString() {
        return String.valueOf(size());
    }
}
