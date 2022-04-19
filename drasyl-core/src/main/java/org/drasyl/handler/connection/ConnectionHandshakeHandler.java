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

import java.nio.channels.ClosedChannelException;
import java.util.function.LongSupplier;

import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.connection.State.CLOSED;
import static org.drasyl.handler.connection.State.CLOSE_WAIT;
import static org.drasyl.handler.connection.State.CLOSING;
import static org.drasyl.handler.connection.State.ESTABLISHED;
import static org.drasyl.handler.connection.State.FIN_WAIT_1;
import static org.drasyl.handler.connection.State.FIN_WAIT_2;
import static org.drasyl.handler.connection.State.LAST_ACK;
import static org.drasyl.handler.connection.State.LISTEN;
import static org.drasyl.handler.connection.State.SYN_RECEIVED;
import static org.drasyl.handler.connection.State.SYN_SENT;
import static org.drasyl.handler.connection.State.TIME_WAIT;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.Preconditions.requirePositive;
import static org.drasyl.util.RandomUtil.randomInt;
import static org.drasyl.util.SerialNumberArithmetic.add;
import static org.drasyl.util.SerialNumberArithmetic.greaterThan;
import static org.drasyl.util.SerialNumberArithmetic.lessThan;
import static org.drasyl.util.SerialNumberArithmetic.lessThanOrEqualTo;

/**
 * This handler performs a handshake with the remote peer. This can be used to create a
 * connection-oriented communication with the remote peer.
 * <p>
 * Depending of the configuration, the synchronization will be automatically issued on {@link
 * #channelActive(ChannelHandlerContext)} or must be manually initiated by the remote peer. Once the
 * synchronization is done, a {@link ConnectionHandshakeCompleted} event will be passed to channel,
 * on failure a {@link ConnectionHandshakeException} exception is passed to channel.
 * <p>
 * The synchronization process has been heavily inspired by the three-way handshake of TCP (<a
 * href="https://datatracker.ietf.org/doc/html/rfc793#section-3.4">RFC 793</a>).
 */
