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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.nio.channels.ClosedChannelException;

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
    private final ConnectionHandshakeSegment seg;
    private final ChannelPromise ackPromise;
    private final long rto;

    RetransmissionApplier(final ChannelHandlerContext ctx,
                          final ConnectionHandshakeSegment seg,
                          final ChannelPromise ackPromise) {
        this(ctx, seg, ackPromise, /*((ConnectionHandshakeHandler) ctx.handler()).tcb.rto()*/1000);
    }

    RetransmissionApplier(final ChannelHandlerContext ctx,
                          final ConnectionHandshakeSegment seg,
                          final ChannelPromise ackPromise,
                          final long rto) {
        this.ctx = requireNonNull(ctx);
        this.seg = requireNonNull(seg);
        this.ackPromise = requireNonNull(ackPromise);
        this.rto = rto;
//        this.rto = requirePositive(rto);
    }

    @SuppressWarnings({ "unchecked", "java:S2164" })
    @Override
    public void operationComplete(final ChannelFuture future) {
        if (future.isSuccess()) {
            // segment has ben successfully been written to the network
            // schedule retransmission if SEG does not get ACKed in time
            ScheduledFuture<?> retransmissionFuture = future.channel().eventLoop().schedule(() -> {
                // retransmission timeout occurred
                // check if we're not CLOSED and if SEG has not been ACKed
                if (future.channel().isOpen() && ((ConnectionHandshakeHandler) ctx.handler()).state != CLOSED && !ackPromise.isDone() && lessThanOrEqualTo(((ConnectionHandshakeHandler) ctx.handler()).tcb.sndUna(), seg.seq(), SEQ_NO_SPACE)) {
                    // not ACKed, send egain
                    LOG.error("{} Segment `{}` has not been acknowledged within {}ms. Send again.", future.channel(), seg, rto);
                    ctx.writeAndFlush(seg.copy()).addListener(new RetransmissionApplier(ctx, seg, ackPromise, rto * 2));
                    // FIXME: vermeide das jedes SEG einzeln erneut neu geschrieben und geflusht wird?
                }
            }, rto, MILLISECONDS);

            // cancel retransmission job if SEG got ACKed
            ackPromise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) {
                    retransmissionFuture.cancel(false);
                }
            });
        }
        else if (!(future.cause() instanceof ClosedChannelException)) {
            LOG.trace("{} Unable to send `{}`:", future::channel, () -> seg, future::cause);
            future.channel().close();
        }
    }
}
