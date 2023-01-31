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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.handler.connection.SegmentOption.SackOption;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.connection.Segment.lessThan;
import static org.drasyl.handler.connection.Segment.lessThanOrEqualTo;
import static org.drasyl.handler.connection.SegmentOption.SACK;
import static org.drasyl.handler.connection.State.CLOSED;
import static org.drasyl.handler.connection.State.CLOSING;
import static org.drasyl.handler.connection.State.ESTABLISHED;
import static org.drasyl.handler.connection.State.FIN_WAIT_1;
import static org.drasyl.handler.connection.State.FIN_WAIT_2;
import static org.drasyl.handler.connection.State.LAST_ACK;
import static org.drasyl.handler.connection.State.LISTEN;
import static org.drasyl.handler.connection.State.SYN_RECEIVED;
import static org.drasyl.handler.connection.State.SYN_SENT;
import static org.drasyl.util.Preconditions.requireNonNegative;

/**
 * This handler provides reliable and ordered delivery of bytes between hosts. The protocol is
 * heavily inspired by the Transmission Control Protocol (TCP), but neither implement all features
 * nor it is compatible with it.
 * <p>
 * This handler mainly implements <a href="https://www.rfc-editor.org/rfc/rfc9293.html">RFC 9293
 * Transmission Control Protocol (TCP)</a>, but also includes TCP Timestamps Option and RTTM
 * Mechanism as described in <a href="https://www.rfc-editor.org/rfc/rfc7323">RFC 7323 TCP
 * Extensions for High Performance</a>. Furthermore, the congestion control algorithms slow start
 * and congestion avoidance as described in <a href="https://www.rfc-editor.org/rfc/rfc5681#section-3.1">RFC
 * 5681 TCP Congestion Control</a> are implemented as well. The <a href="https://www.rfc-editor.org/rfc/rfc9293.html#nagle">Nagle
 * algorithm</a> is used as "Silly Window Syndrome" avoidance algorithm.
 * <p>
 * The handler can be configured to perform an active or passive OPEN process.
 * <p>
 * If the handler is configured for active OPEN, a {@link org.drasyl.handler.oldconnection.ConnectionHandshakeIssued}
 * will be emitted once the handshake has been issued. The handshake process will result either in a
 * {@link org.drasyl.handler.oldconnection.ConnectionHandshakeCompleted} event or {@link
 * org.drasyl.handler.oldconnection.ConnectionHandshakeException} exception.
 */
