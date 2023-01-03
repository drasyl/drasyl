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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.SEQ_NO_SPACE;
import static org.drasyl.handler.connection.State.CLOSED;
import static org.drasyl.util.SerialNumberArithmetic.lessThanOrEqualTo;

/**
 * A {@link ChannelFutureListener} that retransmit not acknowledged segments.
 */
class RetransmissionApplier implements ChannelFutureListener {
    static final Logger LOG = LoggerFactory.getLogger(RetransmissionApplier.class);
    private final ChannelHandlerContext ctx;
    private final TransmissionControlBlock tcb;
    private final ConnectionHandshakeSegment seg;
    private final ChannelPromise promise;

    RetransmissionApplier(final ChannelHandlerContext ctx,
                          final TransmissionControlBlock tcb,
                          final ConnectionHandshakeSegment seg,
                          final ChannelPromise promise) {
        this.ctx = requireNonNull(ctx);
        this.tcb = requireNonNull(tcb);
        this.seg = requireNonNull(seg);
        this.promise = requireNonNull(promise);
    }

    @SuppressWarnings({ "unchecked", "java:S2164" })
    @Override
    public void operationComplete(final ChannelFuture future) {
        // segment has been successfully been written to the network
        // schedule retransmission if SEG does not get ACKed in time
        final Channel channel = future.channel();
        final long rto = tcb.rto();
        final ScheduledFuture<?> retransmissionFuture = channel.eventLoop().schedule(() -> {
            // retransmission timeout occurred
            // check if we're not CLOSED and if SEG has not been ACKed
            final ConnectionHandshakeHandler handler = (ConnectionHandshakeHandler) ctx.handler();
            if (handler.state != CLOSED && !promise.isDone() && lessThanOrEqualTo(handler.tcb.sndUna(), seg.seq(), SEQ_NO_SPACE)) {
                // not ACKed, send again
                LOG.error("{} Segment `{}` has not been acknowledged within {}ms. Send again.", channel, seg, rto);
                ctx.writeAndFlush(seg.copy()).addListener(new RetransmissionApplier(ctx, tcb, seg, promise));
            }
        }, rto, MILLISECONDS);

        // cancel retransmission job if SEG got ACKed
        promise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) {
                retransmissionFuture.cancel(false);
            }
        });
    }
}