public class ConnectionHandshakeHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionHandshakeHandler.class);
    private static final ConnectionHandshakeIssued HANDSHAKE_ISSUED_EVENT = new ConnectionHandshakeIssued();
    private static final ConnectionHandshakeClosing HANDSHAKE_CLOSING_EVENT = new ConnectionHandshakeClosing();
    private static final ConnectionHandshakeException CONNECTION_CLOSING_ERROR = new ConnectionHandshakeException("Connection closing");
    private static final ConnectionHandshakeException CONNECTION_RESET_EXCEPTION = new ConnectionHandshakeException("Connection reset");
    private static final int SEQ_NO_SPACE = 32;
    private final long retransmissionTimeout;
    private final long userTimeout;
    private final LongSupplier issProvider;
    private final boolean activeOpen;
    protected ScheduledFuture<?> userTimeoutFuture;
    private ScheduledFuture<?> timeWaitTimerFuture;
    private ChannelPromise userCallFuture;
    State state;
    // Send Sequence Variables
    long sndUna; // oldest unacknowledged sequence number
    long sndNxt; // next sequence number to be sent
    long iss; // initial send sequence number
    // Receive Sequence Variables
    long rcvNxt; // next sequence number expected on an incoming segments, and is the left or lower edge of the receive window
    long irs; // initial receive sequence number

    /**
     * @param userTimeout time in ms in which a handshake must taken place after issued
     * @param activeOpen  if {@code true} a handshake will be issued on {@link
     *                    #channelActive(ChannelHandlerContext)}. Otherwise the remote peer must
     *                    initiate the handshake
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
                               final LongSupplier issProvider,
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
        userCallClose(ctx, promise);
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
                LOG.trace("{}[{}] Handler is configured to perform active OPEN process.", ctx.channel(), state);
                userCallOpen(ctx, ctx.newPromise());
            }
            else {
                // passive OPEN
                LOG.trace("{}[{}] Handler is configured to perform passive OPEN process. Wait for remote peer to initiate OPEN process.", ctx.channel(), state);
                switchToNewState(ctx, LISTEN);
            }
        }

        ctx.fireChannelActive();
    }

    private void switchToNewState(final ChannelHandlerContext ctx, final State newState) {
        LOG.trace("{}[{} -> {}] Switched to new state.", ctx.channel(), state, newState);
        state = newState;
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

    private void userCallOpen(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        LOG.trace("{}[{}] OPEN call received.", ctx.channel(), state);

        userCallFuture = promise;

        // channel was closed. Perform active OPEN handshake
        // as we have now future we can pass any errors to, we will throw the exception to
        // the channel
        promise.addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                ctx.fireExceptionCaught(future.cause());
            }
        });
        performActiveOpen(ctx);
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
                LOG.trace("{}[{}] Write was performed while we're in passive OPEN mode. Switch to active OPEN mode, enqueue write operation, and initiate OPEN process.", ctx.channel(), state);

                // save promise for later, as it we need ACKnowledgment from remote peer
                userCallFuture = ctx.newPromise();

                // enqueue our write
                userCallFuture.addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        ctx.write(msg, promise);
                    }
                    else {
                        promise.setFailure(future.cause());
                    }
                });
                performActiveOpen(ctx);
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
                promise.setFailure(CONNECTION_CLOSING_ERROR);
                break;
        }
    }

    private void userCallClose(final ChannelHandlerContext ctx,
                               final ChannelPromise promise) {
        LOG.trace("{}[{}] CLOSE call received.", ctx.channel(), state);

        switch (state) {
            case CLOSED:
                ctx.close(promise);
                break;

            case LISTEN:
            case SYN_SENT:
                if (userCallFuture != null) {
                    userCallFuture.setFailure(CONNECTION_CLOSING_ERROR);
                }
                switchToNewState(ctx, CLOSED);
                ctx.close(promise);
                break;

            case SYN_RECEIVED:
                if (userCallFuture != null) {
                    userCallFuture.setFailure(CONNECTION_CLOSING_ERROR);
                }

            case ESTABLISHED:
                // save promise for later, as it we need ACKnowledgment from remote peer
                userCallFuture = promise;

                final ConnectionHandshakeSegment seg = ConnectionHandshakeSegment.finAck(sndNxt, rcvNxt);
                sndNxt++;
                LOG.trace("{}[{}] Initiate CLOSE sequence by sending `{}`.", ctx.channel(), state, seg);
                ctx.writeAndFlush(seg).addListener(new RetransmissionTimeoutApplier(ctx, seg));
                switchToNewState(ctx, FIN_WAIT_1);

                applyUserTimeout(ctx, "CLOSE", promise);
                break;

            case CLOSE_WAIT:
                // save promise for later, as it we need ACKnowledgment from remote peer
                userCallFuture = promise;

                final ConnectionHandshakeSegment seg2 = ConnectionHandshakeSegment.finAck(sndNxt, rcvNxt);
                sndNxt++;
                LOG.trace("{}[{}] As we're already waiting for this. We're sending our last seg `{}` and start waiting for the final ACKnowledgment.", ctx.channel(), state, seg2);
                ctx.writeAndFlush(seg2).addListener(new RetransmissionTimeoutApplier(ctx, seg2));
                switchToNewState(ctx, LAST_ACK);
                break;

            default:
                userCallFuture.addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        promise.setSuccess();
                    }
                    else {
                        promise.setFailure(future.cause());
                    }
                });
                break;
        }
    }

    private void applyUserTimeout(final ChannelHandlerContext ctx,
                                  final String userCall,
                                  final ChannelPromise promise) {
        if (userTimeout > 0) {
            if (userTimeoutFuture != null) {
                userTimeoutFuture.cancel(false);
            }
            userTimeoutFuture = ctx.executor().schedule(() -> {
                LOG.trace("{}[{}] User timeout for {} user call expired after {}ms. Close channel.", ctx.channel(), state, userCall, userTimeout);
                switchToNewState(ctx, CLOSED);
                promise.tryFailure(new ConnectionHandshakeException("User timeout for " + userCall + " user call after " + userTimeout + "ms. Close channel."));
                ctx.channel().close();
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

    private void performActiveOpen(final ChannelHandlerContext ctx) {
        // update send state
        iss = issProvider.getAsLong();
        sndUna = iss;
        sndNxt = add(iss, 1, SEQ_NO_SPACE);

        // send SYN
        final ConnectionHandshakeSegment seg = ConnectionHandshakeSegment.syn(iss);
        LOG.trace("{}[{}] Initiate OPEN process by sending `{}`.", ctx.channel(), state, seg);
        ctx.writeAndFlush(seg).addListener(new RetransmissionTimeoutApplier(ctx, seg));

        switchToNewState(ctx, SYN_SENT);

        // start user timeout guard
        applyUserTimeout(ctx, "OPEN", userCallFuture);

        ctx.fireUserEventTriggered(HANDSHAKE_ISSUED_EVENT);
    }

    /*
     * Arriving Segments
     */

    private void segmentArrives(final ChannelHandlerContext ctx,
                                final ConnectionHandshakeSegment seg) {
        LOG.trace("{}[{}] Read `{}`.", ctx.channel(), state, seg);

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
        LOG.trace("{}[{}] As we're already on CLOSED state, this channel is going to be removed soon. Reset remote peer `{}`.", ctx.channel(), state, response);
        ctx.writeAndFlush(response).addListener(new RetransmissionTimeoutApplier(ctx, response));
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
            LOG.trace("{}[{}] Write `{}`.", ctx.channel(), state, response);
            ctx.writeAndFlush(response).addListener(new RetransmissionTimeoutApplier(ctx, response));
            ReferenceCountUtil.release(seg);
            return;
        }

        // check SYN
        if (seg.isSyn()) {
            LOG.trace("{}[{}] Remote peer initiates handshake by sending a SYN `{}` to us.", ctx.channel(), state, seg);

            if (userTimeout > 0) {
                // create handshake timeguard
                ctx.executor().schedule(() -> {
                    if (state != ESTABLISHED && state != CLOSED) {
                        LOG.trace("{}[{}] Handshake initiated by remote port has not been completed within {}ms. Abort handshake, close channel.", ctx.channel(), state, userTimeout);
                        switchToNewState(ctx, CLOSED);
                        ctx.channel().close();
                    }
                }, userTimeout, MILLISECONDS);
            }

            // yay, peer SYNced with us
            switchToNewState(ctx, SYN_RECEIVED);

            // synchronize receive state
            rcvNxt = add(seg.seq(), 1, SEQ_NO_SPACE);
            irs = seg.seq();

            // update send state
            iss = issProvider.getAsLong();
            sndUna = iss;
            sndNxt = add(iss, 1, SEQ_NO_SPACE);

            // send SYN/ACK
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.synAck(iss, rcvNxt);
            LOG.trace("{}[{}] ACKnowlede the received segment and send our SYN `{}`.", ctx.channel(), state, response);
            ctx.writeAndFlush(response).addListener(new RetransmissionTimeoutApplier(ctx, response));
            ReferenceCountUtil.release(seg);

            ctx.fireUserEventTriggered(HANDSHAKE_ISSUED_EVENT);
            return;
        }

        // we should not reach this point. However, if it does happen, just drop the segment
        unexpectedSegment(ctx, seg);
    }

    @SuppressWarnings("java:S3776")
    private void segmentArrivesOnSynSentState(final ChannelHandlerContext ctx,
                                              final ConnectionHandshakeSegment seg) {
        // check ACK
        if (seg.isAck() && (lessThanOrEqualTo(seg.ack(), iss, SEQ_NO_SPACE) || greaterThan(seg.ack(), sndNxt, SEQ_NO_SPACE))) {
            // segment ACKed something we never sent
            LOG.trace("{}[{}] Get got an ACKnowledgement `{}` for an Segment we never sent. Seems like remote peer is synchronized to another connection.", ctx.channel(), state, seg);
            if (seg.isRst()) {
                LOG.trace("{}[{}] As the RST bit is set. It doesn't matter as we will reset or connection now.", ctx.channel(), state);
            }
            else {
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.rst(seg.ack());
                LOG.trace("{}[{}] Inform remote peer about the desynchronization state by sending an `{}` and dropping the inbound Segment.", ctx.channel(), state, response);
                ctx.writeAndFlush(response).addListener(new RetransmissionTimeoutApplier(ctx, response));
            }
            ReferenceCountUtil.release(seg);
            return;
        }

        // check ACK
        final boolean acceptableAck = isAcceptableAck(seg);
        if (seg.isRst()) {
            if (acceptableAck) {
                ReferenceCountUtil.release(seg);
                switchToNewState(ctx, CLOSED);
                ctx.fireExceptionCaught(CONNECTION_RESET_EXCEPTION);
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
            rcvNxt = add(seg.seq(), 1, SEQ_NO_SPACE);
            irs = seg.seq();
            if (seg.isAck()) {
                // advance send state
                sndUna = seg.ack();
            }

            final boolean ourSynHasBeenAcked = greaterThan(sndUna, iss, SEQ_NO_SPACE);
            if (ourSynHasBeenAcked) {
                LOG.trace("{}[{}] Remote peer has ACKed our SYN package and sent us his SYN `{}`. Handshake on our side is completed.", ctx.channel(), state, seg);

                cancelTimeoutGuards();
                switchToNewState(ctx, ESTABLISHED);
                userCallFuture.setSuccess();
                userCallFuture = null;

                // ACK
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(sndNxt, rcvNxt);
                LOG.trace("{}[{}] ACKnowlede the received segment with a `{}` so the remote peer can complete the handshake as well.", ctx.channel(), state, response);
                ctx.writeAndFlush(response).addListener(CLOSE_ON_FAILURE);
                ReferenceCountUtil.release(seg);

                ctx.fireUserEventTriggered(new ConnectionHandshakeCompleted(sndNxt, rcvNxt));
            }
            else {
                switchToNewState(ctx, SYN_RECEIVED);
                // SYN/ACK
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.synAck(iss, rcvNxt);
                LOG.trace("{}[{}] Write `{}`.", ctx.channel(), state, response);
                ctx.writeAndFlush(response).addListener(new RetransmissionTimeoutApplier(ctx, response));
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
        final boolean acceptableAck = isAcceptableAck(seg);

        if (!validSeg && !acceptableAck) {
            // not expected seq
            if (!seg.isRst()) {
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(sndNxt, rcvNxt);
                LOG.trace("{}[{}] We got an unexpected Segment `{}`. Send an ACKnowledgement for the Segment we actually expect.", ctx.channel(), state, seg, response);
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
                        switchToNewState(ctx, CLOSED);
                        ReferenceCountUtil.release(seg);
                        ctx.fireExceptionCaught(new ConnectionHandshakeException("Connection refused"));
                    }
                    else {
                        // peer is no longer interested in a connection. Go back to previous state
                        switchToNewState(ctx, LISTEN);
                        ReferenceCountUtil.release(seg);
                    }
                    break;

                case ESTABLISHED:
                case FIN_WAIT_1:
                case FIN_WAIT_2:
                case CLOSE_WAIT:
                    switchToNewState(ctx, CLOSED);
                    ReferenceCountUtil.release(seg);
                    ctx.fireExceptionCaught(CONNECTION_RESET_EXCEPTION);
                    break;

                default:
                    switchToNewState(ctx, CLOSED);
                    ReferenceCountUtil.release(seg);
            }
        }

        // check ACK
        if (seg.isAck()) {
            switch (state) {
                case SYN_RECEIVED:
                    if (lessThanOrEqualTo(sndUna, seg.ack(), SEQ_NO_SPACE) && lessThanOrEqualTo(seg.ack(), sndNxt, SEQ_NO_SPACE)) {
                        LOG.trace("{}[{}] Remote peer ACKnowledge `{}` receivable of our SYN. As we've already received his SYN the handshake is now completed on both sides.", ctx.channel(), state, seg);

                        cancelTimeoutGuards();
                        switchToNewState(ctx, ESTABLISHED);

                        if (acceptableAck) {
                            // advance send state
                            sndUna = seg.ack();
                        }
                        else if (lessThan(seg.ack(), sndUna, SEQ_NO_SPACE)) {
                            // ACK is duplicate, ignore
                            ReferenceCountUtil.release(seg);
                        }

                        ReferenceCountUtil.release(seg);
                        ctx.fireUserEventTriggered(new ConnectionHandshakeCompleted(sndNxt, rcvNxt));
                    }
                    else {
                        ReferenceCountUtil.release(seg);
                        final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.rst(seg.ack());
                        LOG.trace("{}[{}] Write `{}`.", ctx.channel(), state, response);
                        ctx.writeAndFlush(response).addListener(new RetransmissionTimeoutApplier(ctx, response));
                    }
                    break;

                case ESTABLISHED:
                case CLOSE_WAIT:
                    // FIXME : implement
                    break;

                case FIN_WAIT_1:
                    if (establishedProcessing(ctx, seg, acceptableAck)) {
                        return;
                    }

                    if (acceptableAck) {
                        // our FIN has been acknowledged
                        switchToNewState(ctx, FIN_WAIT_2);
                    }
                    break;

                case FIN_WAIT_2:
                    if (establishedProcessing(ctx, seg, acceptableAck)) {
                        return;
                    }

                    // TODO: the user's close can be acked

                    break;

                case CLOSING:
                    if (establishedProcessing(ctx, seg, acceptableAck)) {
                        return;
                    }

                    if (acceptableAck) {
                        LOG.trace("{}[{}] Our sent FIN has been ACKnowledged by `{}`. Close sequence done.", ctx.channel(), state, seg);
                        switchToNewState(ctx, CLOSED);
                        ctx.close(userCallFuture);
                    }
                    else {
                        LOG.trace("{}[{}] The received ACKnowledged `{}` does not match our sent FIN. Ignore it.", ctx.channel(), state, seg);
                        ReferenceCountUtil.release(seg);
                        return;
                    }
                    break;

                case LAST_ACK:
                    // our FIN has been ACKed
                    LOG.trace("{}[{}] Our sent FIN has been ACKnowledged by `{}`. Close sequence done.", ctx.channel(), state, seg);
                    switchToNewState(ctx, CLOSED);
                    ctx.close(userCallFuture);
                    return;

                default:
                    // we got a retransmission of a FIN
                    // Ack it
                    final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(sndNxt, rcvNxt);
                    LOG.trace("{}[{}] Write `{}`.", ctx.channel(), state, response);
                    ctx.writeAndFlush(response).addListener(CLOSE_ON_FAILURE);

                    // restart 2 MSL timeout
                    if (timeWaitTimerFuture != null) {
                        timeWaitTimerFuture.cancel(false);
                    }
                    applyTimeWaitTimeout(ctx);
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
            rcvNxt = add(seg.seq(), 1, SEQ_NO_SPACE);

            // send ACK for the FIN
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(sndNxt, rcvNxt);
            LOG.trace("{}[{}] Got CLOSE request `{}` from remote peer. ACKnowledge receival with `{}`.", ctx.channel(), state, seg, response);
            final ChannelFuture ackFuture = ctx.writeAndFlush(response);
            ackFuture.addListener(CLOSE_ON_FAILURE);

            // signal user connection closing
            ctx.fireUserEventTriggered(HANDSHAKE_CLOSING_EVENT);

            switch (state) {
                case SYN_RECEIVED:
                case ESTABLISHED:
                    LOG.trace("{}[{}] This channel is going to close now. Trigger channel close.", ctx.channel(), state);
                    switchToNewState(ctx, CLOSE_WAIT);
                    ctx.pipeline().close();
                    break;

                case FIN_WAIT_1:
                    if (acceptableAck) {
                        // our FIN has been acknowledged
                        switchToNewState(ctx, TIME_WAIT);

                        // Start the time-wait timer, turn off the other timers.
                        applyTimeWaitTimeout(ctx);
                    }
                    else {
                        switchToNewState(ctx, CLOSING);
                    }
                    break;

                case FIN_WAIT_2:
                    LOG.trace("{}[{}] Wait for our ACKnowledgment `{}` to be written to the network. Then close the channel.", ctx.channel(), state, response);
                    switchToNewState(ctx, TIME_WAIT);
                    ackFuture.addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            LOG.trace("{}[{}] Our ACKnowledgment `{}` was written to the network. Close channel!", ctx.channel(), state, response);
                            userCallFuture.setSuccess();
                        }
                        else {
                            LOG.trace("{}[{}] Failed to write our ACKnowledgment `{}` to the network: {}", ctx.channel(), state, response, future.cause());
                            userCallFuture.setFailure(future.cause());
                        }
                        userCallFuture = null;
                        switchToNewState(ctx, CLOSED);
                        future.channel().pipeline().close();
                    });
                    break;

                case TIME_WAIT:
                    // restart 2 MSL time-wait timeout
                    if (timeWaitTimerFuture != null) {
                        timeWaitTimerFuture.cancel(false);
                    }
                    applyTimeWaitTimeout(ctx);
                    break;

                default:
                    // remain in state
            }
        }
    }

    private void applyTimeWaitTimeout(final ChannelHandlerContext ctx) {
        if (userTimeoutFuture != null) {
            userTimeoutFuture.cancel(false);
        }

        timeWaitTimerFuture = ctx.executor().schedule(() -> {
            switchToNewState(ctx, CLOSED);
//            if (closePromise != null) {
//                // close has been triggered by us. pass the event to the remaining pipeline
//                ctx.close(closePromise);
//            }
//            else {
//                // close has been triggered by remote peer. pass event the whole pipeline
//                ctx.pipeline().close();
//            }
        }, retransmissionTimeout, MILLISECONDS);
    }

    private boolean establishedProcessing(final ChannelHandlerContext ctx,
                                          final ConnectionHandshakeSegment seg,
                                          final boolean acceptableAck) {
        if (acceptableAck) {
            // advance send state
            sndUna = seg.ack();
        }
        if (lessThan(seg.ack(), sndUna, SEQ_NO_SPACE)) {
            // ACK is duplicate. ignore
            return true;
        }
        if (greaterThan(seg.ack(), sndUna, SEQ_NO_SPACE)) {
            // something not yet sent has been ACKed
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(sndNxt, rcvNxt);
            LOG.trace("{}[{}] Write `{}`.", ctx.channel(), state, response);
            ctx.writeAndFlush(response).addListener(CLOSE_ON_FAILURE);
            return true;
        }
        return false;
    }

    private boolean isAcceptableAck(final ConnectionHandshakeSegment seg) {
        return seg.isAck() && lessThan(sndUna, seg.ack(), SEQ_NO_SPACE) && lessThanOrEqualTo(seg.ack(), sndNxt, SEQ_NO_SPACE);
    }

    private void unexpectedSegment(final ChannelHandlerContext ctx,
                                   final ConnectionHandshakeSegment seg) {
        ReferenceCountUtil.release(seg);
        LOG.error("{}[{}] Got unexpected segment `{}`.", ctx.channel(), state, seg);
    }

    /**
     * A {@link ChannelFutureListener} that retransmit not acknowledged segments.
     */
    private class RetransmissionTimeoutApplier implements ChannelFutureListener {
        private static final long LOWER_BOUND = 100; // lower bound for retransmission (e.g., 1 second)
        private static final long UPPER_BOUND = 60_000; // upper bound for retransmission (e.g., 1 minute)
        private static final int RTT = 20; // as we're currently not aware of the actual RTT, we use this fixed value
        private static final float ALPHA = .9f; // smoothing factor (e.g., .8 to .9)
        private static final float BETA = 1.7f; // delay variance factor (e.g., 1.3 to 2.0)
        private final ChannelHandlerContext ctx;
        private final ConnectionHandshakeSegment seg;
        private final long srtt;

        public RetransmissionTimeoutApplier(final ChannelHandlerContext ctx,
                                            final ConnectionHandshakeSegment seg,
                                            final long srtt) {
            this.ctx = requireNonNull(ctx);
            this.seg = requireNonNull(seg);
            this.srtt = requirePositive(srtt);
        }

        public RetransmissionTimeoutApplier(final ChannelHandlerContext ctx,
                                            final ConnectionHandshakeSegment seg) {
            this(ctx, seg, RTT);
        }

        @Override
        public void operationComplete(ChannelFuture future) {
            if (future.isSuccess()) {
                if (!seg.isOnlyAck() && !seg.isRst()) {
                    // schedule retransmission
                    long newSrtt = (long) (ALPHA * srtt + (1 - ALPHA) * RTT);
                    final long rto = Math.min(UPPER_BOUND, Math.max(LOWER_BOUND, (long) (BETA * newSrtt)));
                    ctx.executor().schedule(() -> {
                        if (future.channel().isOpen() && state != CLOSED && lessThanOrEqualTo(sndUna, seg.seq(), SEQ_NO_SPACE)) {
                            LOG.trace("{}[{}] Segment `{}` has not been acknowledged within {}ms. Send again.", future.channel(), state, seg, rto);
                            ctx.writeAndFlush(seg).addListener(new RetransmissionTimeoutApplier(ctx, seg, rto));
                        }
                    }, rto, MILLISECONDS);
                }
            }
            else if (!(future.cause() instanceof ClosedChannelException)) {
                LOG.trace("{}[{}] Unable to send `{}`:", ctx::channel, () -> state, () -> seg, future::cause);
                future.channel().close();
            }
        }
    }
}
