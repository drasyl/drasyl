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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.function.Supplier;

import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.State.CLOSED;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.State.ESTABLISHED;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.State.LISTEN;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.State.SYN_RECEIVED;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.State.SYN_SENT;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.UserCall.ABORT;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.UserCall.OPEN;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.RandomUtil.randomInt;

/**
 * This handler performs a handshake with the remote peer. This can be used to create a
 * connection-oriented communication with the remote peer.
 * <p>
 * Depending of the configuration, the synchronization will be automatically issued on {@link
 * #channelActive(ChannelHandlerContext)} or must be manually issued by writing a {@link
 * UserCall#OPEN} message to the channel. Once the synchronization is done, a {@link
 * ConnectionHandshakeCompleted} event will be passed to channel, on failure a {@link
 * ConnectionHandshakeException} exception is passed to channel.
 * <p>
 * The synchronization process has been heavily inspired by the three-way handshake of TCP (<a
 * href="https://datatracker.ietf.org/doc/html/rfc793#section-3.4">RFC 793</a>).
 */
public class ConnectionHandshakeHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionHandshakeHandler.class);
    private static final ConnectionHandshakeIssued HANDSHAKE_ISSUED_EVENT = new ConnectionHandshakeIssued();
    private final long retransmissionTimeout;
    private final long handshakeTimeout;
    private final Supplier<Integer> issProvider;
    private final boolean activeOpen;
    private ChannelPromise openPromise;
    private ScheduledFuture<?> handshakeTimeoutFuture;
    State state;
    // Send Sequence Variables
    int sndUna; // oldest unacknowledged sequence number
    int sndNxt; // next sequence number to be sent
    int iss; // initial send sequence number
    // Receive Sequence Variables
    int rcvNxt; // next sequence number expected on an incoming segments, and is the left or lower edge of the receive window
    int irs; // initial receive sequence number

    /**
     * @param handshakeTimeout time in ms in which a handshake must taken place after issued
     * @param activeOpen       if {@code true} a handshake will be issued on {@link
     *                         #channelActive(ChannelHandlerContext)}. Otherwise the handshake needs
     *                         to be issued by writing a {@link UserCall#OPEN} message to the
     *                         channel
     */
    public ConnectionHandshakeHandler(final long handshakeTimeout,
                                      final boolean activeOpen) {
        this(handshakeTimeout, 100L, () -> randomInt(Integer.MAX_VALUE - 1), activeOpen, CLOSED, 0, 0, 0);
    }

    /**
     * @param handshakeTimeout time in ms in which a handshake must taken place after issued
     * @param issProvider      Provider to generate the initial send sequence number
     * @param activeOpen       Initiate active OPEN handshake process automatically on {@link
     *                         #channelActive(ChannelHandlerContext)}
     * @param state            Current synchronization state
     * @param sndUna           Oldest unacknowledged sequence number
     * @param sndNxt           Next sequence number to be sent
     * @param rcvNxt           Next expected sequence number
     */
    @SuppressWarnings("java:S107")
    ConnectionHandshakeHandler(final long handshakeTimeout,
                               final long retransmissionTimeout,
                               final Supplier<Integer> issProvider,
                               final boolean activeOpen,
                               final State state,
                               final int sndUna,
                               final int sndNxt,
                               final int rcvNxt) {
        this.handshakeTimeout = requireNonNegative(handshakeTimeout);
        this.retransmissionTimeout = requireNonNegative(retransmissionTimeout);
        this.issProvider = requireNonNull(issProvider);
        this.activeOpen = activeOpen;
        this.state = requireNonNull(state);
        this.sndUna = sndUna;
        this.sndNxt = sndNxt;
        this.rcvNxt = rcvNxt;
    }

    /*
     * Channel Events
     */

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        // check if active OPEN mode is enabled
        if (activeOpen) {
            // active OPEN
            LOG.trace("[{}] Perform active OPEN.", state);
            userCallOpen(ctx, ctx.newPromise());
        }
        else {
            // passive OPEN
            LOG.trace("[{}] Perform passive OPEN.", state);
            state = LISTEN;
        }

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        // cancel all timeout guards
        cancelTimeoutGuards();

        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof ConnectionHandshakeSegment) {
            segmentArrives(ctx, (ConnectionHandshakeSegment) msg);
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    /*
     * User Events
     */

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg == OPEN) {
            // user issues handshake
            userCallOpen(ctx, promise);
        }
        else if (msg == ABORT) {
            userCallAbort(ctx, promise);
        }
        else {
            // user call WRITE
            userCallSend(ctx, msg, promise);
        }
    }

    /*
     * User Calls
     */

    private void userCallOpen(final ChannelHandlerContext ctx,
                              final ChannelPromise promise) {
        switch (state) {
            case CLOSED:
            case LISTEN:
                // channel was closed. Perform active OPEN handshake
                openPromise = promise;

                state = SYN_SENT;

                // update send state
                iss = issProvider.get();
                sndUna = iss;
                sndNxt = iss + 1;

                // send SYN
                final int seq = iss;
                final ConnectionHandshakeSegment seg = ConnectionHandshakeSegment.syn(seq);
                LOG.trace("[{}] Initiate active OPEN handshake by sending `{}`.", state, seg);
                ctx.writeAndFlush(seg).addListener(new RetransmissionOnTimeout(ctx, seg));

                // start handshake timeout guard
                applyHandshakeTimeout(ctx, promise);

                ctx.fireUserEventTriggered(HANDSHAKE_ISSUED_EVENT);
                break;

            default:
                promise.setFailure(new Exception("Connection is already open."));
        }
    }

    private void userCallSend(final ChannelHandlerContext ctx,
                              final Object msg,
                              final ChannelPromise promise) {
        if (state != ESTABLISHED) {
            promise.setFailure(new IllegalStateException("Attempting to write to channel with handshake in progress"));
            return;
        }

        ctx.write(msg, promise);
    }

    private void userCallAbort(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        if (openPromise != null) {
            openPromise.setFailure(new Exception("handshake aborted."));
        }

        switch (state) {
            case CLOSED:
                promise.setFailure(new Exception("Connection is already closed."));
                break;

            case LISTEN:
            case SYN_SENT:
                LOG.trace("[{}] Abort connection.", state);
                state = CLOSED;
                promise.setSuccess();
                break;

            case SYN_RECEIVED:
            case ESTABLISHED:
                LOG.trace("[{}] Reset connection.", state);
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.rst(sndNxt);
                LOG.trace("[{}] Write `{}`.", state, response);
                ctx.writeAndFlush(response).addListener(new RetransmissionOnTimeout(ctx, response));
                state = CLOSED;
                promise.setSuccess();
                break;
        }
    }

    private void applyHandshakeTimeout(final ChannelHandlerContext ctx,
                                       final ChannelPromise promise) {
        if (handshakeTimeout > 0) {
            handshakeTimeoutFuture = ctx.executor().schedule(() -> {
                LOG.trace("[{}] Handshake timed out after {}ms!", state, handshakeTimeout);
                state = CLOSED;
                final ConnectionHandshakeException e = new ConnectionHandshakeException("Error: Handshake timeout");
                ctx.fireExceptionCaught(e);
                promise.setFailure(e);
            }, handshakeTimeout, MILLISECONDS);
        }
    }

    private void cancelTimeoutGuards() {
        if (handshakeTimeoutFuture != null) {
            handshakeTimeoutFuture.cancel(false);
        }
    }

    /*
     * Arriving Segments
     */

    private void segmentArrives(final ChannelHandlerContext ctx,
                                final ConnectionHandshakeSegment seg) {
        LOG.trace("[{}] Read `{}`.", state, seg);

        switch (state) {
            case CLOSED:
                segmentArrivesOnClosedState(ctx, seg);
                break;

            case LISTEN:
                segmentArrivesOnListenState(ctx, seg);
                break;

            case SYN_SENT:
                segmentArrivesOnSynSentState(ctx, seg);
                break;

            default:
                segmentArrivesOnOtherStates(ctx, seg);
        }
    }

    private void segmentArrivesOnClosedState(final ChannelHandlerContext ctx,
                                             final ConnectionHandshakeSegment seg) {
        if (seg.isRst()) {
            // as we are already/still in CLOSED state, we can ignore the RST
            ReferenceCountUtil.release(seg);
            return;
        }

        // at this point we're not (longer) willing to synchronize.
        // reset the peer
        final ConnectionHandshakeSegment response;
        if (seg.isAck()) {
            // send normal RST as we don't want to ACK an ACK
            response = ConnectionHandshakeSegment.rst(seg.ack());
        }
        else {
            response = ConnectionHandshakeSegment.rstAck(0, seg.seq());
        }
        LOG.trace("[{}] Write `{}`.", state, response);
        ctx.writeAndFlush(response).addListener(new RetransmissionOnTimeout(ctx, response));
        ReferenceCountUtil.release(seg);
    }

    private void segmentArrivesOnListenState(final ChannelHandlerContext ctx,
                                             final ConnectionHandshakeSegment seg) {
        if (seg.isRst()) {
            // as we are already/still in CLOSED state, we can ignore the RST
            ReferenceCountUtil.release(seg);
            return;
        }

        if (seg.isAck()) {
            // we are on a state were we have never sent anything that must be ACKed
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.rst(seg.ack());
            LOG.trace("[{}] Write `{}`.", state, response);
            ctx.writeAndFlush(response).addListener(new RetransmissionOnTimeout(ctx, response));
            ReferenceCountUtil.release(seg);
            return;
        }

        if (seg.isSyn()) {
            // yay, peer SYNced with us
            state = SYN_RECEIVED;

            // synchronize receive state
            rcvNxt = seg.seq() + 1;
            irs = seg.seq();

            // update send state
            iss = issProvider.get();
            sndUna = iss;
            sndNxt = iss + 1;

            // send SYN/ACK
            final int seq = iss;
            final int ack = rcvNxt;
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.synAck(seq, ack);
            LOG.trace("[{}] Write `{}`.", state, response);
            ctx.writeAndFlush(response).addListener(new RetransmissionOnTimeout(ctx, response));
            ReferenceCountUtil.release(seg);

            ctx.fireUserEventTriggered(HANDSHAKE_ISSUED_EVENT);
            return;
        }

        // we should not reach this point. However, if it does happen, just drop the segment
        unexpectedSegment(seg);
    }

    @SuppressWarnings("java:S3776")
    private void segmentArrivesOnSynSentState(final ChannelHandlerContext ctx,
                                              final ConnectionHandshakeSegment seg) {
        if (seg.isAck() && (seg.ack() <= iss || seg.ack() > sndNxt)) {
            // segment ACKed something we never sent
            if (!seg.isRst()) {
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.rst(seg.ack());
                LOG.trace("[{}] Write `{}`.", state, response);
                ctx.writeAndFlush(response).addListener(new RetransmissionOnTimeout(ctx, response));
            }
            ReferenceCountUtil.release(seg);
            return;
        }

        if (seg.isRst()) {
            final boolean acceptableAck = seg.isAck() && seg.ack() == sndNxt;
            if (acceptableAck) {
                ReferenceCountUtil.release(seg);
                state = CLOSED;
                ctx.fireExceptionCaught(new ConnectionHandshakeException("Error: Connection reset"));
                return;
            }
            else {
                ReferenceCountUtil.release(seg);
                return;
            }
        }

        if (seg.isSyn()) {
            rcvNxt = seg.seq() + 1;
            irs = seg.seq();
            if (seg.isAck()) {
                sndUna = seg.ack();
            }

            final boolean ourSynHasBeenAcked = sndUna > iss;
            if (ourSynHasBeenAcked) {
                cancelTimeoutGuards();
                state = ESTABLISHED;

                // ACK
                final int seq = sndNxt;
                final int ack = rcvNxt;
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(seq, ack);
                LOG.trace("[{}] Write `{}`.", state, response);
                ctx.writeAndFlush(response).addListener(CLOSE_ON_FAILURE);
                ReferenceCountUtil.release(seg);

                ctx.fireUserEventTriggered(new ConnectionHandshakeCompleted(sndNxt, rcvNxt));

                openPromise.setSuccess();
            }
            else {
                state = SYN_RECEIVED;
                // SYN/ACK
                final int seq = iss;
                final int ack = rcvNxt;
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.synAck(seq, ack);
                LOG.trace("[{}] Write `{}`.", state, response);
                ctx.writeAndFlush(response).addListener(new RetransmissionOnTimeout(ctx, response));
                ReferenceCountUtil.release(seg);
            }
        }
    }

    @SuppressWarnings({
            "java:S131",
            "java:S1301",
            "java:S3776",
            "SwitchStatementWithTooFewBranches"
    })
    private void segmentArrivesOnOtherStates(final ChannelHandlerContext ctx,
                                             final ConnectionHandshakeSegment seg) {
        // check SEQ
        switch (state) {
            case SYN_RECEIVED:
            case ESTABLISHED:
                if (seg.seq() != rcvNxt && !seg.isOnlyAck()) {
                    // not expected seq
                    if (!seg.isRst()) {
                        final int seq = sndNxt;
                        final int ack = rcvNxt;
                        final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(seq, ack);
                        LOG.trace("[{}] Write `{}`.", state, response);
                        ctx.writeAndFlush(response).addListener(CLOSE_ON_FAILURE);
                    }
                    ReferenceCountUtil.release(seg);
                    return;
                }
                break;
        }

        // check RST
        if (seg.isRst()) {
            switch (state) {
                case SYN_RECEIVED:
                    if (activeOpen) {
                        // connection has been refused by remote
                        state = CLOSED;
                        ReferenceCountUtil.release(seg);
                        ctx.fireExceptionCaught(new ConnectionHandshakeException("Error: Connection refused"));
                    }
                    else {
                        // peer is no longer interested in a connection. Go back to previous state
                        state = LISTEN;
                        ReferenceCountUtil.release(seg);
                    }
                    return;

                case ESTABLISHED:
                    state = CLOSED;
                    ReferenceCountUtil.release(seg);
                    ctx.fireExceptionCaught(new ConnectionHandshakeException("Error: Connection reset"));
                    return;
            }
        }

        // check ACK
        if (seg.isAck()) {
            switch (state) {
                case SYN_RECEIVED:
                    if (sndUna <= seg.ack() && seg.ack() <= sndNxt) {
                        cancelTimeoutGuards();
                        state = ESTABLISHED;

                        if (sndUna < seg.ack() && seg.ack() <= sndNxt) {
                            sndUna = seg.ack();
                        }
                        else if (seg.ack() < sndUna) {
                            // ACK is duplicate, ignore
                            ReferenceCountUtil.release(seg);
                        }

                        ReferenceCountUtil.release(seg);
                        ctx.fireUserEventTriggered(new ConnectionHandshakeCompleted(sndNxt, rcvNxt));

                        if (openPromise != null) {
                            openPromise.setSuccess();
                        }
                    }
                    else {
                        ReferenceCountUtil.release(seg);
                        final int seq = seg.ack();
                        final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.rst(seq);
                        LOG.trace("[{}] Write `{}`.", state, response);
                        ctx.writeAndFlush(response).addListener(new RetransmissionOnTimeout(ctx, response));
                    }
                    break;
            }
        }
    }

    private void unexpectedSegment(final ConnectionHandshakeSegment seg) {
        ReferenceCountUtil.release(seg);
        LOG.error("[{}] Got unexpected segment `{}`.", state, seg);
    }

    /**
     * States of the handshake progress
     */
    enum State {
        // connection does not exist
        CLOSED,
        // connection non-synchronized
        LISTEN,
        SYN_SENT,
        SYN_RECEIVED,
        // connection synchronized
        ESTABLISHED
    }

    /**
     * Signals to control the handshake progress.
     */
    public enum UserCall {
        // initiate handshake process
        OPEN,
        // abort handshake process
        ABORT
    }

    private class RetransmissionOnTimeout implements ChannelFutureListener {
        private final ChannelHandlerContext ctx;
        private final ConnectionHandshakeSegment seg;

        public RetransmissionOnTimeout(final ChannelHandlerContext ctx,
                                       final ConnectionHandshakeSegment seg) {
            this.ctx = requireNonNull(ctx);
            this.seg = requireNonNull(seg);
        }

        @Override
        public void operationComplete(ChannelFuture future) {
            if (future.isSuccess()) {
                if (!seg.isOnlyAck()) {
                    // schedule retransmission
                    ctx.executor().schedule(() -> {
                        if (future.channel().isOpen() && sndUna <= seg.seq()) {
                            LOG.trace("[{}] Segment `{}` has not been acknowledged within {}ms. Send again.", state, seg, retransmissionTimeout);
                            ctx.writeAndFlush(seg).addListener(this);
                        }
                    }, retransmissionTimeout, MILLISECONDS);
                }
            }
            else {
                LOG.trace("Unable to send `{}`:", () -> seg, future::cause);
                future.channel().close();
            }
        }
    }
}
