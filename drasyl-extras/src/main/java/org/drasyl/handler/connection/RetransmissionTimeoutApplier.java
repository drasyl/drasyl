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
    private final ConnectionHandshakeHandler handler;
    private final long rto;

    RetransmissionTimeoutApplier(final ChannelHandlerContext ctx,
                                 final ConnectionHandshakeSegment seg,
                                 final ChannelPromise ackPromise,
                                 final ConnectionHandshakeHandler handler) {
        this(ctx, seg, ackPromise, handler, handler.rto());
    }

    RetransmissionTimeoutApplier(final ChannelHandlerContext ctx,
                                 final ConnectionHandshakeSegment seg,
                                 final ChannelPromise ackPromise,
                                 final ConnectionHandshakeHandler handler,
                                 final long rto) {
        this.ctx = requireNonNull(ctx);
        this.seg = requireNonNull(seg);
        this.ackPromise = requireNonNull(ackPromise);
        this.handler = requireNonNull(handler);
        this.rto = requirePositive(rto);
    }

    @SuppressWarnings({ "unchecked", "java:S2164" })
    @Override
    public void operationComplete(final ChannelFuture future) {
        if (future.isSuccess()) {
            // segment has ben successfully been written to the network
            // schedule retransmission if SEG does not get ACKed in time
            ScheduledFuture<?> retransmissionFuture = ctx.executor().schedule(() -> {
                // retransmission timeout occurred
                // check if we're not CLOSED and if SEG has not been ACKed
                if (future.channel().isOpen() && handler.state != CLOSED && !ackPromise.isDone() && lessThanOrEqualTo(handler.tcb().sndUna, seg.seq(), ConnectionHandshakeHandler.SEQ_NO_SPACE)) {
                    // not ACKed, send egain
                    ConnectionHandshakeHandler.LOG.error("{}[{}] Segment `{}` has not been acknowledged within {}ms. Send again.", future.channel(), handler.state, seg, rto);
                    ctx.writeAndFlush(seg.copy()).addListener(new RetransmissionTimeoutApplier(ctx, seg, ackPromise, handler, rto * 2));
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
            ConnectionHandshakeHandler.LOG.trace("{}[{}] Unable to send `{}`:", ctx::channel, () -> handler.state, () -> seg, future::cause);
            future.channel().close();
        }
    }
}
