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
import io.netty.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.ArrayDeque;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.Segment.greaterThan;

/**
 * Holds all segments that has been written to the network (called in-flight) but have not been
 * acknowledged yet. This FIFO queue also updates the {@link io.netty.channel.Channel} writability
 * for the bytes it holds.
 * <p>
 * This queue mainly implements <a href="https://www.rfc-editor.org/rfc/rfc7323">RFC 7323 TCP
 * Extensions for High Performance</a>, <a href="https://www.rfc-editor.org/rfc/rfc7323#section-3">Section
 * 3 TCP Timestamps Option</a> and
 * <a href="https://www.rfc-editor.org/rfc/rfc7323#section-4">Section 4 The RTTM Mechanism</a>.
 */
public class RetransmissionQueue {
    private static final Logger LOG = LoggerFactory.getLogger(RetransmissionQueue.class);
    private final ArrayDeque<Segment> queue;
    private long firstSegmentSentTime;

    RetransmissionQueue(final ArrayDeque<Segment> queue) {
        this.queue = requireNonNull(queue);
    }

    RetransmissionQueue() {
        this(new ArrayDeque<>());
    }

    public void add(final ChannelHandlerContext ctx,
                    final Segment seg,
                    final TransmissionControlBlock tcb) {
        LOG.trace("{} Add SEG `{}` to RTNS.Q.", ctx.channel(), seg);
        ReferenceCountUtil.touch(seg, "RetransmissionQueue enqueue " + seg.toString());
        queue.add(seg.copy());

        // RFC 5482: The Transmission Control Protocol (TCP) specification [RFC0793] defines a
        // RFC 5482: local, per-connection "user timeout" parameter that specifies the maximum
        // RFC 5482: amount of time that transmitted data may remain unacknowledged before TCP
        // RFC 5482: will forcefully close the corresponding connection.

        // RFC 9293: If a timeout is specified, the current user timeout for this connection is
        // RFC 9293: changed to the new one.
        final ReliableConnectionHandler handler = (ReliableConnectionHandler) ctx.handler();
        if (tcb.config().userTimeout().toMillis() > 0) {
            handler.cancelUserTimer(ctx);
            handler.startUserTimer(ctx);
        }

        // RFC 6298: (5.1) Every time a packet containing data is sent (including a retransmission),
        // RFC 6298:       if the timer is not running, start it running so that it will expire
        // RFC 6298:       after RTO seconds (for the current value of RTO).
        if (handler.retransmissionTimer == null) {
            handler.startRetransmissionTimer(ctx, tcb);
        }

        if (firstSegmentSentTime == 0) {
            firstSegmentSentTime = tcb.config().clock().time();
        }
    }

    @Override
    public String toString() {
        return "RTNS.Q(SIZE=" + queue.size() + ")";
    }

    public void removeAcknowledged(final ChannelHandlerContext ctx,
                                   final TransmissionControlBlock tcb) {
        int ackedBytes = 0;
        boolean somethingWasAcked = false;
        Segment seg;
        while ((seg = queue.peek()) != null) {
            if (greaterThan(tcb.sndUna(), seg.lastSeq())) {
                // fully ACKed
                somethingWasAcked = true;
                ackedBytes += seg.content().readableBytes();
                seg.release();
                queue.remove();
            }
            else if (greaterThan(tcb.sndUna(), seg.seq())) {
                // partially ACKed
                System.out.println();
                somethingWasAcked = true;
                break;
            }
            else {
                break;
            }
        }

        if (somethingWasAcked) {
            final ReliableConnectionHandler handler = (ReliableConnectionHandler) ctx.handler();
            if (!tcb.sendBuffer().hasOutstandingData()) {
                handler.cancelUserTimer(ctx);
                // RFC 6298: (5.2) When all outstanding data has been acknowledged, turn off the
                // RFC 6298:       retransmission timer.
                handler.cancelRetransmissionTimer(ctx);
            }
            else {
                handler.cancelUserTimer(ctx);
                handler.startUserTimer(ctx);
                // RFC 6298: (5.3) When an ACK is received that acknowledges new data, restart the
                // RFC 6298:       retransmission timer so that it will expire after RTO seconds
                // RFC 6298:       (for the current value of RTO).
                handler.cancelRetransmissionTimer(ctx);
                handler.startRetransmissionTimer(ctx, tcb);
            }
        }
    }

    Segment retransmissionSegment(ChannelHandlerContext ctx,
                                  final TransmissionControlBlock tcb) {
        final Segment seg = queue.peek();
        if (seg != null) {
            final ReliableConnectionHandler handler = (ReliableConnectionHandler) ctx.handler();
            return handler.formSegment(ctx, seg.seq(), seg.ack(), seg.ctl(), seg.content().copy());
        }
        return null;
    }

    public void release() {
        Segment seg;
        while ((seg = queue.poll()) != null) {
            seg.release();
        }
    }

    public long firstSegmentSentTime() {
        return firstSegmentSentTime;
    }
}