@SuppressWarnings({ "java:S138", "java:S1142", "java:S1151", "java:S1192", "java:S1541" })
public class ReliableDeliveryHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ReliableDeliveryHandler.class);
    private static final ConnectionHandshakeException CONNECTION_REFUSED_EXCEPTION = new ConnectionHandshakeException("Connection refused");
    private static final ClosedChannelException CONNECTION_CLOSED_ERROR = new ClosedChannelException();
    private static final ConnectionHandshakeIssued HANDSHAKE_ISSUED_EVENT = new ConnectionHandshakeIssued();
    private static final ConnectionHandshakeClosing HANDSHAKE_CLOSING_EVENT = new ConnectionHandshakeClosing();
    private static final ConnectionHandshakeException CONNECTION_CLOSING_ERROR = new ConnectionHandshakeException("Connection closing");
    private static final ConnectionHandshakeException CONNECTION_RESET_EXCEPTION = new ConnectionHandshakeException("Connection reset");
    private final Duration userTimeout;
    private final Function<Channel, TransmissionControlBlock> tcbProvider;
    private final boolean activeOpen;
    ScheduledFuture<?> userTimeoutTimer;
    State state;
    TransmissionControlBlock tcb;
    private ChannelPromise userCallFuture;
    private boolean initDone;
    private boolean readPending;

    /**
     * @param userTimeout time in ms in which a handshake must taken place after issued
     * @param activeOpen  Initiate active OPEN handshake process automatically on {@link
     *                    #channelActive(ChannelHandlerContext)}
     * @param state       Current synchronization state
     * @param tcbProvider
     */
    ReliableDeliveryHandler(final Duration userTimeout,
                            final boolean activeOpen,
                            final State state,
                            final Function<Channel, TransmissionControlBlock> tcbProvider,
                            final TransmissionControlBlock tcb) {
        this.userTimeout = requireNonNegative(userTimeout);
        this.activeOpen = activeOpen;
        this.state = requireNonNull(state);
        this.tcbProvider = requireNonNull(tcbProvider);
        this.tcb = tcb;
    }

    ReliableDeliveryHandler(final Duration userTimeout,
                            final boolean activeOpen,
                            final State state,
                            final int initialMss,
                            final int initialWindow) {
        this(userTimeout, activeOpen, state, channel -> {
            final long iss = Segment.randomSeq();
            return new TransmissionControlBlock(iss, iss, 0, iss, 0, initialWindow, 0, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), initialMss);
        }, null);
    }

    /**
     * @param userTimeout time in ms in which a handshake must taken place after issued
     * @param activeOpen  if {@code true} a handshake will be issued on {@link
     *                    #channelActive(ChannelHandlerContext)}. Otherwise the remote peer must
     *                    initiate the handshake
     */
    public ReliableDeliveryHandler(final Duration userTimeout,
                                   final boolean activeOpen) {
        this(userTimeout, activeOpen, CLOSED, 1220, 64 * 1220);
    }

    /*
     * Handler Events
     */

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            initHandler(ctx);
        }
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        // cancel all timeout guards
        cancelUserTimeoutGuard();

        deleteTcb();
    }

    /*
     * Channel Signals
     */

    @Override
    public void close(final ChannelHandlerContext ctx,
                      final ChannelPromise promise) {
        userCallClose(ctx, promise);
    }

    @Override
    public void read(final ChannelHandlerContext ctx) {
        readPending = true;
        if (tcb != null) {
            tcb.receiveBuffer().fireRead(ctx, tcb);
        }

        ctx.read();
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (!(msg instanceof ByteBuf)) {
            // reject all non-ByteBuf messages
            final UnsupportedMessageTypeException exception = new UnsupportedMessageTypeException(msg, ByteBuf.class);
            promise.tryFailure(exception);
            ReferenceCountUtil.safeRelease(msg);
        }
        else {
            // interpret as SEND call
            userCallSend(ctx, (ByteBuf) msg, promise);
        }
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) {
        // FIXME: check for ESTABLISHED?
        if (tcb != null) {
            tcb.flush(ctx, state, true);
        }

        ctx.flush(); // tcb.flush macht eventuell auch schon ein ctx.flush. also hätten wir dan zwei. das ist doof. müssen wir noch besser machen
    }

    /*
     * Channel Events
     */

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        initHandler(ctx);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        cancelUserTimeoutGuard();
        deleteTcb();
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        ReferenceCountUtil.touch(msg, "channelRead");
        if (msg instanceof Segment) {
            segmentArrives(ctx, (Segment) msg);
        }
        else {
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        // FIXME: check for ESTABLISHED?
        if (tcb != null) {
            tcb.flush(ctx, state, false);
        }

        ctx.fireChannelReadComplete();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx,
                                final Throwable cause) {
        LOG.trace("{}[{}] Exception caught. Close channel:", ctx.channel(), state, cause);

        // FIXME: remove line below
        cause.printStackTrace();

        changeState(ctx, CLOSED);
        deleteTcb();

        ctx.close();

        ctx.fireExceptionCaught(cause);
    }

