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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.function.LongSupplier;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.SEQ_NO_SPACE;
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
import static org.drasyl.util.Preconditions.requirePositive;
import static org.drasyl.util.RandomUtil.randomInt;
import static org.drasyl.util.SerialNumberArithmetic.lessThan;
import static org.drasyl.util.SerialNumberArithmetic.lessThanOrEqualTo;

/**
 * This handler partially implements the Transmission Control Protocol know from <a
 * href="https://datatracker.ietf.org/doc/html/rfc793#section-3.4">RFC 793</a>. Only the
 * three-way-handshake and tear-down handshake are currently supported. Segments just contain
 * parameters required for that operations.
 * <p>
 * The handler can be configured to perform an active or passive OPEN process.
 * <p>
 * If the handler is configured for active OPEN, a {@link ConnectionHandshakeIssued} will be emitted
 * once the handshake has been issued. The handshake process will result either in a
 * {@link ConnectionHandshakeCompleted} event or {@link ConnectionHandshakeException} exception.
 * <p>
 * https://www.rfc-editor.org/rfc/rfc1323#page-11
 * <p>
 * Wire format ist nicht kompatibel.
 */
@SuppressWarnings({ "java:S138", "java:S1142", "java:S1151", "java:S1192", "java:S1541" })
public class ConnectionHandshakeHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionHandshakeHandler.class);
    private static final ConnectionHandshakeException CONNECTION_REFUSED_EXCEPTION = new ConnectionHandshakeException("Connection refused");
    private static final ClosedChannelException CONNECTION_CLOSED_ERROR = new ClosedChannelException();
    private static final ConnectionHandshakeIssued HANDSHAKE_ISSUED_EVENT = new ConnectionHandshakeIssued();
    private static final ConnectionHandshakeClosing HANDSHAKE_CLOSING_EVENT = new ConnectionHandshakeClosing();
    private static final ConnectionHandshakeException CONNECTION_CLOSING_ERROR = new ConnectionHandshakeException("Connection closing");
    private static final ConnectionHandshakeException CONNECTION_RESET_EXCEPTION = new ConnectionHandshakeException("Connection reset");
    private final Duration userTimeout;
    private final LongSupplier issProvider;
    private final boolean activeOpen;
    private final int initialMss;
    private final int initialWindow;
    ScheduledFuture<?> userTimeoutFuture;
    State state;
    TransmissionControlBlock tcb;
    private UserCallPromise userCallFuture;
    private boolean initDone;
    private boolean readPending;

    /**
     * @param userTimeout   time in ms in which a handshake must taken place after issued
     * @param issProvider   Provider to generate the initial send sequence number
     * @param activeOpen    Initiate active OPEN handshake process automatically on
     *                      {@link #channelActive(ChannelHandlerContext)}
     * @param state         Current synchronization state
     * @param initialMss    Maximum segment size
     * @param initialWindow
     */
    @SuppressWarnings("java:S107")
    ConnectionHandshakeHandler(final Duration userTimeout,
                               final LongSupplier issProvider,
                               final boolean activeOpen,
                               final State state,
                               final int initialMss,
                               final int initialWindow,
                               final TransmissionControlBlock tcb) {
        this.userTimeout = requireNonNegative(userTimeout);
        this.issProvider = requireNonNull(issProvider);
        this.activeOpen = activeOpen;
        this.state = requireNonNull(state);
        this.initialMss = requirePositive(initialMss);
        this.initialWindow = requirePositive(initialWindow);
        this.tcb = tcb;
    }

    /**
     * @param userTimeout   time in ms in which a handshake must taken place after issued
     * @param activeOpen    if {@code true} a handshake will be issued on
     *                      {@link #channelActive(ChannelHandlerContext)}. Otherwise the remote peer
     *                      must initiate the handshake
     * @param initialWindow
     */
    public ConnectionHandshakeHandler(final Duration userTimeout,
                                      final boolean activeOpen,
                                      final int initialMss,
                                      final int initialWindow) {
        this(userTimeout, () -> randomInt(Integer.MAX_VALUE - 1), activeOpen, CLOSED, initialMss, initialWindow, null);
    }

    /**
     * @param userTimeout time in ms in which a handshake must taken place after issued
     * @param activeOpen  if {@code true} a handshake will be issued on
     *                    {@link #channelActive(ChannelHandlerContext)}. Otherwise the remote peer
     *                    must initiate the handshake
     */
    public ConnectionHandshakeHandler(final Duration userTimeout,
                                      final boolean activeOpen) {
        this(userTimeout, activeOpen, 1220, 10_000 * 1220);
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
    public void handlerRemoved(final ChannelHandlerContext ctx) throws Exception {
        // cancel all timeout guards
        cancelUserTimeoutGuard();

        deleteTcb(CONNECTION_CLOSED_ERROR);
    }

    /*
     * Channel Signals
     */

    @Override
    public void close(final ChannelHandlerContext ctx,
                      final ChannelPromise promise) throws Exception {
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
        // cancel all timeout guards
        cancelUserTimeoutGuard();

        deleteTcb(CONNECTION_CLOSED_ERROR);

        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof ConnectionHandshakeSegment) {
            segmentArrives(ctx, (ConnectionHandshakeSegment) msg);
        }
        else {
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        if (tcb != null) {
            tcb.flush(ctx, state, false);
        }

        ctx.fireChannelReadComplete();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx,
                                final Throwable cause) {
        LOG.trace("{}[{}] Exception caught. Close channel:", ctx.channel(), state, cause);

        switchToNewState(ctx, CLOSED);
        deleteTcb(cause);

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
     * OPEN call as described in <a
     * href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.1">RFC 9293, Section
     * 3.10.1</a>.
     */
    private void userCallOpen(final ChannelHandlerContext ctx) {
        LOG.trace("{}[{}] OPEN call received.", ctx.channel(), state);

        // create TCB
        createTcb(ctx);

        // send SYN
        final ConnectionHandshakeSegment seg = ConnectionHandshakeSegment.syn(tcb.iss());
        LOG.trace("{}[{}] Initiate OPEN process by sending `{}`.", ctx.channel(), state, seg);
        tcb.writeAndFlush(ctx, seg);

        // switch state
        switchToNewState(ctx, SYN_SENT);

        // start user call timeout guard
        if (userTimeout.toMillis() > 0) {
            userTimeoutFuture = ctx.executor().schedule(() -> {
                // FIXME: For any state if the user timeout expires, flush all queues, signal the user "error: connection aborted due to user timeout" in general and for any outstanding calls, delete the TCB, enter the CLOSED state, and return.
                LOG.trace("{}[{}] User timeout for OPEN user call expired after {}ms. Close channel.", ctx.channel(), state, userTimeout.toMillis());
                switchToNewState(ctx, CLOSED);
                ctx.fireExceptionCaught(new ConnectionHandshakeException("User timeout for OPEN user call expired after " + userTimeout.toMillis() + "ms. Close channel."));
                ctx.close();
            }, userTimeout.toMillis(), MILLISECONDS);
        }

        // inform application about issue handshake
        ctx.fireUserEventTriggered(HANDSHAKE_ISSUED_EVENT);
    }

    /**
     * SEND call as described in <a
     * href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.2">RFC 9293, Section
     * 3.10.2</a>.
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
     * CLOSE call as described in <a
     * href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.4">RFC 9293, Section
     * 3.10.4</a>.
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
                switchToNewState(ctx, CLOSED);
                ctx.close(promise);
                break;

            case SYN_RECEIVED:
                cancelUserTimeoutGuard();
                ctx.fireExceptionCaught(CONNECTION_CLOSING_ERROR);
                // continue with ESTABLISHED part

            case ESTABLISHED:
                // save promise for later, as it we need ACKnowledgment from remote peer
                userCallFuture = UserCallPromise.wrapPromise(UserCall.CLOSE, promise);

                // signal user connection closing
                ctx.fireUserEventTriggered(HANDSHAKE_CLOSING_EVENT);

                final ConnectionHandshakeSegment seg = ConnectionHandshakeSegment.finAck(tcb.sndNxt(), tcb.rcvNxt());
                LOG.trace("{}[{}] Initiate CLOSE sequence by sending `{}`.", ctx.channel(), state, seg);
                tcb.writeAndFlush(ctx, seg);

                switchToNewState(ctx, FIN_WAIT_1);

                if (userTimeout.toMillis() > 0) {
                    // FIXME: For any state if the user timeout expires, flush all queues, signal the user "error: connection aborted due to user timeout" in general and for any outstanding calls, delete the TCB, enter the CLOSED state, and return.
                    userTimeoutFuture = ctx.executor().schedule(() -> {
                        LOG.trace("{}[{}] User timeout for CLOSE user call expired after {}ms. Close channel.", ctx.channel(), state, userTimeout.toMillis());
                        switchToNewState(ctx, CLOSED);
                        promise.tryFailure(new ConnectionHandshakeException("User timeout for CLOSE user call after " + userTimeout.toMillis() + "ms. Close channel."));
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
     * STATUS call as described in <a
     * href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.6">RFC 9293, Section
     * 3.10.6</a>.
     *
     * @return
     */
    public ConnectionHandshakeStatus userCallStatus() throws ClosedChannelException {
        switch (state) {
            case CLOSED:
                throw CONNECTION_CLOSED_ERROR;
            default:
                return new ConnectionHandshakeStatus(state, tcb);
        }
    }

    /*
     * Helper Methods
     */

    private void createTcb(final ChannelHandlerContext ctx) {
        // window size sollte ein vielfaches von mss betragen
        tcb = new TransmissionControlBlock(ctx.channel(), issProvider.getAsLong(), initialWindow, initialMss);
        LOG.trace("{}[{}] TCB created: {}", ctx.channel(), state, tcb);

//        ctx.executor().scheduleAtFixedRate(() -> {
//            System.err.println("STATUS CALL: " + new ConnectionHandshakeStatus(state, tcb));
//        }, 0, 100, MILLISECONDS);
    }

    private void deleteTcb(final Throwable cause) {
        if (tcb != null) {
            tcb.delete(cause);
            tcb = null;
        }
    }

    private void switchToNewState(final ChannelHandlerContext ctx, final State newState) {
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
                    switchToNewState(ctx, LISTEN);
                }
            }
        }
    }

    /**
     * SEGMENT ARRIVES call as described in <a
     * href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.7">RFC 9293, Section
     * 3.10.7</a>.
     */
    private void segmentArrives(final ChannelHandlerContext ctx,
                                final ConnectionHandshakeSegment seg) {
        ReferenceCountUtil.touch(seg, "segmentArrives");
        LOG.trace("{}[{}] Read `{}`.", ctx.channel(), state, seg);

        // RTTM
        if (tcb != null) {
            tcb.rttMeasurement().segmentArrives(ctx, seg, tcb.mss(), tcb.sndWnd()); // FIXME: platzhalter. eigentlich müssen wir hier SMSS und FlightSize nehmen
        }

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
                                             final ConnectionHandshakeSegment seg) {
        ReferenceCountUtil.touch(seg, "segmentArrivesOnClosedState");
        if (seg.isRst()) {
            // as we are already/still in CLOSED state, we can ignore the RST
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
        tcb.write(response);
    }

    private void segmentArrivesOnListenState(final ChannelHandlerContext ctx,
                                             final ConnectionHandshakeSegment seg) {
        ReferenceCountUtil.touch(seg, "segmentArrivesOnListenState");
        // check RST
        if (seg.isRst()) {
            // Nothing to reset in this state. Just ignore the RST :-)
            return;
        }

        // check ACK
        if (seg.isAck()) {
            // we are on a state were we have never sent anything that must be ACKed
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.rst(seg.ack());
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
                        switchToNewState(ctx, CLOSED);
                        ctx.fireExceptionCaught(new ConnectionHandshakeException("User Timeout! Handshake initiated by remote port has not been completed within " + userTimeout.toMillis() + "ms. Abort handshake. Close channel."));
                        ctx.close();
                    }
                }, userTimeout.toMillis(), MILLISECONDS);
            }

            // yay, peer SYNced with us
            switchToNewState(ctx, SYN_RECEIVED);

            createTcb(ctx);

            // synchronize receive state
            tcb.synchronizeState(seg);

            // update window
            tcb.updateSndWnd(seg);

            LOG.error("{}[{}] TCB synchronized: {}", ctx.channel(), state, tcb);

            // mss negotiation
            tcb.negotiateMss(ctx, seg);

            // send SYN/ACK
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.synAck(tcb.iss(), tcb.rcvNxt());
            LOG.trace("{}[{}] ACKnowlede the received segment and send our SYN `{}`.", ctx.channel(), state, response);
            tcb.write(response);
            return;
        }

        // we should not reach this point. However, if it does happen, just drop the segment
        unexpectedSegment(ctx, seg);
    }

    @SuppressWarnings("java:S3776")
    private void segmentArrivesOnSynSentState(final ChannelHandlerContext ctx,
                                              final ConnectionHandshakeSegment seg) {
        ReferenceCountUtil.touch(seg, "segmentArrivesOnSynSentState");
        // check ACK
        if (tcb.isAckSomethingNeverSent(seg)) {
            // segment ACKed something we never sent
            LOG.trace("{}[{}] Get got an ACKnowledgement `{}` for an Segment we never sent. Seems like remote peer is synchronized to another connection.", ctx.channel(), state, seg);
            if (seg.isRst()) {
                LOG.trace("{}[{}] As the RST bit is set. It doesn't matter as we will reset or connection now.", ctx.channel(), state);
            }
            else {
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.rst(seg.ack());
                LOG.trace("{}[{}] Inform remote peer about the desynchronization state by sending an `{}` and dropping the inbound Segment.", ctx.channel(), state, response);
                tcb.write(response);
            }
            return;
        }
        final boolean acceptableAck = tcb.isAcceptableAck(seg);

        // check RST
        if (seg.isRst()) {
            if (acceptableAck) {
                LOG.trace("{}[{}] Segment `{}` is an acceptable ACKnowledgement. Inform user, drop segment, enter CLOSED state.", ctx.channel(), state, seg);
                switchToNewState(ctx, CLOSED);
                deleteTcb(CONNECTION_RESET_EXCEPTION);
                ctx.fireExceptionCaught(CONNECTION_RESET_EXCEPTION);
                ctx.close();
            }
            else {
                LOG.trace("{}[{}] Segment `{}` is not an acceptable ACKnowledgement. Drop it.", ctx.channel(), state, seg);
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

            LOG.error("{}[{}] TCB synchronized: {}", ctx.channel(), state, tcb);

            if (tcb.synHasBeenAcknowledged()) {
                LOG.trace("{}[{}] Remote peer has ACKed our SYN package and sent us his SYN `{}`. Handshake on our side is completed.", ctx.channel(), state, seg);

                switchToNewState(ctx, ESTABLISHED);
                cancelUserTimeoutGuard();

                // mss negotiation
                tcb.negotiateMss(ctx, seg);

                // ACK
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(tcb.sndNxt(), tcb.rcvNxt());
                LOG.trace("{}[{}] ACKnowlede the received segment with a `{}` so the remote peer can complete the handshake as well.", ctx.channel(), state, response);
                tcb.write(response);

                ctx.fireUserEventTriggered(new ConnectionHandshakeCompleted(tcb.sndNxt(), tcb.rcvNxt()));
            }
            else {
                switchToNewState(ctx, SYN_RECEIVED);
                // SYN/ACK
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.synAck(tcb.iss(), tcb.rcvNxt());
                LOG.trace("{}[{}] Write `{}`.", ctx.channel(), state, response);
                tcb.writeWithout(response);
            }
        }
    }

    @SuppressWarnings("java:S3776")
    private void segmentArrivesOnOtherStates(final ChannelHandlerContext ctx,
                                             final ConnectionHandshakeSegment seg) {
        ReferenceCountUtil.touch(seg, "segmentArrivesOnOtherStates " + seg.toString());
        // check SEQ
        final boolean validSeg = seg.seq() == tcb.rcvNxt();
        final boolean acceptableAck = tcb.isAcceptableAck2(seg);

        if (!validSeg && !acceptableAck) {
            // not expected seq
            if (!seg.isRst()) {
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(tcb.sndNxt(), tcb.rcvNxt());
                LOG.trace("{}[{}] We got an unexpected Segment `{}`. Send an ACKnowledgement `{}` for the Segment we actually expect.", ctx.channel(), state, seg, response);
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
                        switchToNewState(ctx, CLOSED);
                        deleteTcb(CONNECTION_REFUSED_EXCEPTION);
                        ctx.fireExceptionCaught(CONNECTION_REFUSED_EXCEPTION);
                        ctx.close();
                        return;
                    }
                    else {
                        // peer is no longer interested in a connection. Go back to previous state
                        LOG.trace("{}[{}] We got `{}`. Remote peer is not longer interested in a connection. We're going back to the LISTEN state.", ctx.channel(), state, seg);
                        switchToNewState(ctx, LISTEN);
                        return;
                    }

                case ESTABLISHED:
                case FIN_WAIT_1:
                case FIN_WAIT_2:
                    LOG.trace("{}[{}] We got `{}`. Remote peer is not longer interested in a connection. Close channel.", ctx.channel(), state, seg);
                    switchToNewState(ctx, CLOSED);
                    deleteTcb(CONNECTION_RESET_EXCEPTION);
                    ctx.fireExceptionCaught(CONNECTION_RESET_EXCEPTION);
                    ctx.close();
                    return;

                default:
                    // CLOSING
                    // LAST-ACK
                    LOG.trace("{}[{}] We got `{}`. Close channel.", ctx.channel(), state, seg);
                    switchToNewState(ctx, CLOSED);
                    deleteTcb(CONNECTION_CLOSED_ERROR);
                    ctx.close();
                    return;
            }
        }

        // check ACK
        if (!seg.isAck() && seg.content().isReadable()) {
            LOG.trace("{}[{}] Got `{}` with off ACK bit. Drop segment and return.", ctx.channel(), state, seg);
            return;
        }
        else {
            switch (state) {
                case SYN_RECEIVED:
                    if (tcb.isAckOurSynOrFin(seg)) {
                        LOG.trace("{}[{}] Remote peer ACKnowledge `{}` receivable of our SYN. As we've already received his SYN the handshake is now completed on both sides.", ctx.channel(), state, seg);

                        cancelUserTimeoutGuard();
                        switchToNewState(ctx, ESTABLISHED);
                        tcb.updateSndWnd(seg);
                        ctx.fireUserEventTriggered(new ConnectionHandshakeCompleted(tcb.sndNxt(), tcb.rcvNxt()));

                        if (!acceptableAck) {
                            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.rst(seg.ack());
                            LOG.trace("{}[{}] Segment `{}` is not an acceptable ACKnowledgement. Send RST `{}` and drop received Segment.", ctx.channel(), state, seg, response);
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
                        switchToNewState(ctx, FIN_WAIT_2);
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
                        switchToNewState(ctx, CLOSED);
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
                    switchToNewState(ctx, CLOSED);
                    deleteTcb(CONNECTION_CLOSED_ERROR);
                    if (userCallFuture != null) {
                        ctx.close(userCallFuture);
                    }
                    else {
                        ctx.channel().close();
                    }
                    return;

                default:
                    // we got a retransmission of a FIN
                    // Ack it
                    final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(tcb.sndNxt(), tcb.rcvNxt());
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
                    tcb.receiveBuffer().receive(ctx, seg.retain(), tcb);

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
                    final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(tcb.sndNxt(), tcb.rcvNxt());
                    LOG.trace("{}[{}] ACKnowledge receival of `{}` by sending `{}`.", ctx.channel(), state, seg, response);
                    tcb.write(response);

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
            tcb.receiveBuffer().receive(ctx, seg.retain(), tcb);

            // send ACK for the FIN
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(tcb.sndNxt(), tcb.rcvNxt());
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
                    final ConnectionHandshakeSegment response2 = ConnectionHandshakeSegment.fin(tcb.sndNxt());
                    LOG.trace("{}[{}] As we're already waiting for this. We're sending our last Segment `{}` and start waiting for the final ACKnowledgment.", ctx.channel(), state, response2);
                    tcb.write(response2);
                    switchToNewState(ctx, LAST_ACK);
                    break;

                case FIN_WAIT_1:
                    if (tcb.isAckOurSynOrFin(seg)) {
                        // our FIN has been acknowledged
                        LOG.trace("{}[{}] Our FIN has been ACKnowledged. Close channel.", ctx.channel(), state, seg);
                        switchToNewState(ctx, CLOSED);
                        deleteTcb(CONNECTION_CLOSED_ERROR);
                    }
                    else {
                        switchToNewState(ctx, CLOSING);
                    }
                    break;

                case FIN_WAIT_2:
                    LOG.trace("{}[{}] Wait for our ACKnowledgment `{}` to be written to the network. Then close the channel.", ctx.channel(), state, response);
                    switchToNewState(ctx, CLOSED);
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
                                   final ConnectionHandshakeSegment seg) {
        LOG.error("{}[{}] Got unexpected segment `{}`.", ctx.channel(), state, seg);
    }

    private boolean establishedProcessing(final ChannelHandlerContext ctx,
                                          final ConnectionHandshakeSegment seg,
                                          final boolean acceptableAck) {
        if (acceptableAck) {
            LOG.trace("{}[{}] Got `{}`. Advance SND.UNA from {} to {} (+{}).", ctx.channel(), state, seg, tcb.sndUna(), seg.ack(), (int) (seg.ack() - tcb.sndUna()));
            // advance send state
            tcb.handleAcknowledgement(ctx, seg);

            // update window
            if (lessThan(tcb.sndWl1(), seg.seq(), SEQ_NO_SPACE) || (tcb.sndWl1() == seg.seq() && lessThanOrEqualTo(tcb.sndWl2(), seg.ack(), SEQ_NO_SPACE))) {
                tcb.updateSndWnd(seg);
            }
        }
        else if (tcb.isDuplicateAck(seg)) {
            // ACK is duplicate. ignore
            LOG.error("{}[{}] Got old ACK. Ignore.", ctx.channel(), state);
        }
        if (tcb.isAckSomethingNotYetSent(seg)) {
            // FIXME: add support for window!
            // something not yet sent has been ACKed
            LOG.error("{}[{}] something not yet sent has been ACKed: SND.NXT={}; SEG={}", ctx.channel(), state, tcb.sndNxt(), seg);
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(tcb.sndNxt(), tcb.rcvNxt());
            LOG.trace("{}[{}] Write `{}`.", ctx.channel(), state, response);
            tcb.write(response);
            return true;
        }
        return false;
    }

    private void cancelUserTimeoutGuard() {
        if (userTimeoutFuture != null) {
            userTimeoutFuture.cancel(false);
            userTimeoutFuture = null;
        }
    }
}
