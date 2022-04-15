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
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.State.CLOSE_WAIT;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.State.CLOSING;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.State.ESTABLISHED;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.State.FIN_WAIT_1;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.State.FIN_WAIT_2;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.State.LAST_ACK;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.State.LISTEN;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.State.SYN_RECEIVED;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.State.SYN_SENT;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.State.TIME_WAIT;
import static org.drasyl.handler.connection.ConnectionHandshakeHandler.UserCall.CLOSE;
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
    private static final ConnectionHandshakeClosing HANDSHAKE_CLOSING_EVENT = new ConnectionHandshakeClosing();
    private final long retransmissionTimeout;
    private final long userTimeout;
    private final Supplier<Integer> issProvider;
    private final boolean activeOpen;
    private ChannelPromise openPromise;
    private ScheduledFuture<?> userTimeoutFuture;
    private ScheduledFuture<?> timeWaitTimerFuture;
    private ChannelPromise closePromise;
    State state;
    // Send Sequence Variables
    int sndUna; // oldest unacknowledged sequence number
    int sndNxt; // next sequence number to be sent
    int iss; // initial send sequence number
    // Receive Sequence Variables
    int rcvNxt; // next sequence number expected on an incoming segments, and is the left or lower edge of the receive window
    int irs; // initial receive sequence number

    /**
     * @param userTimeout time in ms in which a handshake must taken place after issued
     * @param activeOpen  if {@code true} a handshake will be issued on {@link
     *                    #channelActive(ChannelHandlerContext)}. Otherwise the handshake needs to
     *                    be issued by writing a {@link UserCall#OPEN} message to the channel
     */
    public ConnectionHandshakeHandler(final long userTimeout,
                                      final boolean activeOpen) {
        this(userTimeout, 100L, () -> randomInt(Integer.MAX_VALUE - 1), activeOpen, CLOSED, 0, 0, 0);
    }

    /**
     * @param userTimeout time in ms in which a handshake must taken place after issued
     * @param issProvider Provider to generate the initial send sequence number
     * @param activeOpen  Initiate active OPEN handshake process automatically on {@link
     *                    #channelActive(ChannelHandlerContext)}
     * @param state       Current synchronization state
     * @param sndUna      Oldest unacknowledged sequence number
     * @param sndNxt      Next sequence number to be sent
     * @param rcvNxt      Next expected sequence number
     */
    @SuppressWarnings("java:S107")
    ConnectionHandshakeHandler(final long userTimeout,
                               final long retransmissionTimeout,
                               final Supplier<Integer> issProvider,
                               final boolean activeOpen,
                               final State state,
                               final int sndUna,
                               final int sndNxt,
                               final int rcvNxt) {
        this.userTimeout = requireNonNegative(userTimeout);
        this.retransmissionTimeout = requireNonNegative(retransmissionTimeout);
        this.issProvider = requireNonNull(issProvider);
        this.activeOpen = activeOpen;
        this.state = requireNonNull(state);
        this.sndUna = sndUna;
        this.sndNxt = sndNxt;
        this.rcvNxt = rcvNxt;
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        if (state == CLOSED) {
            ctx.close(promise);
        }
        else if (closePromise == null) {
            userCallClose(ctx, promise);
        }
        else {
            closePromise.addListener(future -> {
                if (future.isSuccess()) {
                    promise.setSuccess();
                }
                else {
                    promise.setFailure(future.cause());
                }
            });
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        // user call WRITE
        userCallSend(ctx, msg, promise);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        if (state == CLOSED) {
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
                LOG.trace("[{}] Switched to new state.", state);
            }
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
     * User Calls
     */

    private void userCallOpen(final ChannelHandlerContext ctx,
                              final ChannelPromise promise) {
        switch (state) {
            case CLOSED:
            case LISTEN:
                // channel was closed. Perform active OPEN handshake
                performActiveOpen(ctx, promise);
                break;

            default:
                promise.setFailure(new ConnectionHandshakeException("Connection is already exists"));
        }
    }

    private void userCallSend(final ChannelHandlerContext ctx,
                              final Object msg,
                              final ChannelPromise promise) {
        switch (state) {
            case CLOSED:
                promise.setFailure(new ConnectionHandshakeException("Connection does not exist"));
                break;

            case LISTEN:
                // channel was in passive OPEN mode. Now switch to active OPEN handshake
                ReferenceCountUtil.release(msg);
                promise.setFailure(new ConnectionHandshakeException("Connection does not exist. Handshake is now issued. Please try again later"));
                performActiveOpen(ctx, ctx.newPromise());
                break;

            case SYN_SENT:
            case SYN_RECEIVED:
                ReferenceCountUtil.release(msg);
                promise.setFailure(new ConnectionHandshakeException("Handshake in progress"));
                break;

            case ESTABLISHED:
            case CLOSE_WAIT:
                ctx.write(msg, promise);
                break;

            default:
                ReferenceCountUtil.release(msg);
                promise.setFailure(new ConnectionHandshakeException("Connection closing"));
                break;
        }
    }

    private void userCallClose(final ChannelHandlerContext ctx,
                               final ChannelPromise promise) {
        switch (state) {
            case CLOSED:
                promise.setFailure(new ConnectionHandshakeException("Connection does not exist"));
                break;

            case LISTEN:
            case SYN_SENT:
                ctx.close(promise);
                break;

            case SYN_RECEIVED:
            case ESTABLISHED:
                final int seq = sndNxt;
                final int ack = rcvNxt;
                sndNxt++;
                final ConnectionHandshakeSegment seg = ConnectionHandshakeSegment.finAck(seq, ack);
                LOG.trace("[{}] Write `{}`.", state, seg);
                ctx.writeAndFlush(seg).addListener(new RetransmissionOnTimeout(ctx, seg));
                state = FIN_WAIT_1;
                LOG.trace("[{}] Switched to new state.", state);

                closePromise = promise;
                applyUserTimeout(ctx, CLOSE, promise);
                break;

            case CLOSE_WAIT:
                final int seq2 = sndNxt;
                final int ack2 = rcvNxt;
                sndNxt++;
                final ConnectionHandshakeSegment seg2 = ConnectionHandshakeSegment.finAck(seq2, ack2);
                LOG.trace("[{}] Write `{}`.", state, seg2);
                ctx.writeAndFlush(seg2).addListener(new RetransmissionOnTimeout(ctx, seg2));
                state = LAST_ACK;
                LOG.trace("[{}] Switched to new state.", state);
                break;

            default:
                promise.setFailure(new ConnectionHandshakeException("Connection closing"));
                break;
        }
    }

    private void applyUserTimeout(final ChannelHandlerContext ctx,
                                  final UserCall call,
                                  final ChannelPromise promise) {
        if (userTimeout > 0) {
            if (userTimeoutFuture != null) {
                userTimeoutFuture.cancel(false);
            }
            userTimeoutFuture = ctx.executor().schedule(() -> {
                LOG.trace("[{}] User timeout for {} user call expired after {}ms. Close channel.", state, call, userTimeout);
                state = CLOSED;
                LOG.trace("[{}] Switched to new state.", state);
                final ConnectionHandshakeException e = new ConnectionHandshakeException("User timeout for " + call + " user call");
                promise.setFailure(e);
                ctx.close();
            }, userTimeout, MILLISECONDS);
        }
    }

    private void cancelTimeoutGuards() {
        if (userTimeoutFuture != null) {
            userTimeoutFuture.cancel(false);
        }
        if (timeWaitTimerFuture != null) {
            timeWaitTimerFuture.cancel(false);
        }
    }

    private void performActiveOpen(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        openPromise = promise;

        state = SYN_SENT;
        LOG.trace("[{}] Switched to new state.", state);

        // update send state
        iss = issProvider.get();
        sndUna = iss;
        sndNxt = iss + 1;

        // send SYN
        final int seq = iss;
        final ConnectionHandshakeSegment seg = ConnectionHandshakeSegment.syn(seq);
        LOG.trace("[{}] Initiate active OPEN handshake by sending `{}`.", state, seg);
        ctx.writeAndFlush(seg).addListener(new RetransmissionOnTimeout(ctx, seg));

        // start user timeout guard
        applyUserTimeout(ctx, OPEN, promise);

        ctx.fireUserEventTriggered(HANDSHAKE_ISSUED_EVENT);
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
        // check RST
        if (seg.isRst()) {
            // as we are already/still in CLOSED state, we can ignore the RST
            ReferenceCountUtil.release(seg);
            return;
        }

        // check ACK
        if (seg.isAck()) {
            // we are on a state were we have never sent anything that must be ACKed
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.rst(seg.ack());
            LOG.trace("[{}] Write `{}`.", state, response);
            ctx.writeAndFlush(response).addListener(new RetransmissionOnTimeout(ctx, response));
            ReferenceCountUtil.release(seg);
            return;
        }

        // check SYN
        if (seg.isSyn()) {
            // yay, peer SYNced with us
            state = SYN_RECEIVED;
            LOG.trace("[{}] Switched to new state.", state);

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
        // check ACK
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

        // check ACK
        if (seg.isRst()) {
            final boolean acceptableAck = seg.isAck() && seg.ack() == sndNxt;
            if (acceptableAck) {
                ReferenceCountUtil.release(seg);
                state = CLOSED;
                LOG.trace("[{}] Switched to new state.", state);
                ctx.fireExceptionCaught(new ConnectionHandshakeException("Connection reset"));
                return;
            }
            else {
                ReferenceCountUtil.release(seg);
                return;
            }
        }

        // check SYN
        if (seg.isSyn()) {
            // synchronize receive state
            rcvNxt = seg.seq() + 1;
            irs = seg.seq();
            if (seg.isAck()) {
                // advance send state
                sndUna = seg.ack();
            }

            final boolean ourSynHasBeenAcked = sndUna > iss;
            if (ourSynHasBeenAcked) {
                cancelTimeoutGuards();
                state = ESTABLISHED;
                LOG.trace("[{}] Switched to new state.", state);

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
                LOG.trace("[{}] Switched to new state.", state);
                // SYN/ACK
                final int seq = iss;
                final int ack = rcvNxt;
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.synAck(seq, ack);
                LOG.trace("[{}] Write `{}`.", state, response);
                ctx.writeAndFlush(response).addListener(new RetransmissionOnTimeout(ctx, response));
                ReferenceCountUtil.release(seg);
            }

            return;
        }

        // drop
        ReferenceCountUtil.release(seg);
    }

    @SuppressWarnings("java:S3776")
    private void segmentArrivesOnOtherStates(final ChannelHandlerContext ctx,
                                             final ConnectionHandshakeSegment seg) {
        // check SEQ
        final boolean validSeg = seg.seq() == rcvNxt;
        final boolean validAck = seg.isAck() && sndUna < seg.ack() && seg.ack() <= sndNxt;

        if (!validSeg && !validAck) {
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

        // check RST
        if (seg.isRst()) {
            switch (state) {
                case SYN_RECEIVED:
                    if (activeOpen) {
                        // connection has been refused by remote
                        state = CLOSED;
                        LOG.trace("[{}] Switched to new state.", state);
                        ReferenceCountUtil.release(seg);
                        ctx.fireExceptionCaught(new ConnectionHandshakeException("Connection refused"));
                    }
                    else {
                        // peer is no longer interested in a connection. Go back to previous state
                        state = LISTEN;
                        LOG.trace("[{}] Switched to new state.", state);
                        ReferenceCountUtil.release(seg);
                    }
                    break;

                case ESTABLISHED:
                case FIN_WAIT_1:
                case FIN_WAIT_2:
                case CLOSE_WAIT:
                    state = CLOSED;
                    LOG.trace("[{}] Switched to new state.", state);
                    ReferenceCountUtil.release(seg);
                    ctx.fireExceptionCaught(new ConnectionHandshakeException("Connection reset"));
                    break;

                default:
                    state = CLOSED;
                    LOG.trace("[{}] Switched to new state.", state);
                    ReferenceCountUtil.release(seg);
            }
        }

        // check ACK
        if (seg.isAck()) {
            switch (state) {
                case SYN_RECEIVED:
                    if (sndUna <= seg.ack() && seg.ack() <= sndNxt) {
                        cancelTimeoutGuards();
                        state = ESTABLISHED;
                        LOG.trace("[{}] Switched to new state.", state);

                        if (sndUna < seg.ack() && seg.ack() <= sndNxt) {
                            // advance send state
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

                case ESTABLISHED:
                case CLOSE_WAIT:
                    // FIXME : implement
                    break;

                case FIN_WAIT_1:
                    final boolean finAcked = sndUna < seg.ack() && seg.ack() <= sndNxt;

                    if (establishedProcessing(ctx, seg)) {
                        return;
                    }

                    if (finAcked) {
                        // our FIN has been acknowledged
                        state = FIN_WAIT_2;
                        LOG.trace("[{}] Switched to new state.", state);
                    }
                    break;

                case FIN_WAIT_2:
                    if (establishedProcessing(ctx, seg)) {
                        return;
                    }

                    // TODO: the user's close can be acked

                    break;

                case CLOSING:
                    final boolean finAcked2 = sndUna < seg.ack() && seg.ack() <= sndNxt;

                    if (establishedProcessing(ctx, seg)) {
                        return;
                    }

                    if (finAcked2) {
                        state = TIME_WAIT;
                        LOG.trace("[{}] Switched to new state.", state);

                        // Start the time-wait timer, turn off the other timers.
                        applyTimeWaitTimer(ctx);
                    }
                    else {
                        ReferenceCountUtil.release(seg);
                    }
                    break;

                case LAST_ACK:
                    // our FIN has been ACKed
                    state = CLOSED;
                    LOG.trace("[{}] Switched to new state.", state);
                    if (closePromise != null) {
                        // close has been triggered by us. pass the event to the remaining pipeline
                        ctx.close(closePromise);
                    }
                    else {
                        // close has been triggered by remote peer. pass event the whole pipeline
                        ctx.pipeline().close();
                    }
                    return;

                default:
                    // we got a retransmission of a FIN
                    // Ack it
                    final int seq = sndNxt;
                    final int ack = rcvNxt;
                    final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(seq, ack);
                    LOG.trace("[{}] Write `{}`.", state, response);
                    ctx.writeAndFlush(response).addListener(CLOSE_ON_FAILURE);

                    // restart 2 MSL timeout
                    if (timeWaitTimerFuture != null) {
                        timeWaitTimerFuture.cancel(false);
                    }
                    applyTimeWaitTimer(ctx);
            }
        }

        // check URG here...

        // check segment text here...

        // check FIN
        if (seg.isFin()) {
            if (state == CLOSED || state == LISTEN || state == SYN_SENT) {
                // we cannot validate SEG.SEQ. drop the segment
                ReferenceCountUtil.release(seg);
                return;
            }

            // advance receive state
            rcvNxt = seg.seq() + 1;

            // send ACK for the FIN
            final int seq = sndNxt;
            final int ack = rcvNxt;
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(seq, ack);
            LOG.trace("[{}] Write `{}`.", state, response);
            ctx.writeAndFlush(response).addListener(CLOSE_ON_FAILURE);

            // signal user connection closing
            ctx.fireUserEventTriggered(HANDSHAKE_CLOSING_EVENT);

            switch (state) {
                case SYN_RECEIVED:
                case ESTABLISHED:
                    state = CLOSE_WAIT;
                    LOG.trace("[{}] Switched to new state.", state);
                    ctx.pipeline().close();
                    break;

                case FIN_WAIT_1:
                    if (sndUna < seg.ack() && seg.ack() <= sndNxt) {
                        // our FIN has been acknowledged
                        state = TIME_WAIT;
                        LOG.trace("[{}] Switched to new state.", state);

                        // Start the time-wait timer, turn off the other timers.
                        applyTimeWaitTimer(ctx);
                    }
                    else {
                        state = CLOSING;
                        LOG.trace("[{}] Switched to new state.", state);
                    }
                    break;

                case FIN_WAIT_2:
                    state = TIME_WAIT;
                    LOG.trace("[{}] Switched to new state.", state);
                    // Start the time-wait timer, turn off the other timers.
                    applyTimeWaitTimer(ctx);
                    break;

                case TIME_WAIT:
                    // restart 2 MSL time-wait timeout
                    if (timeWaitTimerFuture != null) {
                        timeWaitTimerFuture.cancel(false);
                    }
                    applyTimeWaitTimer(ctx);
                    break;

                default:
                    // remain in state
            }
        }
    }

    private void applyTimeWaitTimer(final ChannelHandlerContext ctx) {
        if (userTimeoutFuture != null) {
            userTimeoutFuture.cancel(false);
        }

        timeWaitTimerFuture = ctx.executor().schedule(() -> {
            state = CLOSED;
            LOG.trace("[{}] Switched to new state.", state);
            if (closePromise != null) {
                // close has been triggered by us. pass the event to the remaining pipeline
                ctx.close(closePromise);
            }
            else {
                // close has been triggered by remote peer. pass event the whole pipeline
                ctx.pipeline().close();
            }
        }, retransmissionTimeout, MILLISECONDS);
    }

    private boolean establishedProcessing(final ChannelHandlerContext ctx,
                                          final ConnectionHandshakeSegment seg) {
        if (sndUna < seg.ack() && seg.ack() <= sndNxt) {
            // advance send state
            sndUna = seg.ack();
        }
        if (seg.ack() < sndUna) {
            // ACK is duplicate. ignore
            return true;
        }
        if (seg.ack() > sndUna) {
            // something not yet sent has been ACKed
            final int seq = sndNxt;
            final int ack = rcvNxt;
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(seq, ack);
            LOG.trace("[{}] Write `{}`.", state, response);
            ctx.writeAndFlush(response).addListener(CLOSE_ON_FAILURE);
            return true;
        }
        return false;
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
        ESTABLISHED,
        FIN_WAIT_1,
        FIN_WAIT_2,
        CLOSING,
        TIME_WAIT,
        CLOSE_WAIT,
        LAST_ACK
    }

    /**
     * Signals to control the handshake progress.
     */
    public enum UserCall {
        // initiate handshake process
        OPEN,
        CLOSE,
        // abort handshake process
        ABORT,
    }

    /**
     * A {@link ChannelFutureListener} that retransmit not acknowledged segments.
     */
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
                if (!seg.isOnlyAck() && !seg.isRst()) {
                    // schedule retransmission
                    ctx.executor().schedule(() -> {
                        if (future.channel().isOpen() && state != CLOSED && sndUna <= seg.seq()) {
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
