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
 * Represents the retransmission queue that holds segments that need to be retransmitted due to
 * timeout or loss in a connection.
 * <p>
 * The retransmission queue is used by the sender to keep track of segments that have been sent but
 * not yet acknowledged, and need to be retransmitted after a certain period of time due to timeout
 * or loss in the network.
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
        final ConnectionHandler handler = (ConnectionHandler) ctx.handler();
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

    @SuppressWarnings("java:S135")
    public boolean removeAcknowledged(final ChannelHandlerContext ctx,
                                      final TransmissionControlBlock tcb) {
        boolean somethingWasAcked = false;
        Segment seg;
        while ((seg = queue.peek()) != null) {
            if (greaterThan(tcb.sndUna(), seg.lastSeq())) {
                // fully ACKed
                somethingWasAcked = true;
                seg.release();
                queue.remove();
            }
            else if (greaterThan(tcb.sndUna(), seg.seq())) {
                // partially ACKed
                // TODO: remove ACKed part from segment?
                somethingWasAcked = true;
                break;
            }
            else {
                // nothing ACKed
                break;
            }
        }

        return somethingWasAcked;
    }

    Segment nextSegment() {
        return queue.peek();
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
