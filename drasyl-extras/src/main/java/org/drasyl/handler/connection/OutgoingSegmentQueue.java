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
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.ArrayDeque;

import static java.util.Objects.requireNonNull;

/**
 * This queue holds all control bits and data to be sent.
 */
public class OutgoingSegmentQueue {
    private static final Logger LOG = LoggerFactory.getLogger(OutgoingSegmentQueue.class);
    private final ArrayDeque<Segment> queue;

    OutgoingSegmentQueue(final ArrayDeque<Segment> queue) {
        this.queue = requireNonNull(queue);
    }

    OutgoingSegmentQueue() {
        this(new ArrayDeque<>());
    }

    void place(final ChannelHandlerContext ctx, final Segment seg) {
        LOG.trace("{} Place SEG `{}` on the outgoing segment queue.", ctx.channel(), seg);
        queue.add(seg);
    }

    public void flush(final ChannelHandlerContext ctx,
                      final TransmissionControlBlock tcb) {
        LOG.trace("{} Flush outgoing segment queue ({} elements).", ctx.channel(), queue.size());
        final boolean doFlush = !queue.isEmpty();
        Segment seg;
        while ((seg = queue.poll()) != null) {
            LOG.trace("{} Write SEG `{}` to network.", ctx.channel(), seg);

            if (seg.mustBeAcked()) {
                // ACKnowledgement necessary. Add SEG to retransmission queue
                tcb.retransmissionQueue().add(ctx, seg, tcb);
            }

            // write SEQ to network
            ctx.write(seg);
        }

        if (doFlush) {
            ctx.flush();
        }
    }

    public int size() {
        return queue.size();
    }

    @Override
    public String toString() {
        return String.valueOf(size());
    }
}