//    @Override
//    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
//        LOG.error("channelWritabilityChanged {}", ctx.channel().isWritable());
//        super.channelWritabilityChanged(ctx);
//    }

    /*
     * User Calls
     */

    /**
     * OPEN call as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.1">RFC
     * 9293, Section 3.10.1</a>.
     */
    private void userCallOpen(final ChannelHandlerContext ctx) {
        LOG.trace("{}[{}] OPEN call received.", ctx.channel(), state);

        // create TCB
        createTcb(ctx);

        // send SYN
        final Segment seg = Segment.syn(tcb.iss());
        LOG.trace("{}[{}] Initiate OPEN process by sending `{}`.", ctx.channel(), state, seg);
        tcb.writeAndFlush(ctx, seg);

        // change state
        changeState(ctx, SYN_SENT);

        // start user call timeout guard
        if (userTimeout.toMillis() > 0) {
            assert userTimeoutTimer == null;
            userTimeoutTimer = ctx.executor().schedule(() -> {
                // For any state if the user timeout expires, flush all queues, signal the user "error: connection aborted due to user timeout" in general and for any outstanding calls, delete the TCB, enter the CLOSED state, and return.
                LOG.trace("{}[{}] User timeout for OPEN user call expired after {}ms. Close channel.", ctx.channel(), state, userTimeout.toMillis());
                changeState(ctx, CLOSED);
                final ConnectionHandshakeException cause = new ConnectionHandshakeException("User timeout for OPEN user call expired after " + userTimeout.toMillis() + "ms. Close channel.");
                deleteTcb();
                ctx.fireExceptionCaught(cause);
                ctx.close();
            }, userTimeout.toMillis(), MILLISECONDS);
        }

        // inform application about issue handshake
        ctx.fireUserEventTriggered(HANDSHAKE_ISSUED_EVENT);
    }

    /**
     * SEND call as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.2">RFC
     * 9293, Section 3.10.2</a>.
     */
    private void userCallSend(final ChannelHandlerContext ctx,
                              final ByteBuf data,
                              final ChannelPromise promise) {
        LOG.trace("{}[{}] SEND call received.", ctx.channel(), state);

        switch (state) {
            case CLOSED:
                // Connection is already closed. Reject write
                LOG.trace("{}[{}] Connection is already closed. Reject data `{}`.", ctx.channel(), state, data);
                promise.tryFailure(CONNECTION_CLOSED_ERROR);
                ReferenceCountUtil.safeRelease(data);
                break;

            case LISTEN:
                // Channel is in passive OPEN mode. SEND user call will trigger active OPEN handshake
                LOG.trace("{}[{}] SEND user wall was requested while we're in passive OPEN mode. Switch to active OPEN mode, initiate OPEN process, and enqueue data `{}` for transmission after connection has been established.", ctx.channel(), state, data);

                // trigger active OPEN handshake
                userCallOpen(ctx);

                // enqueue data for transmission after entering ESTABLISHED state
                tcb.addToSendBuffer(data, promise);
                break;

            case SYN_SENT:
            case SYN_RECEIVED:
                // Queue the data for transmission after entering ESTABLISHED state.
                LOG.trace("{}[{}] Queue data `{}` for transmission after entering ESTABLISHED state.", ctx.channel(), state, data);
                tcb.addToSendBuffer(data, promise);
                break;

            case ESTABLISHED:
                // Queue the data for transmission, to allow it to be sent together with other data for transmission efficiency.
                LOG.trace("{}[{}] Connection is established. Enqueue data `{}` for transmission.", ctx.channel(), state, data);
                tcb.addToSendBuffer(data, promise);
                break;

            default:
                // FIN-WAIT-1
                // FIN-WAIT-2
                // CLOSING
                // LAST-ACK
                LOG.trace("{}[{}] Connection is in process of being closed. Reject data `{}`.", ctx.channel(), state, data);
                promise.tryFailure(CONNECTION_CLOSING_ERROR);
                ReferenceCountUtil.safeRelease(data);
                break;
        }
    }

    /**
     * CLOSE call as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.4">RFC
     * 9293, Section 3.10.4</a>.
     */
    @SuppressWarnings("java:S128")
    private void userCallClose(final ChannelHandlerContext ctx,
                               final ChannelPromise promise) {
        LOG.trace("{}[{}] CLOSE call received.", ctx.channel(), state);

        // FIXME: unser close muss eigentlich darauf warten, dass noch alle ausstehenden daten übertragen wurden

        switch (state) {
            case CLOSED:
                // normale we have to fail the given promise. But here we just pass the close call further through the pipeline
                LOG.trace("{}[{}] Channel is already closed. Pass close call further through the pipeline.", ctx.channel(), state);
                ctx.close(promise);
                break;

            case LISTEN:
            case SYN_SENT:
                cancelUserTimeoutGuard();
                ctx.fireExceptionCaught(CONNECTION_CLOSING_ERROR);
                changeState(ctx, CLOSED);
                ctx.close(promise);
                break;

            case SYN_RECEIVED:
                cancelUserTimeoutGuard();
                ctx.fireExceptionCaught(CONNECTION_CLOSING_ERROR);
                // continue with ESTABLISHED part

            case ESTABLISHED:
                // save promise for later, as it we need ACKnowledgment from remote peer
                assert userCallFuture == null;
                userCallFuture = promise;

                // signal user connection closing
                ctx.fireUserEventTriggered(HANDSHAKE_CLOSING_EVENT);

                final Segment seg = Segment.finAck(tcb.sndNxt(), tcb.rcvNxt());
                LOG.trace("{}[{}] Initiate CLOSE sequence by sending `{}`.", ctx.channel(), state, seg);
                tcb.writeAndFlush(ctx, seg);

                changeState(ctx, FIN_WAIT_1);

                if (userTimeout.toMillis() > 0) {
                    // For any state if the user timeout expires, flush all queues, signal the user "error: connection aborted due to user timeout" in general and for any outstanding calls, delete the TCB, enter the CLOSED state, and return.
                    assert userTimeoutTimer == null;
                    userTimeoutTimer = ctx.executor().schedule(() -> {
                        LOG.trace("{}[{}] User timeout for CLOSE user call expired after {}ms. Close channel.", ctx.channel(), state, userTimeout.toMillis());
                        changeState(ctx, CLOSED);
                        final ConnectionHandshakeException cause = new ConnectionHandshakeException("User timeout for CLOSE user call after " + userTimeout.toMillis() + "ms. Close channel.");
                        promise.tryFailure(cause);
                        deleteTcb();
                        ctx.close();
                    }, userTimeout.toMillis(), MILLISECONDS);
                }
                break;

            default:
                if (userCallFuture != null) {
                    userCallFuture.addListener(new PromiseNotifier<>(promise));
                }
                break;
        }
    }

    /**
     * STATUS call as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.6">RFC
     * 9293, Section 3.10.6</a>.
     *
     * @return
     */
    public ConnectionHandshakeStatus userCallStatus() throws ClosedChannelException {
        if (requireNonNull(state) == CLOSED) {
            throw CONNECTION_CLOSED_ERROR;
        }
        return new ConnectionHandshakeStatus(state, tcb);
    }

    /*
     * Helper Methods
     */

    private void createTcb(final ChannelHandlerContext ctx) {
        assert tcb == null;
        tcb = tcbProvider.apply(ctx.channel());
        LOG.trace("{}[{}] TCB created: {}", ctx.channel(), state, tcb);
    }

    private void deleteTcb() {
        if (tcb != null) {
            tcb.delete();
            tcb = null;
        }
    }

    private void changeState(final ChannelHandlerContext ctx, final State newState) {
        LOG.trace("{}[{} -> {}] Switched to new state.", ctx.channel(), state, newState);
        state = newState;
    }

    private void initHandler(final ChannelHandlerContext ctx) {
        if (!initDone) {
            initDone = true;

            // this state check is only required for some of our unit tests
            if (state == CLOSED) {
                // check if active OPEN mode is enabled
                if (activeOpen) {
                    // active OPEN
                    LOG.trace("{}[{}] Handler is configured to perform active OPEN process. ChannelActive event acts as implicit OPEN call.", ctx.channel(), state);
                    userCallOpen(ctx);
                }
                else {
                    // passive OPEN
                    LOG.trace("{}[{}] Handler is configured to perform passive OPEN process. Go to {} state and wait for remote peer to initiate OPEN process.", ctx.channel(), state, LISTEN);
                    changeState(ctx, LISTEN);
                }
            }
        }
    }

    /**
     * SEGMENT ARRIVES call as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.7">RFC
     * 9293, Section 3.10.7</a>.
     */
    private void segmentArrives(final ChannelHandlerContext ctx,
                                final Segment seg) {
        ReferenceCountUtil.touch(seg, "segmentArrives");
        LOG.trace("{}[{}] Read `{}`.", ctx.channel(), state, seg);

        try {
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
        finally {
            seg.release();
        }
    }

    private void segmentArrivesOnClosedState(final ChannelHandlerContext ctx,
                                             final Segment seg) {
        ReferenceCountUtil.touch(seg, "segmentArrivesOnClosedState");
        if (seg.isRst()) {
            // as we are already/still in CLOSED state, we can ignore the RST
            return;
        }

        // at this point we're not (longer) willing to synchronize.
        // reset the peer
        final Segment response;
        if (seg.isAck()) {
            // send normal RST as we don't want to ACK an ACK
            response = Segment.rst(seg.ack());
        }
        else {
            response = Segment.rstAck(0, seg.seq());
        }
        LOG.trace("{}[{}] As we're already on CLOSED state, this channel is going to be removed soon. Reset remote peer `{}`.", ctx.channel(), state, response);
        tcb.write(response);
    }

    private void segmentArrivesOnListenState(final ChannelHandlerContext ctx,
                                             final Segment seg) {
        ReferenceCountUtil.touch(seg, "segmentArrivesOnListenState");
        // check RST
        if (seg.isRst()) {
            // Nothing to reset in this state. Just ignore the RST :-)
            return;
        }

        if (seg.isAck() || seg.isSyn()) {
            // create TCB
            createTcb(ctx);
        }

        // check ACK
        if (seg.isAck()) {
            // we are on a state were we have never sent anything that must be ACKed
            final Segment response = Segment.rst(seg.ack());
            LOG.trace("{}[{}] We are on a state were we have never sent anything that must be ACKnowledged. Send RST `{}`.", ctx.channel(), state, response);
            tcb.write(response);
            return;
        }

        // check SYN
        if (seg.isSyn()) {
            LOG.trace("{}[{}] Remote peer initiates handshake by sending a SYN `{}` to us.", ctx.channel(), state, seg);

            // start timer that aborts the handshake if remote peer is not ACKing our SYN
            if (userTimeout.toMillis() > 0) {
                // create handshake timeguard
                ctx.executor().schedule(() -> {
                    if (state != ESTABLISHED && state != CLOSED) {
                        LOG.trace("{}[{}] Handshake initiated by remote peer has not been completed within {}ms. Abort handshake. Close channel.", ctx.channel(), state, userTimeout);
                        changeState(ctx, CLOSED);
                        ctx.fireExceptionCaught(new ConnectionHandshakeException("User Timeout! Handshake initiated by remote port has not been completed within " + userTimeout.toMillis() + "ms. Abort handshake. Close channel."));
                        ctx.close();
                    }
                }, userTimeout.toMillis(), MILLISECONDS);
            }

            // RTTM
            tcb.retransmissionQueue().segmentArrivesOnListenState(ctx, seg, tcb);

            // yay, peer SYNced with us
            changeState(ctx, SYN_RECEIVED);

            // synchronize receive state
            tcb.synchronizeState(seg);

            // update window
            tcb.updateSndWnd(seg);

            LOG.trace("{}[{}] TCB synchronized: {}", ctx.channel(), state, tcb);

            // mss negotiation
            tcb.negotiateMss(ctx, seg);

            // send SYN/ACK
            final Segment response = Segment.synAck(tcb.iss(), tcb.rcvNxt());
            LOG.trace("{}[{}] ACKnowlede the received segment and send our SYN `{}`.", ctx.channel(), state, response);
            tcb.write(response);
            return;
        }

        // we should not reach this point. However, if it does happen, just drop the segment
        unexpectedSegment(ctx, seg);
    }

    @SuppressWarnings("java:S3776")
    private void segmentArrivesOnSynSentState(final ChannelHandlerContext ctx,
                                              final Segment seg) {
        ReferenceCountUtil.touch(seg, "segmentArrivesOnSynSentState");
        // check ACK
        if (tcb.isAckSomethingNeverSent(seg)) {
            // segment ACKed something we never sent
            LOG.trace("{}[{}] Get got an ACK `{}` for an SEG we never sent. Seems like remote peer is synchronized to another connection.", ctx.channel(), state, seg);
            if (seg.isRst()) {
                LOG.trace("{}[{}] As the RST bit is set. It doesn't matter as we will reset or connection now.", ctx.channel(), state);
            }
            else {
                final Segment response = Segment.rst(seg.ack());
                LOG.trace("{}[{}] Inform remote peer about the desynchronization state by sending an `{}` and dropping the inbound SEG.", ctx.channel(), state, response);
                tcb.write(response);
            }
            return;
        }
        final boolean acceptableAck = tcb.isAcceptableAck(seg);

        // check RST
        if (seg.isRst()) {
            if (acceptableAck) {
                LOG.trace("{}[{}] SEG `{}` is an acceptable ACK. Inform user, drop segment, enter CLOSED state.", ctx.channel(), state, seg);
                changeState(ctx, CLOSED);
                deleteTcb();
                ctx.fireExceptionCaught(CONNECTION_RESET_EXCEPTION);
                ctx.close();
            }
            else {
                LOG.trace("{}[{}] SEG `{}` is not an acceptable ACK. Drop it.", ctx.channel(), state, seg);
            }
            return;
        }

        // check SYN
        if (seg.isSyn()) {
            // synchronize receive state
            tcb.synchronizeState(seg);

            if (seg.isAck()) {
                // advance send state
                tcb.handleAcknowledgement(ctx, seg);
            }

            // update window
            tcb.updateSndWnd(seg);

            LOG.trace("{}[{}] TCB synchronized: {}", ctx.channel(), state, tcb);

            tcb.retransmissionQueue().segmentArrivesOnSynSentState(ctx, seg, tcb);

            if (tcb.synHasBeenAcknowledged()) {
                LOG.trace("{}[{}] Remote peer has ACKed our SYN and sent us its SYN `{}`. Handshake on our side is completed.", ctx.channel(), state, seg);

                changeState(ctx, ESTABLISHED);
                cancelUserTimeoutGuard();

                // mss negotiation
                tcb.negotiateMss(ctx, seg);

                // ACK
                final Segment response = Segment.ack(tcb.sndNxt(), tcb.rcvNxt());
                LOG.trace("{}[{}] ACKnowlede the received segment with a `{}` so the remote peer can complete the handshake as well.", ctx.channel(), state, response);
                tcb.write(response);

                ctx.fireUserEventTriggered(new ConnectionHandshakeCompleted(tcb.sndNxt(), tcb.rcvNxt()));
            }
            else {
                changeState(ctx, SYN_RECEIVED);
                // SYN/ACK
                final Segment response = Segment.synAck(tcb.iss(), tcb.rcvNxt());
                LOG.trace("{}[{}] Write `{}`.", ctx.channel(), state, response);
                tcb.writeWithout(response);
            }
        }
    }

    @SuppressWarnings("java:S3776")
    private void segmentArrivesOnOtherStates(final ChannelHandlerContext ctx,
                                             final Segment seg) {
        ReferenceCountUtil.touch(seg, "segmentArrivesOnOtherStates " + seg.toString());
        // check SEQ
        boolean acceptableSeg = tcb.isAcceptableSeg(seg);
        final boolean acceptableAck = tcb.isAcceptableAck(seg);

        // RTTM
        acceptableSeg = tcb.retransmissionQueue().segmentArrivesOnOtherStates(ctx, seg, tcb, state, acceptableSeg);

        if (!acceptableSeg) {
            // not expected seq
            if (!seg.isRst()) {
                final Segment response = Segment.ack(tcb.sndNxt(), tcb.rcvNxt());
                LOG.trace("{}[{}] We got an unexpected SEG `{}`. Send an ACK `{}` for the SEG we actually expect.", ctx.channel(), state, seg, response);
                tcb.write(response);
            }
            return;
        }

        // check RST
        if (seg.isRst()) {
            switch (state) {
                case SYN_RECEIVED:
                    if (activeOpen) {
                        // connection has been refused by remote
                        LOG.trace("{}[{}] We got `{}`. Connection has been refused by remote peer.", ctx.channel(), state, seg);
                        changeState(ctx, CLOSED);
                        deleteTcb();
                        ctx.fireExceptionCaught(CONNECTION_REFUSED_EXCEPTION);
                        ctx.close();
                        return;
                    }
                    else {
                        // peer is no longer interested in a connection. Go back to previous state
                        LOG.trace("{}[{}] We got `{}`. Remote peer is not longer interested in a connection. We're going back to the LISTEN state.", ctx.channel(), state, seg);
                        changeState(ctx, LISTEN);
                        return;
                    }

                case ESTABLISHED:
                case FIN_WAIT_1:
                case FIN_WAIT_2:
                    LOG.trace("{}[{}] We got `{}`. Remote peer is not longer interested in a connection. Close channel.", ctx.channel(), state, seg);
                    changeState(ctx, CLOSED);
                    deleteTcb();
                    ctx.fireExceptionCaught(CONNECTION_RESET_EXCEPTION);
                    ctx.close();
                    return;

                default:
                    // CLOSING
                    // LAST-ACK
                    LOG.trace("{}[{}] We got `{}`. Close channel.", ctx.channel(), state, seg);
                    changeState(ctx, CLOSED);
                    deleteTcb();
                    ctx.close();
                    return;
            }
        }

        // check SYN
        if (seg.isSyn()) {
            if (requireNonNull(state) == SYN_RECEIVED) {// If the connection was initiated with a passive OPEN, then return this connection to the LISTEN state and return.
                if (!activeOpen) {
                    LOG.trace("{}[{}] We got an additional `{}`. As this connection was initiated with a passive OPEN return to LISTEN state.", ctx.channel(), state, seg);
                    changeState(ctx, LISTEN);
                    return;
                }
                // Otherwise, handle per the directions for synchronized states below.
            }
            final boolean theseSynchronizedStates = state != SYN_RECEIVED;
            if (theseSynchronizedStates) {
                final Segment response = Segment.ack(tcb.sndNxt(), tcb.rcvNxt());
                LOG.trace("{}[{}] We got `{}` while we're in a synchronized state. Peer might be crashed. Send challenge ACK `{}`.", ctx.channel(), state, seg, response);
                tcb.write(response);
                return;
            }
        }

        // check ACK
        if (!seg.isAck() && seg.content().isReadable()) {
            LOG.trace("{}[{}] Got `{}` with off ACK bit. Drop segment and return.", ctx.channel(), state, seg);
            return;
        }
        else {
            // FIXME:
            tcb.lastAdvertisedWindow = seg.window();

            switch (state) {
                case SYN_RECEIVED:
                    if (tcb.isAckOurSynOrFin(seg)) {
                        LOG.trace("{}[{}] Remote peer ACKnowledge `{}` receivable of our SYN. As we've already received his SYN the handshake is now completed on both sides.", ctx.channel(), state, seg);

                        cancelUserTimeoutGuard();
                        changeState(ctx, ESTABLISHED);
                        tcb.updateSndWnd(seg);
                        ctx.fireUserEventTriggered(new ConnectionHandshakeCompleted(tcb.sndNxt(), tcb.rcvNxt()));

                        if (!acceptableAck) {
                            final Segment response = Segment.rst(seg.ack());
                            LOG.trace("{}[{}] SEG `{}` is not an acceptable ACK. Send RST `{}` and drop received SEG.", ctx.channel(), state, seg, response);
                            tcb.write(response);
                            return;
                        }

                        // advance send state
                        tcb.handleAcknowledgement(ctx, seg);
                    }
                    break;

                case ESTABLISHED:
                    if (establishedProcessing(ctx, seg, acceptableAck)) {
                        return;
                    }
                    break;

                case FIN_WAIT_1:
                    final boolean ackOurFin = tcb.isAckOurSynOrFin(seg);

                    if (establishedProcessing(ctx, seg, acceptableAck)) {
                        return;
                    }

                    if (ackOurFin) {
                        // our FIN has been acknowledged
                        changeState(ctx, FIN_WAIT_2);
                    }
                    break;

                case FIN_WAIT_2:
                    // FIXME: In addition to the processing for the ESTABLISHED state, if the retransmission queue is empty, the user's CLOSE can be acknowledged ("ok") but do not delete the TCB.
                    if (establishedProcessing(ctx, seg, acceptableAck)) {
                        return;
                    }

                    break;

                case CLOSING:
                    final boolean ackOurFin2 = tcb.isAckOurSynOrFin(seg);
                    if (establishedProcessing(ctx, seg, acceptableAck)) {
                        return;
                    }

                    if (ackOurFin2) {
                        LOG.trace("{}[{}] Our sent FIN has been ACKnowledged by `{}`. Close sequence done.", ctx.channel(), state, seg);
                        changeState(ctx, CLOSED);
                        ctx.close(userCallFuture);
                    }
                    else {
                        LOG.trace("{}[{}] The received ACKnowledged `{}` does not match our sent FIN. Ignore it.", ctx.channel(), state, seg);
                        return;
                    }
                    break;

                case LAST_ACK:
                    // our FIN has been ACKed
                    LOG.trace("{}[{}] Our sent FIN has been ACKnowledged by `{}`. Close sequence done.", ctx.channel(), state, seg);
                    changeState(ctx, CLOSED);
                    deleteTcb();
                    if (userCallFuture != null) {
                        ctx.close(userCallFuture);
                    }
                    else {
                        ctx.close();
                    }
                    return;

                default:
                    // we got a retransmission of a FIN
                    // Ack it
                    final Segment response = Segment.ack(tcb.sndNxt(), tcb.rcvNxt());
                    LOG.trace("{}[{}] Write `{}`.", ctx.channel(), state, response);
                    tcb.write(response);
            }
        }

        // check URG here...

        // process the segment text
        if (seg.content().readableBytes() > 0) {
            switch (state) {
                case ESTABLISHED:
                case FIN_WAIT_1:
                case FIN_WAIT_2:
                    final boolean outOfOrder = seg.seq() != tcb.rcvNxt();
                    tcb.receiveBuffer().receive(ctx, tcb, seg);

                    if (readPending) {
                        readPending = false;
                        LOG.trace("{}[{}] Got `{}`. Add to RCV.BUF and trigger channelRead because read is pending.", ctx.channel(), state, seg);
                        tcb.receiveBuffer().fireRead(ctx, tcb);
                    }
                    else if (seg.isPsh()) {
                        LOG.trace("{}[{}] Got `{}`. Add to RCV.BUF and trigger channelRead because PSH flag is set.", ctx.channel(), state, seg);
                        tcb.receiveBuffer().fireRead(ctx, tcb);
                    }
                    else {
                        LOG.trace("{}[{}] Got `{}`. Add to RCV.BUF and wait for more segment.", ctx.channel(), state, seg);
                    }

                    // Ack receival of segment text
                    final Segment response = Segment.ack(tcb.sndNxt(), tcb.rcvNxt());
                    LOG.trace("{}[{}] ACKnowledge receival of `{}` by sending `{}`.", ctx.channel(), state, seg, response);
                    if (outOfOrder) {
                        // send immediate ACK for out-of-order segments
                        tcb.writeAndFlush(ctx, response);
                    }
                    else {
                        tcb.write(response);
                        // TODO: We are delay ACK till channelReadComplete. We have to respect the following: the ACK delay MUST be less than 0.5 seconds (MUST-40)
                    }

                    break;
                default:
                    LOG.trace("{}[{}] Got `{}`. This should not occur. Ignore the segment text.", ctx.channel(), state, seg);
            }
        }

        // check FIN
        if (seg.isFin()) {
            if (state == CLOSED || state == LISTEN || state == SYN_SENT) {
                // we cannot validate SEG.SEQ. drop the segment
                return;
            }

            // advance receive state
            tcb.receiveBuffer().receive(ctx, tcb, seg);

            // send ACK for the FIN
            final Segment response = Segment.ack(tcb.sndNxt(), tcb.rcvNxt());
            LOG.trace("{}[{}] Got CLOSE request `{}` from remote peer. ACKnowledge receival with `{}`.", ctx.channel(), state, seg, response);
            tcb.write(response);

            switch (state) {
                case SYN_RECEIVED:
                case ESTABLISHED:
                    // signal user connection closing
                    ctx.fireUserEventTriggered(HANDSHAKE_CLOSING_EVENT);

                    // wir haben keinen CLOSE_WAIT state, wir gehen also direkt zu LAST_ACK
                    // daher verschicken wir schon hier das FIN, was es sonst zwischen CLOSE_WAIT und LAST_ACK geben würde
                    LOG.trace("{}[{}] This channel is going to close now. Trigger channel close.", ctx.channel(), state);
                    final Segment response2 = Segment.fin(tcb.sndNxt());
                    LOG.trace("{}[{}] As we're already waiting for this. We're sending our last SEG `{}` and start waiting for the final ACKnowledgment.", ctx.channel(), state, response2);
                    tcb.write(response2);
                    changeState(ctx, LAST_ACK);
                    break;

                case FIN_WAIT_1:
                    if (tcb.isAckOurSynOrFin(seg)) {
                        // our FIN has been acknowledged
                        LOG.trace("{}[{}] Our FIN has been ACKnowledged. Close channel.", ctx.channel(), state, seg);
                        changeState(ctx, CLOSED);
                        deleteTcb();
                    }
                    else {
                        changeState(ctx, CLOSING);
                    }
                    break;

                case FIN_WAIT_2:
                    LOG.trace("{}[{}] Wait for our ACKnowledgment `{}` to be written to the network. Then close the channel.", ctx.channel(), state, response);
                    changeState(ctx, CLOSED);
                    userCallFuture.setSuccess();
                    userCallFuture = null;
                    tcb.write(response);
                    // FIXME: Enter the TIME-WAIT state. Start the time-wait timer, turn off the other timers.
                    break;

                default:
                    // remain in state
            }
        }
    }

    private void unexpectedSegment(final ChannelHandlerContext ctx,
                                   final Segment seg) {
        LOG.error("{}[{}] Got unexpected segment `{}`.", ctx.channel(), state, seg);
    }

    private boolean establishedProcessing(final ChannelHandlerContext ctx,
                                          final Segment seg,
                                          final boolean acceptableAck) {
        final boolean duplicateAck = tcb.isDuplicateAck(seg);
        if (acceptableAck) {
            // advance send state
            tcb.handleAcknowledgement(ctx, seg);

            // update window
            if (lessThan(tcb.sndWl1(), seg.seq()) || (tcb.sndWl1() == seg.seq() && lessThanOrEqualTo(tcb.sndWl2(), seg.ack()))) {
                tcb.updateSndWnd(seg);
            }
        }
        if (duplicateAck) {
            SackOption sackOption = (SackOption) seg.options().get(SACK);
            if (sackOption != null) {
                // retransmit?
//                tcb.sendBuffer();
//                System.out.println();
            }

            // ACK is duplicate. ignore
            LOG.trace("{}[{}] Got duplicate ACK `{}`. Ignore.", ctx.channel(), state, seg);
            tcb.gotDuplicateAckCandidate(ctx, seg);
        }
        if (tcb.isAckSomethingNotYetSent(seg)) {
            // something not yet sent has been ACKed
            LOG.error("{}[{}] something not yet sent has been ACKed: SND.NXT={}; SEG={}", ctx.channel(), state, tcb.sndNxt(), seg);
            final Segment response = Segment.ack(tcb.sndNxt(), tcb.rcvNxt());
            LOG.trace("{}[{}] Write `{}`.", ctx.channel(), state, response);
            tcb.write(response);
            return true;
        }
        return false;
    }

    private void cancelUserTimeoutGuard() {
        if (userTimeoutTimer != null) {
            userTimeoutTimer.cancel(false);
            userTimeoutTimer = null;
        }
    }
}
