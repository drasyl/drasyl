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

import java.nio.channels.ClosedChannelException;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.connection.State.CLOSED;
import static org.drasyl.util.Preconditions.requirePositive;
import static org.drasyl.util.SerialNumberArithmetic.lessThanOrEqualTo;

/**
 * A {@link ChannelFutureListener} that retransmit not acknowledged segments.
 */
class RetransmissionTimeoutApplier implements ChannelFutureListener {
    static final long LOWER_BOUND = 1_000; // lower bound for retransmission (e.g., 1 second)
    static final long UPPER_BOUND = 60_000; // upper bound for retransmission (e.g., 1 minute)
    // as we're currently not aware of the actual RTT, we use this fixed value
    // TS Value (TSval)  |TS Echo Reply (TSecr)
    // https://www.rfc-editor.org/rfc/rfc1323
    static final float ALPHA = .8F; // smoothing factor (e.g., .8 to .9)
    static final float BETA = 1.3F; // delay variance factor (e.g., 1.3 to 2.0)
    private final ChannelHandlerContext ctx;
    private final ConnectionHandshakeSegment seg;
    private final ChannelPromise ackPromise;
    private final long rto;

    RetransmissionTimeoutApplier(final ChannelHandlerContext ctx,
                                 final ConnectionHandshakeSegment seg,
                                 final ChannelPromise ackPromise) {
        this(ctx, seg, ackPromise, ((ConnectionHandshakeHandler) ctx.handler()).rto());
    }

    RetransmissionTimeoutApplier(final ChannelHandlerContext ctx,
                                 final ConnectionHandshakeSegment seg,
                                 final ChannelPromise ackPromise,
                                 final long rto) {
        this.ctx = requireNonNull(ctx);
        this.seg = requireNonNull(seg);
        this.ackPromise = requireNonNull(ackPromise);
        this.rto = requirePositive(rto);
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
                if (future.channel().isOpen() && ((ConnectionHandshakeHandler) ctx.handler()).state != CLOSED && !ackPromise.isDone() && lessThanOrEqualTo(((ConnectionHandshakeHandler) ctx.handler()).tcb().sndUna, seg.seq(), ConnectionHandshakeSegment.SEQ_NO_SPACE)) {
                    // not ACKed, send egain
                    ConnectionHandshakeHandler.LOG.error("{} Segment `{}` has not been acknowledged within {}ms. Send again.", future.channel(), seg, rto);
                    ctx.writeAndFlush(seg.copy()).addListener(new RetransmissionTimeoutApplier(ctx, seg, ackPromise, rto * 2));
                    // FIXME: vermeide das jedes SEG einzeln erneut neu geschrieben und geflusht wird?
                }
            }, rto, MILLISECONDS);

            // cancel retransmission job if SEG got ACKed
            ackPromise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    retransmissionFuture.cancel(false);
                }
            });
        }
        else if (!(future.cause() instanceof ClosedChannelException)) {
            ConnectionHandshakeHandler.LOG.trace("{} Unable to send `{}`:", future::channel, () -> seg, future::cause);
            future.channel().close();
        }
    }
}
