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
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.handler.connection.SegmentOption.SackOption;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.function.Function;

import static java.time.Duration.ofMinutes;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.connection.Segment.add;
import static org.drasyl.handler.connection.Segment.lessThan;
import static org.drasyl.handler.connection.Segment.lessThanOrEqualTo;
import static org.drasyl.handler.connection.SegmentOption.SACK;
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

/**
 * This handler provides reliable and ordered delivery of bytes between hosts. The protocol is
 * heavily inspired by the Transmission Control Protocol (TCP), but neither implement all features
 * nor it is compatible with it.
 * <p>
 * This handler mainly implements <a href="https://www.rfc-editor.org/rfc/rfc9293.html">RFC 9293
 * Transmission Control Protocol (TCP)</a>, but also includes TCP Timestamps Option and RTTM
 * Mechanism as described in <a href="https://www.rfc-editor.org/rfc/rfc7323">RFC 7323 TCP
 * Extensions for High Performance</a>. Furthermore, the congestion control algorithms slow start,
 * congestion avoidance, fast retransmit, and fast recovery as described in <a
 * href="https://www.rfc-editor.org/rfc/rfc5681#section-3.1">RFC 5681 TCP Congestion Control</a> are
 * implemented as well. The improvements presented in <a href="https://www.rfc-editor.org/rfc/rfc6582">RFC
 * 6582 The NewReno Modification to TCP's Fast Recovery Algorithm</a> and <a
 * href="https://www.rfc-editor.org/rfc/rfc3042">RFC 3042 Enhancing TCP's Loss Recovery Using
 * Limited Transmit</a> are added to the fast recovery algorithm.
 * <p>
 * The <a href="https://www.rfc-editor.org/rfc/rfc9293.html#nagle">Nagle algorithm</a> is used as
 * "Silly Window Syndrome" avoidance algorithm. To improve performance of recovering from multiple
 * losses, the <a href="https://www.rfc-editor.org/rfc/rfc2018">RFC 2018 TCP Selective
 * Acknowledgment Options</a> is used in conjunction with <a href=""></a>.
 * <p>
 * The handler can be configured to perform an active or passive OPEN process.
 * <p>
 * If the handler is configured for active OPEN, a {@link org.drasyl.handler.oldconnection.ConnectionHandshakeIssued}
 * will be emitted once the handshake has been issued. The handshake process will result either in a
 * {@link org.drasyl.handler.oldconnection.ConnectionHandshakeCompleted} event or {@link
 * org.drasyl.handler.oldconnection.ConnectionHandshakeException} exception.
 */
@SuppressWarnings({
        "java:S125",
        "java:S138",
        "java:S1066",
        "java:S1142",
        "java:S1151",
        "java:S1192",
        "java:S1541",
        "java:S3626",
        "java:S3776"
})
public class ReliableDeliveryHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ReliableDeliveryHandler.class);
    private static final ConnectionHandshakeException CONNECTION_REFUSED_EXCEPTION = new ConnectionHandshakeException("Connection refused");
    private static final ClosedChannelException CONNECTION_NOT_EXIST_ERROR = new ClosedChannelException();
    private static final ConnectionHandshakeIssued HANDSHAKE_ISSUED_EVENT = new ConnectionHandshakeIssued();
    private static final ConnectionHandshakeClosing HANDSHAKE_CLOSING_EVENT = new ConnectionHandshakeClosing();
    private static final ConnectionHandshakeException CONNECTION_CLOSING_ERROR = new ConnectionHandshakeException("Connection closing");
    private static final ConnectionHandshakeException CONNECTION_RESET_EXCEPTION = new ConnectionHandshakeException("Connection reset");
    private static final ConnectionHandshakeException CONNECTION_EXISTS_EXCEPTION = new ConnectionHandshakeException("Connection already exists");
    // RFC 9293: Maximum Segment Lifetime, the time a TCP segment can exist in the internetwork
    // RFC 9293: system. Arbitrarily defined to be 2 minutes.
    static final Duration MSL = ofMinutes(2);
    private final Duration userTimeout;
    private final Function<Channel, TransmissionControlBlock> tcbProvider;
    private final boolean activeOpen;
    private final Duration msl;
    ScheduledFuture<?> userTimeoutTimer;
    State state;
    TransmissionControlBlock tcb;
    private ChannelPromise userCallFuture;
    private boolean readPending;

    /**
     * @param userTimeout      time in ms in which a handshake must taken place after issued
     * @param tcbProvider
     * @param activeOpen       Initiate active OPEN handshake process automatically on {@link
     *                         #channelActive(ChannelHandlerContext)}
     * @param msl
     * @param userTimeoutTimer
     * @param state            Current synchronization state
     * @param userCallFuture
     * @param readPending
     */
    @SuppressWarnings("java:S107")
    ReliableDeliveryHandler(final Duration userTimeout,
                            final Function<Channel, TransmissionControlBlock> tcbProvider,
                            final boolean activeOpen,
                            final Duration msl,
                            final ScheduledFuture<?> userTimeoutTimer,
                            final State state,
                            final TransmissionControlBlock tcb,
                            final ChannelPromise userCallFuture,
                            final boolean readPending) {
        this.userTimeout = requireNonNegative(userTimeout);
        this.activeOpen = activeOpen;
        this.msl = requirePositive(msl);
        this.userTimeoutTimer = userTimeoutTimer;
        this.state = state;
        this.tcbProvider = requireNonNull(tcbProvider);
        this.tcb = tcb;
        this.userCallFuture = userCallFuture;
        this.readPending = readPending;
    }

    ReliableDeliveryHandler(final Duration userTimeout,
                            final boolean activeOpen,
                            final State state,
                            final int initialMss,
                            final int initialWindow) {
        this(userTimeout, channel -> {
            final long iss = Segment.randomSeq();
            return new TransmissionControlBlock(() -> iss, 0, 0, 0, 0, 0, initialWindow, 0, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), initialMss);
        }, activeOpen, MSL, null, state, null, null, false);
    }

    /**
     * @param userTimeout time in ms in which a handshake must taken place after issued
     * @param activeOpen  if {@code true} a handshake will be issued on {@link
     *                    #channelActive(ChannelHandlerContext)}. Otherwise the remote peer must
     *                    initiate the handshake
     */
    public ReliableDeliveryHandler(final Duration userTimeout,
                                   final boolean activeOpen) {
        this(userTimeout, activeOpen, null, 1432, 128 * 1432);
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
        // cancel all timers
        cancelUserTimer();
        cancelUserTimer();

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
        userCallReceive(ctx);
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
        // TODO: check for ESTABLISHED?
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
        cancelUserTimer();
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

        switch (state) {
            case CLOSED:
                // RFC 9293: Create a new transmission control block (TCB) to hold connection state
                // RFC 9293: information.
                createTcb(ctx);

                // RFC 9293: Fill in local socket identifier, remote socket, Diffserv field,
                // RFC 9293: security/compartment, and user timeout information.
                // RFC 9293: Note that some parts of the remote socket may be unspecified in a
                // RFC 9293: passive OPEN and are to be filled in by the parameters of the incoming
                // RFC 9293: SYN segment. Verify the security and Diffserv value requested are
                // RFC 9293: allowed for this user, if not, return "error: Diffserv value not
                // RFC 9293: allowed" or "error: security/compartment not allowed".
                // (not applicable for us)

                if (!activeOpen) {
                    // RFC 9293: If passive, enter the LISTEN state
                    LOG.trace("{}[{}] Handler is configured to perform passive OPEN process. Go to {} state and wait for remote peer to initiate OPEN process.", ctx.channel(), state, LISTEN);
                    changeState(ctx, LISTEN);
                    // RFC 9293: and return.
                    return;
                }
                else {
                    LOG.trace("{}[{}] Handler is configured to perform active OPEN process. ChannelActive event acts as implicit OPEN call.", ctx.channel(), state);

                    // RFC 9293: If active and the remote socket is unspecified, return "error: remote
                    // RFC 9293: socket unspecified";
                    // (not applicable for us)

                    // RFC 9293: if active and the remote socket is specified, issue a SYN segment.

                    // RFC 9293: An initial send sequence number (ISS) is selected.
                    tcb.selectIss();

                    // RFC 9293: A SYN segment of the form <SEQ=ISS><CTL=SYN> is sent.
                    final Segment seg = Segment.syn(tcb.iss());
                    LOG.trace("{}[{}] Initiate OPEN process by sending `{}`.", ctx.channel(), state, seg);
                    tcb.writeAndFlush(ctx, seg);
                    // RFC 9293: Set SND.UNA to ISS, SND.NXT to ISS+1,
                    tcb.initSndUnaSndNxt();

                    // RFC 9293: enter SYN-SENT state,
                    changeState(ctx, SYN_SENT);

                    // start user timer
                    startUserTimer(ctx, null);

                    // inform application about issue handshake
                    ctx.fireUserEventTriggered(HANDSHAKE_ISSUED_EVENT);

                    // RFC 9293: and return.
                    return;

                    // RFC 9293: If the caller does not have access to the local socket specified,
                    // RFC 9293: return "error: connection illegal for this process". If there is no
                    // RFC 9293: room to create a new connection, return "error: insufficient
                    // RFC 9293: resources".
                    // (not applicable for us)
                }

            case LISTEN:
                // RFC 9293: If the OPEN call is active and the remote socket is specified, then
                // RFC 9293: change the connection from passive to active,

                // RFC 9293: select an ISS.
                tcb.selectIss();

                // RFC 9293: Send a SYN segment,
                final Segment seg = Segment.syn(tcb.iss());
                LOG.trace("{}[{}] Initiate OPEN process by sending `{}`.", ctx.channel(), state, seg);
                tcb.writeAndFlush(ctx, seg);

                // RFC 9293: set SND.UNA to ISS, SND.NXT to ISS+1.
                tcb.initSndUnaSndNxt();

                //  RFC 9293: Enter SYN-SENT state.
                changeState(ctx, SYN_SENT);

                // start user timer
                startUserTimer(ctx, null);

                //  RFC 9293: Data associated with SEND may be sent with SYN segment or queued for
                //  RFC 9293: transmission after entering ESTABLISHED state. The urgent bit if
                //  RFC 9293: requested in the command must be sent with the data segments sent as a
                //  RFC 9293: result of this command. If there is no room to queue the request,
                //  RFC 9293: respond with "error: insufficient resources". If the remote socket was
                //  RFC 9293: not specified, then return "error: remote socket unspecified".
                break;

            case SYN_SENT:
            case SYN_RECEIVED:
            case ESTABLISHED:
            case FIN_WAIT_1:
            case FIN_WAIT_2:
            case CLOSE_WAIT:
            case CLOSING:
            case LAST_ACK:
            case TIME_WAIT:
                // RFC 9293: Return "error: connection already exists".
                ctx.fireUserEventTriggered(CONNECTION_EXISTS_EXCEPTION);
        }
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
                // RFC 9293: If the user does not have access to such a connection, then return
                // RFC 9293: "error: connection illegal for this process".
                // (not applicable for us)

                // RFC 9293: Otherwise, return "error: connection does not exist".
                // Connection is already closed. Reject write
                LOG.trace("{}[{}] Connection is already closed. Reject data `{}`.", ctx.channel(), state, data);
                promise.tryFailure(CONNECTION_NOT_EXIST_ERROR);
                ReferenceCountUtil.safeRelease(data);
                break;

            case LISTEN:
                // RFC 9293: If the remote socket is specified, then change the connection from
                // passive to active,
                // Channel is in passive OPEN mode. SEND user call will trigger active OPEN handshake
                LOG.trace("{}[{}] SEND user wall was requested while we're in passive OPEN mode. Switch to active OPEN mode, initiate OPEN process, and enqueue data `{}` for transmission after connection has been established.", ctx.channel(), state, data);

                // trigger active OPEN handshake
                userCallOpen(ctx);

                // RFC 9293: select an ISS.
                tcb.selectIss();

                // RFC 9293: Send a SYN segment,
                // (is already done by userCallOpen)

                // RFC 9293: set SND.UNA to ISS, SND.NXT to ISS+1.
                // (is already done by userCallOpen)

                // RFC 9293: Enter SYN-SENT state.
                // (is already done by userCallOpen)

                // RFC 9293: Data associated with SEND may be sent with SYN segment or queued for
                // RFC 9293: transmission after entering ESTABLISHED state.
                tcb.addToSendBuffer(data, promise);

                // RFC 9293: The urgent bit if requested in the command must be sent with the data
                // RFC 9293: segments sent as a result of this command.
                // (URG not supported! It is only kept by TCP for legacy reasons, see SHLD-13)

                // RFC 9293: If there is no room to queue the request, respond with "error:
                // RFC 9293: insufficient resources". If the remote socket was not specified, then
                // RFC 9293: return "error: remote socket unspecified".
                // (not applicable for us)
                break;

            case SYN_SENT:
            case SYN_RECEIVED:
                // RFC 9293: Queue the data for transmission after entering ESTABLISHED state.
                LOG.trace("{}[{}] Queue data `{}` for transmission after entering ESTABLISHED state.", ctx.channel(), state, data);
                tcb.addToSendBuffer(data, promise);

                // RFC 9293: If no space to queue, respond with "error: insufficient resources".
                // (not applicable for us)
                break;

            case ESTABLISHED:
            case CLOSE_WAIT:
                // RFC 9293: Segmentize the buffer and send it with a piggybacked acknowledgment
                // RFC 9293: (acknowledgment value = RCV.NXT).
                // Queue the data for transmission, to allow it to be sent together with other data for transmission efficiency.
                LOG.trace("{}[{}] Connection is established. Enqueue data `{}` for transmission.", ctx.channel(), state, data);
                tcb.addToSendBuffer(data, promise);

                // RFC 9293: If there is insufficient space to remember this buffer, simply return
                // RFC 9293: "error: insufficient resources".
                // (not applicable for us)
                break;

            case FIN_WAIT_1:
            case FIN_WAIT_2:
            case CLOSING:
            case LAST_ACK:
            case TIME_WAIT:
                // RFC 9293: Return "error: connection closing" and do not service request.
                LOG.trace("{}[{}] Connection is in process of being closed. Reject data `{}`.", ctx.channel(), state, data);
                promise.tryFailure(CONNECTION_CLOSING_ERROR);
                ReferenceCountUtil.safeRelease(data);
                break;
        }
    }

    /**
     * RECEIVE call as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.3">RFC
     * 9293, Section 3.10.3</a>.
     */
    @SuppressWarnings("java:S128")
    private void userCallReceive(final ChannelHandlerContext ctx) {
        // FIXME

        // TODO: set readPending = true in any states?
        readPending = true;
        if (tcb != null) {
            tcb.receiveBuffer().fireRead(ctx, tcb);
        }

        ctx.read();

        // FIXME:
        switch (state) {
            case CLOSED:
                assert tcb == null;
                // RFC 9293: If the user does not have access to such a connection, return
                // RFC 9293: "error: connection illegal for this process".
                // RFC 9293: Otherwise, return "error: connection does not exist".
                return;

            case LISTEN:
            case SYN_SENT:
            case SYN_RECEIVED:
                // RFC 9293: Queue for processing after entering ESTABLISHED state. If there is no
                // RFC 9293: room to queue this request, respond with "error: insufficient
                // RFC 9293: resources".

            case ESTABLISHED:
            case FIN_WAIT_1:
            case FIN_WAIT_2:
                // RFC 9293: If insufficient incoming segments are queued to satisfy the request,
                // RFC 9293: queue the request. If there is no queue space to remember the RECEIVE,
                // RFC 9293: respond with "error: insufficient resources".

                // RFC 9293: Reassemble queued incoming segments into receive buffer and return to
                // RFC 9293: user. Mark "push seen" (PUSH) if this is the case.

                // RFC 9293: If RCV.UP is in advance of the data currently being passed to the user,
                // RFC 9293: notify the user of the presence of urgent data.

                // RFC 9293: When the TCP endpoint takes responsibility for delivering data to the
                // RFC 9293: user, that fact must be communicated to the sender via an
                // RFC 9293: acknowledgment. The formation of such an acknowledgment is described
                // RFC 9293: below in the discussion of processing an incoming segment.

            case CLOSE_WAIT:
                // RFC 9293: Since the remote side has already sent FIN, RECEIVEs must be satisfied
                // RFC 9293: by data already on hand, but not yet delivered to the user. If no text
                // RFC 9293: is awaiting delivery, the RECEIVE will get an "error: connection
                // RFC 9293: closing" response. Otherwise, any remaining data can be used to satisfy
                // RFC 9293: the RECEIVE.

            case CLOSING:
            case LAST_ACK:
            case TIME_WAIT:
                // RFC 9293: Return "error: connection closing".
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
                // RFC 9293: If the user does not have access to such a connection, return
                // RFC 9293: "error: connection illegal for this process".
                // (not applicable for us)

                // FIXME:
                //  RFC 9293: Otherwise, return "error: connection does not exist".
                // normale we have to fail the given promise. But here we just pass the close call further through the pipeline
                LOG.trace("{}[{}] Channel is already closed. Pass close call further through the pipeline.", ctx.channel(), state);
                ctx.close(promise);
                break;

            case LISTEN:
                // FIXME:
                //  RFC 9293: Any outstanding RECEIVEs are returned with "error: closing" responses.
                //  RFC 9293: Delete TCB,
                cancelUserTimer();
                //  RFC 9293: enter CLOSED state,
                changeState(ctx, CLOSED);
                ctx.close(promise);
                //  RFC 9293: and return.
                return;

            case SYN_SENT:
                // RFC 9293: Delete the TCB
                deleteTcb();
                // RFC 9293: and return "error: closing" responses to any queued SENDs, or RECEIVEs.
                cancelUserTimer();
                ctx.fireExceptionCaught(CONNECTION_CLOSING_ERROR);
                changeState(ctx, CLOSED);
                ctx.close(promise);
                break;

            case SYN_RECEIVED:
                // FIXME:
                //  RFC 9293: If no SENDs have been issued and there is no pending data to send,
                //  RFC 9293: then form a FIN segment and send it,
                //  RFC 9293: and enter FIN-WAIT-1 state;
                //  RFC 9293: otherwise, queue for processing after entering ESTABLISHED state.
                cancelUserTimer();
                ctx.fireExceptionCaught(CONNECTION_CLOSING_ERROR);
                // continue with ESTABLISHED part

            case ESTABLISHED:
                // RFC 9293: Queue this until all preceding SENDs have been segmentized,
                // RFC 9293: then form a FIN segment and send it.
                // RFC 9293: In any case, enter FIN-WAIT-1 state.
                // save promise for later, as it we need ACKnowledgment from remote peer
                assert userCallFuture == null;
                userCallFuture = promise;

                // signal user connection closing
                ctx.fireUserEventTriggered(HANDSHAKE_CLOSING_EVENT);

                final Segment seg = Segment.finAck(tcb.sndNxt(), tcb.rcvNxt());
                LOG.trace("{}[{}] Initiate CLOSE sequence by sending `{}`.", ctx.channel(), state, seg);
                tcb.writeAndFlush(ctx, seg);

                changeState(ctx, FIN_WAIT_1);

                // start user timer
                startUserTimer(ctx, promise);
                break;

            case FIN_WAIT_1:
            case FIN_WAIT_2:
                // FIXME:
                //  RFC 9293: Strictly speaking, this is an error and should receive an
                //  RFC 9293: "error: connection closing" response.
                //  RFC 9293: An "ok" response would be acceptable, too, as long as a second FIN is
                //  RFC 9293: not emitted (the first FIN may be retransmitted, though).
                break;

            case CLOSE_WAIT:
                // TODO:
                //  RFC 9293: Queue this request until all preceding SENDs have been segmentized;

                // RFC 9293: then send a FIN segment,
                final Segment seg2 = Segment.finAck(tcb.sndNxt(), tcb.rcvNxt());
                tcb.writeAndFlush(ctx, seg2);

                // RFC 9293: enter LAST-ACK state.
                changeState(ctx, LAST_ACK);
                break;

            case CLOSING:
            case LAST_ACK:
            case TIME_WAIT:
                // FIXME:
                //  RFC 9293: Respond with "error: connection closing".
        }
    }

    /**
     * STATUS call as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.6">RFC
     * 9293, Section 3.10.6</a>.
     *
     * @return
     */
    public ConnectionHandshakeStatus userCallStatus() throws ClosedChannelException {
        switch (state) {
            case CLOSED:
                // RFC 9293: If the user should not have access to such a connection, return
                // RFC 9293: "error: connection illegal for this process".
                // RFC 9293: Otherwise, return "error: connection does not exist".
                throw CONNECTION_NOT_EXIST_ERROR;

            case LISTEN:
            case SYN_SENT:
            case SYN_RECEIVED:
            case ESTABLISHED:
            case FIN_WAIT_1:
            case FIN_WAIT_2:
            case CLOSE_WAIT:
            case CLOSING:
            case LAST_ACK:
            case TIME_WAIT:
                // RFC 9293: Return state and the TCB pointer.
                return new ConnectionHandshakeStatus(state, tcb);
        }

        return null;
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
        if (state == null) {
            assert tcb == null;
            state = CLOSED;
            userCallOpen(ctx);
        }
    }

    /**
     * SEGMENT ARRIVES event as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.7">RFC
     * 9293, Section 3.10.7</a>.
     */
    private void segmentArrives(final ChannelHandlerContext ctx,
                                final Segment seg) {
        ReferenceCountUtil.touch(seg, "segmentArrives");
        LOG.trace("{}[{}] Read `{}`.", ctx.channel(), state, seg);

        try {
            switch (state) {
                case CLOSED:
                    // RFC 9293: If the state is CLOSED (i.e., TCB does not exist), then
                    assert tcb == null;
                    segmentArrivesOnClosedState(ctx, seg);
                    break;

                case LISTEN:
                    // RFC 9293: If the state is LISTEN, then
                    segmentArrivesOnListenState(ctx, seg);
                    break;

                case SYN_SENT:
                    // RFC 9293: If the state is SYN-SENT, then
                    segmentArrivesOnSynSentState(ctx, seg);
                    break;

                default:
                    // RFC 9293: Otherwise,
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
        // RFC 9293: all data in the incoming segment is discarded.
        // (this is handled by handler's auto release of all arrived segments)

        // RFC 9293: An incoming segment containing a RST is discarded.
        if (seg.isRst()) {
            return;
        }

        // RFC 9293: An incoming segment not containing a RST causes a RST to be sent in response.
        // RFC 9293: The acknowledgment and sequence field values are selected to make the reset
        // RFC 9293: sequence acceptable to the TCP endpoint that sent the offending segment.
        final Segment response;
        if (!seg.isAck()) {
            // RFC 9293: If the ACK bit is off, sequence number zero is used,
            // RFC 9293: <SEQ=0><ACK=SEG.SEQ+SEG.LEN><CTL=RST,ACK>
            response = Segment.rstAck(0, add(seg.seq(), seg.len()));
        }
        else {
            // RFC 9293: If the ACK bit is on,
            // RFC 9293: <SEQ=SEG.ACK><CTL=RST>
            response = Segment.rst(seg.ack());
        }
        LOG.trace("{}[{}] As we're already on CLOSED state, this channel is going to be removed soon. Reset remote peer `{}`.", ctx.channel(), state, response);
        ctx.writeAndFlush(response);
        // RFC 9293: Return.
        return;
    }

    private void segmentArrivesOnListenState(final ChannelHandlerContext ctx,
                                             final Segment seg) {
        ReferenceCountUtil.touch(seg, "segmentArrivesOnListenState");
        // RFC 9293: First, check for a RST:
        if (seg.isRst()) {
            // RFC 9293: An incoming RST segment could not be valid since it could not have been
            // RFC 9293: sent in response to anything sent by this incarnation of the connection. An
            // RFC 9293: incoming RST should be ignored. Return.
            return;
        }

        // RFC 9293: Second, check for an ACK:
        if (seg.isAck()) {
            // RFC 9293: Any acknowledgment is bad if it arrives on a connection still in the LISTEN
            // RFC 9293: state. An acceptable reset segment should be formed for any arriving
            // RFC 9293: ACK-bearing segment. The RST should be formatted as follows:
            // RFC 9293: <SEQ=SEG.ACK><CTL=RST>
            final Segment response = Segment.rst(seg.ack());
            LOG.trace("{}[{}] We are on a state were we have never sent anything that must be ACKnowledged. Send RST `{}`.", ctx.channel(), state, response);
            ctx.writeAndFlush(response);
            // Return.
            return;
        }

        // RFC 9293: Third, check for a SYN:
        if (seg.isSyn()) {
            // RFC 9293: If the SYN bit is set, check the security. If the security/compartment on
            // RFC 9293: the incoming segment does not exactly match the security/compartment in
            // RFC 9293: the TCB, then send a reset and return.
            // RFC 9293: <SEQ=0><ACK=SEG.SEQ+SEG.LEN><CTL=RST,ACK>
            // (not applicable for us)

            LOG.trace("{}[{}] Remote peer initiates handshake by sending a SYN `{}` to us.", ctx.channel(), state, seg);

            // start timer that aborts the handshake if remote peer is not ACKing our SYN
            if (userTimeout.toMillis() > 0) {
                // create handshake timeguard
                ctx.executor().schedule(() -> {
                    if (state != ESTABLISHED && state != CLOSED) {
                        LOG.trace("{}[{}] Handshake initiated by remote peer has not been completed within {}ms. Abort handshake. Close channel.", ctx.channel(), state, userTimeout);
                        changeState(ctx, CLOSED);
                        // FIXME: das ist kein User Timeout!
                        ctx.fireExceptionCaught(new ConnectionHandshakeException("User Timeout! Handshake initiated by remote port has not been completed within " + userTimeout.toMillis() + "ms. Abort handshake. Close channel."));
                        ctx.close();
                    }
                }, userTimeout.toMillis(), MILLISECONDS);
            }

            // RTTM
            tcb.retransmissionQueue().segmentArrivesOnListenState(ctx, seg, tcb);

            // yay, peer SYNced with us
            changeState(ctx, SYN_RECEIVED);

            // RFC 9293: Set RCV.NXT to SEG.SEQ+1, IRS is set to SEG.SEQ,
            tcb.synchronizeState(seg);

            // TODO:
            //  RFC 9293: and any other control or text should be queued for processing later.

            // update window
            tcb.updateSndWnd(ctx, seg);

            LOG.trace("{}[{}] TCB synchronized: {}", ctx.channel(), state, tcb);

            // mss negotiation
            tcb.negotiateMss(ctx, seg);

            // RFC 9293: ISS should be selected
            tcb.selectIss();

            // RFC 9293: and a SYN segment sent of the form:
            // RFC 9293: <SEQ=ISS><ACK=RCV.NXT><CTL=SYN,ACK>
            final Segment response = Segment.synAck(tcb.iss(), tcb.rcvNxt());
            LOG.trace("{}[{}] ACKnowlede the received segment and send our SYN `{}`.", ctx.channel(), state, response);
            tcb.write(response);
            return;
        }

        // RFC 9293: Fourth, other data or control:
        // RFC 9293: This should not be reached.
        // RFC 9293: Drop the segment
        // (this is handled by handler's auto release of all arrived segments)
        // RFC 9293: and return. Any other control or data-bearing segment (not containing SYN) must
        // RFC 9293: have an ACK and thus would have been discarded by the ACK processing in the
        // RFC 9293: second step, unless it was first discarded by RST checking in the first step.
        unexpectedSegment(ctx, seg);
    }

    @SuppressWarnings("java:S3776")
    private void segmentArrivesOnSynSentState(final ChannelHandlerContext ctx,
                                              final Segment seg) {
        ReferenceCountUtil.touch(seg, "segmentArrivesOnSynSentState");

        // RFC 9293: First, check the ACK bit:
        if (seg.isAck()) {
            // RFC 9293: If the ACK bit is set,
            if (tcb.isAckSomethingNeverSent(seg)) {
                // RFC 9293: If SEG.ACK =< ISS or SEG.ACK > SND.NXT, send a reset (unless the RST
                // RFC 9293: bit is set, if so drop the segment and return)
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

                // RFC 9293: and discard the segment.
                // (this is handled by handler's auto release of all arrived segments)
                // RFC 9293: Return.
                return;
            }
        }

        final boolean acceptableAck = tcb.isAcceptableAck(seg);

        // RFC 9293: Second, check the RST bit:
        if (seg.isRst()) {
            // RFC 9293: If the RST bit is set,

            // TODO:
            //  RFC 9293: A potential blind reset attack is described in RFC 5961 [9]. The
            //  RFC 9293: mitigation described in that document has specific applicability explained
            //  RFC 9293: therein, and is not a substitute for cryptographic protection (e.g., IPsec
            //  RFC 9293: or TCP-AO). A TCP implementation that supports the mitigation described in
            //  RFC 9293: RFC 5961 SHOULD first check that the sequence number exactly matches
            //  RFC 9293: RCV.NXT prior to executing the action in the next paragraph.

            // RFC 9293: If the ACK was acceptable,
            if (acceptableAck) {
                LOG.trace("{}[{}] SEG `{}` is an acceptable ACK. Inform user, drop segment, enter CLOSED state.", ctx.channel(), state, seg);
                // RFC 9293: then signal to the user "error: connection reset",
                ctx.fireExceptionCaught(CONNECTION_RESET_EXCEPTION);
                ctx.close();
                // RFC 9293: drop the segment,
                // (this is handled by handler's auto release of all arrived segments)
                // RFC 9293: enter CLOSED state,
                changeState(ctx, CLOSED);
                // RFC 9293: delete TCB,
                deleteTcb();
                // RFC 9293: and return.
                return;
            }
            else {
                // RFC 9293: Otherwise (no ACK), drop the segment
                tcb.selectIss();
                LOG.trace("{}[{}] SEG `{}` is not an acceptable ACK. Drop it.", ctx.channel(), state, seg);
                // RFC 9293: and return.
                return;
            }
        }

        // RFC 9293: Third, check the security:
        // RFC 9293: If the security/compartment in the segment does not exactly match the
        // RFC 9293: security/compartment in the TCB, send a reset:
        // RFC 9293: If there is an ACK,
        // RFC 9293: <SEQ=SEG.ACK><CTL=RST>
        // RFC 9293: Otherwise,
        // RFC 9293: <SEQ=0><ACK=SEG.SEQ+SEG.LEN><CTL=RST,ACK>
        // RFC 9293: If a reset was sent, discard the segment and return.
        // (not applicable for us)

        // RFC 9293: Fourth, check the SYN bit:
        // RFC 9293: This step should be reached only if the ACK is ok, or there is no ACK, and the
        // RFC 9293: segment did not contain a RST.
        if (seg.isSyn()) {
            // RFC 9293: If the SYN bit is on and the security/compartment is acceptable,
            // RFC 9293: then RCV.NXT is set to SEG.SEQ+1, IRS is set to SEG.SEQ.
            // synchronize receive state
            tcb.synchronizeState(seg);
            // RFC 9293: SND.UNA should be advanced to equal SEG.ACK (if there is an ACK),
            if (seg.isAck()) {
                // advance send state
                tcb.handleAcknowledgement(ctx, seg);
            }
            // RFC 9293: and any segments on the retransmission queue that are thereby acknowledged
            // RFC 9293: should be removed.
            // (this is done by the handleAcknowledgement call above)

            LOG.trace("{}[{}] TCB synchronized: {}", ctx.channel(), state, tcb);

            tcb.retransmissionQueue().segmentArrivesOnSynSentState(ctx, seg, tcb);

            if (tcb.synHasBeenAcknowledged()) {
                LOG.trace("{}[{}] Remote peer has ACKed our SYN and sent us its SYN `{}`. Handshake on our side is completed.", ctx.channel(), state, seg);
                // RFC 9293: If SND.UNA > ISS (our SYN has been ACKed), change the connection state
                // RFC 9293: to ESTABLISHED,
                changeState(ctx, ESTABLISHED);
                cancelUserTimer();

                // mss negotiation
                tcb.negotiateMss(ctx, seg);

                // RFC 9293: form an ACK segment
                // RFC 9293: <SEQ=SND.NXT><ACK=RCV.NXT><CTL=ACK>
                // RFC 9293: and send it. Data or controls that were queued for transmission MAY be
                // RFC 9293: included. Some TCP implementations suppress sending this segment when
                // RFC 9293: the received segment contains data that will anyways generate an
                // RFC 9293: acknowledgment in the later processing steps, saving this extra
                // RFC 9293: acknowledgment of the SYN from being sent.
                final Segment response = Segment.ack(tcb.sndNxt(), tcb.rcvNxt());
                LOG.trace("{}[{}] ACKnowlede the received segment with a `{}` so the remote peer can complete the handshake as well.", ctx.channel(), state, response);
                tcb.write(response);

                ctx.fireUserEventTriggered(new ConnectionHandshakeCompleted(tcb.sndNxt(), tcb.rcvNxt()));

                // ohne das funktioniert das direkte anhängen von Daten am ACK nicht...(weil SND.WND = 0 ist)
                tcb.updateSndWnd(ctx, seg);

                // TODO:
                //  RFC 9293: If there are other controls or text in the segment, then continue
                //  RFC 9293: processing at the sixth step under Section 3.10.7.4 where the URG bit
                //  RFC 9293: is checked;

                // RFC 9293: otherwise, return.
                return;
            }
            else {
                // RFC 9293: Otherwise, enter SYN-RECEIVED,
                changeState(ctx, SYN_RECEIVED);

                // RFC 9293: form a SYN,ACK segment
                // RFC 9293: <SEQ=ISS><ACK=RCV.NXT><CTL=SYN,ACK>
                // RFC 9293: and send it.
                final Segment response = Segment.synAck(tcb.iss(), tcb.rcvNxt());
                LOG.trace("{}[{}] Write `{}`.", ctx.channel(), state, response);
                tcb.writeWithout(response);

                // RFC 9293: Set the variables:
                // RFC 9293: SND.WND <- SEG.WND
                // RFC 9293: SND.WL1 <- SEG.SEQ
                // RFC 9293: SND.WL2 <- SEG.ACK
                tcb.updateSndWnd(ctx, seg);

                // TODO:
                //  RFC 9293: If there are other controls or text in the segment, queue them for
                //  RFC 9293: processing after the ESTABLISHED state has been reached,

                // RFC 9293: return.
                return;

                // RFC 9293: Note that it is legal to send and receive application data on SYN
                // RFC 9293: segments (this is the "text in the segment" mentioned above). There has
                // RFC 9293: been significant misinformation and misunderstanding of this topic
                // RFC 9293: historically. Some firewalls and security devices consider this
                // RFC 9293: suspicious. However, the capability was used in T/TCP [21] and is used
                // RFC 9293: in TCP Fast Open (TFO) [48], so is important for implementations and
                // RFC 9293: network devices to permit.
            }
        }

        // RFC 9293: Fifth, if neither of the SYN or RST bits is set,
        // RFC 9293: then drop the segment
        // (this is handled by handler's auto release of all arrived segments)
        // RFC 9293: and return.
    }

    @SuppressWarnings("java:S3776")
    private void segmentArrivesOnOtherStates(final ChannelHandlerContext ctx,
                                             final Segment seg) {
        ReferenceCountUtil.touch(seg, "segmentArrivesOnOtherStates " + seg.toString());

        // RFC 9293: First, check sequence number:

        switch (state) {
            case SYN_RECEIVED:
            case ESTABLISHED:
            case FIN_WAIT_1:
            case FIN_WAIT_2:
            case CLOSE_WAIT:
            case CLOSING:
            case LAST_ACK:
            case TIME_WAIT:
                // RFC 9293: Segments are processed in sequence. Initial tests on arrival are used to
                // RFC 9293: discard old duplicates, but further processing is done in SEG.SEQ order. If a
                // RFC 9293: segment's contents straddle the boundary between old and new, only the new
                // RFC 9293: parts are processed.

                // RFC 9293: In general, the processing of received segments MUST be implemented to
                // RFC 9293: aggregate ACK segments whenever possible (MUST-58). For example, if the TCP
                // RFC 9293: endpoint is processing a series of queued segments, it MUST process them all
                // RFC 9293: before sending any ACK segments (MUST-59).

                // RFC 9293: There are four cases for the acceptability test for an incoming segment:
                // RFC 9293: Segment Length Receive Window  Test
                // RFC 9293: 0              0               SEG.SEQ = RCV.NXT
                // RFC 9293: 0              >0              RCV.NXT =< SEG.SEQ < RCV.NXT+RCV.WND
                // RFC 9293: >0             0               not acceptable
                // RFC 9293: >0             >0              RCV.NXT =< SEG.SEQ < RCV.NXT+RCV.WND
                // RFC 9293:                                or
                // RFC 9293:                                RCV.NXT =< SEG.SEQ+SEG.LEN-1 < RCV.NXT+RCV.WND

                // RFC 9293: In implementing sequence number validation as described here, please note
                // RFC 9293: Appendix A.2.

                // RFC 9293: If the RCV.WND is zero, no segments will be acceptable, but special allowance
                // RFC 9293: should be made to accept valid ACKs, URGs, and RSTs.
                // check SEQ
                boolean acceptableSeg = tcb.isAcceptableSeg(seg);

                // RTTM
                acceptableSeg = tcb.retransmissionQueue().segmentArrivesOnOtherStates(ctx, seg, tcb, state, acceptableSeg);

                if (!acceptableSeg) {
                    // RFC 9293: If an incoming segment is not acceptable, an acknowledgment should be sent
                    // RFC 9293: in reply (unless the RST bit is set, if so drop the segment and return):
                    // RFC 9293: <SEQ=SND.NXT><ACK=RCV.NXT><CTL=ACK>
                    if (!seg.isRst()) {
                        final Segment response = Segment.ack(tcb.sndNxt(), tcb.rcvNxt());
                        LOG.trace("{}[{}] We got an unexpected SEG `{}`. Send an ACK `{}` for the SEG we actually expect.", ctx.channel(), state, seg, response);
                        tcb.write(response);
                    }

                    // RFC 9293: After sending the acknowledgment, drop the unacceptable segment and return.
                    // (this is handled by handler's auto release of all arrived segments)
                    return;

                    // TODO:
                    //  RFC 9293: Note that for the TIME-WAIT state, there is an improved algorithm
                    //  RFC 9293: described in [40] for handling incoming SYN segments that utilizes
                    //  RFC 9293: timestamps rather than relying on the sequence number check described
                    //  RFC 9293: here. When the improved algorithm is implemented, the logic above is not
                    //  RFC 9293: applicable for incoming SYN segments with Timestamp Options, received on a
                    //  RFC 9293: connection in the TIME-WAIT state.

                    // TODO:
                    //  RFC 9293: In the following it is assumed that the segment is the idealized segment
                    //  RFC 9293: that begins at RCV.NXT and does not exceed the window. One could tailor
                    //  RFC 9293: actual segments to fit this assumption by trimming off any portions that
                    //  RFC 9293: lie outside the window (including SYN and FIN) and only processing further
                    //  RFC 9293: if the segment then begins at RCV.NXT. Segments with higher beginning
                    //  RFC 9293: sequence numbers SHOULD be held for later processing (SHLD-31).
                }
        }

        // RFC 9293: Second, check the RST bit:

        // TODO:
        //  RFC 9293: RFC 5961 [9], Section 3 describes a potential blind reset attack and optional
        //  RFC 9293: mitigation approach. This does not provide a cryptographic protection (e.g.,
        //  RFC 9293: as in IPsec or TCP-AO) but can be applicable in situations described in
        //  RFC 9293: RFC 5961. For stacks implementing the protection described in RFC 5961, the
        //  RFC 9293: three checks below apply; otherwise, processing for these states is indicated
        //  RFC 9293: further below.
        //  RFC 9293:
        //  RFC 9293: 1)    If the RST bit is set and the sequence number is outside the current
        //  RFC 9293:       receive window, silently drop the segment.
        //  RFC 9293: 2)    If the RST bit is set and the sequence number exactly matches the next
        //  RFC 9293:       expected sequence number (RCV.NXT), then TCP endpoints MUST reset the
        //  RFC 9293:       connection in the manner prescribed below according to the connection
        //  RFC 9293:       state.
        //  RFC 9293: 3)    If the RST bit is set and the sequence number does not exactly match the
        //  RFC 9293:       next expected sequence value, yet is within the current receive window,
        //  RFC 9293:       TCP endpoints MUST send an acknowledgment (challenge ACK):
        //  RFC 9293:       <SEQ=SND.NXT><ACK=RCV.NXT><CTL=ACK>
        //  RFC 9293:       After sending the challenge ACK, TCP endpoints MUST drop the
        //  RFC 9293:       unacceptable segment and stop processing the incoming packet further.
        //  RFC 9293:       Note that RFC 5961 and Errata ID 4772 [99] contain additional
        //  RFC 9293:       considerations for ACK throttling in an implementation.

        switch (state) {
            case SYN_RECEIVED:
                if (seg.isRst()) {
                    // RFC 9293: If the RST bit is set,
                    if (!activeOpen) {
                        // RFC 9293: If this connection was initiated with a passive OPEN (i.e.,
                        // RFC 9293: came from the LISTEN state), then return this connection to
                        // RFC 9293: LISTEN state
                        // peer is no longer interested in a connection. Go back to previous state
                        LOG.trace("{}[{}] We got `{}`. Remote peer is not longer interested in a connection. We're going back to the LISTEN state.", ctx.channel(), state, seg);
                        changeState(ctx, LISTEN);
                        // RFC 9293: and return. The user need not be informed.
                        return;
                    }
                    else {
                        // RFC 9293: If this connection was initiated with an active OPEN (i.e.,
                        // RFC 9293: came from SYN-SENT state), then the connection was refused;
                        // RFC 9293: signal the user "connection refused".
                        // connection has been refused by remote
                        LOG.trace("{}[{}] We got `{}`. Connection has been refused by remote peer.", ctx.channel(), state, seg);
                        ctx.fireExceptionCaught(CONNECTION_REFUSED_EXCEPTION);
                    }

                    // TODO:
                    //  RFC 9293: In either case, the retransmission queue should be flushed.

                    if (activeOpen) {
                        // RFC 9293: And in the active OPEN case, enter the CLOSED state
                        changeState(ctx, CLOSED);
                        ctx.close();
                        // RFC 9293: and delete the TCB,
                        deleteTcb();
                        // RFC 9293: and return.
                        return;
                    }
                }
                break;

            case ESTABLISHED:
            case FIN_WAIT_1:
            case FIN_WAIT_2:
            case CLOSE_WAIT:
                if (seg.isRst()) {
                    // TODO:
                    //  RFC 9293: If the RST bit is set, then any outstanding RECEIVEs and SEND
                    //  RFC 9293: should receive "reset" responses. All segment queues should be
                    //  RFC 9293: flushed.
                    LOG.trace("{}[{}] We got `{}`. Remote peer is not longer interested in a connection. Close channel.", ctx.channel(), state, seg);
                    //  RFC 9293: Users should also receive an unsolicited general
                    //  RFC 9293: "connection reset" signal.
                    ctx.fireExceptionCaught(CONNECTION_RESET_EXCEPTION);
                    //  RFC 9293: Enter the CLOSED state, delete the TCB
                    changeState(ctx, CLOSED);
                    ctx.close();
                    //  RFC 9293: delete the TCB,
                    deleteTcb();
                    //  RFC 9293: and return.
                    return;
                }
                break;

            case CLOSING:
            case LAST_ACK:
            case TIME_WAIT:
                if (seg.isRst()) {
                    // RFC 9293: If the RST bit is set, then enter the CLOSED state,
                    changeState(ctx, CLOSED);
                    ctx.close();
                    LOG.trace("{}[{}] We got `{}`. Close channel.", ctx.channel(), state, seg);
                    // RFC 9293: delete the TCB,
                    deleteTcb();
                    // RFC 9293: and return.
                    return;
                }
        }

        // TODO:
        //  RFC 9293: Third, check security:
        switch (state) {
            case SYN_RECEIVED:
                // TODO:
                //  RFC 9293: If the security/compartment in the segment does not exactly match the
                //  RFC 9293: security/compartment in the TCB, then send a reset and return.
                break;

            case ESTABLISHED:
            case FIN_WAIT_1:
            case FIN_WAIT_2:
            case CLOSE_WAIT:
            case CLOSING:
            case LAST_ACK:
            case TIME_WAIT:
                // TODO:
                //  RFC 9293: If the security/compartment in the segment does not exactly match the
                //  RFC 9293: security/compartment in the TCB, then send a reset; any outstanding
                //  RFC 9293: RECEIVEs and SEND should receive "reset" responses. All segment queues
                //  RFC 9293: should be flushed. Users should also receive an unsolicited general
                //  RFC 9293: "connection reset" signal. Enter the CLOSED state, delete the TCB,
                //  RFC 9293: and return.
        }

        // TODO:
        //  RFC 9293: Note this check is placed following the sequence check to prevent a segment
        //  RFC 9293: from an old connection between these port numbers with a different security
        //  RFC 9293: from causing an abort of the current connection.

        // RFC 9293: Fourth, check the SYN bit:
        if (seg.isSyn()) {
            switch (state) {
                case SYN_RECEIVED:
                    if (!activeOpen) {
                        // RFC 9293: If the connection was initiated with a passive OPEN, then
                        // RFC 9293: return this connection to the LISTEN state and return.
                        LOG.trace("{}[{}] We got an additional `{}`. As this connection was initiated with a passive OPEN return to LISTEN state.", ctx.channel(), state, seg);
                        changeState(ctx, LISTEN);
                        return;
                    }
                    // RFC 9293: Otherwise, handle per the directions for synchronized states below.

                case ESTABLISHED:
                case FIN_WAIT_1:
                case FIN_WAIT_2:
                case CLOSE_WAIT:
                case CLOSING:
                case LAST_ACK:
                case TIME_WAIT:
                    if (state.synchronizedConnection()) {
                        // FIXME:
                        // RFC 9293: If the SYN bit is set in these synchronized states, it may be
                        // RFC 9293: either a legitimate new connection attempt (e.g., in the case of
                        // RFC 9293: TIME-WAIT), an error where the connection should be reset, or the
                        // RFC 9293: result of an attack attempt, as described in RFC 5961 [9].For the
                        // RFC 9293: TIME-WAIT state, new connections can be accepted if the Timestamp
                        // RFC 9293: Option is used and meets expectations (per [40]). For all other
                        // RFC 9293: cases, RFC 5961 provides a mitigation with applicability to some
                        // RFC 9293: situations, though there are also alternatives that offer
                        // RFC 9293: cryptographic protection (see Section 7). RFC 5961 recommends that
                        // RFC 9293: in these synchronized states, if the SYN bit is set, irrespective
                        // RFC 9293: of the sequence number, TCP endpoints MUST send a "challenge ACK"
                        // RFC 9293: to the remote peer:
                        // RFC 9293: <SEQ=SND.NXT><ACK=RCV.NXT><CTL=ACK>
                        final Segment response = Segment.ack(tcb.sndNxt(), tcb.rcvNxt());
                        LOG.trace("{}[{}] We got `{}` while we're in a synchronized state. Peer might be crashed. Send challenge ACK `{}`.", ctx.channel(), state, seg, response);
                        tcb.write(response);

                        // RFC 9293: After sending the acknowledgment, TCP implementations MUST
                        // RFC 9293: drop the unacceptable segment
                        // (this is handled by handler's auto release of all arrived segments)
                        // RFC 9293: and stop processing further.
                        return;
                    }

                    // RFC 9293: Note that RFC 5961 and Errata ID 4772 [99] contain additional ACK
                    // RFC 9293: throttling notes for an implementation.

                    // RFC 9293: For implementations that do not follow RFC 5961, the original
                    // RFC 9293: behavior described in RFC 793 follows in this paragraph. If the SYN
                    // RFC 9293: is in the window it is an error: send a reset, any outstanding
                    // RFC 9293: RECEIVEs and SEND should receive "reset" responses, all segment
                    // RFC 9293: queues should be flushed, the user should also receive an
                    // RFC 9293: unsolicited general "connection reset" signal, enter the CLOSED
                    // RFC 9293: state, delete the TCB, and return.
                    // RFC 9293: If the SYN is not in the window, this step would not be reached and
                    // RFC 9293: an ACK would have been sent in the first step (sequence number
                    // RFC 9293: check).
            }
        }

        // RFC 9293: Fifth, check the ACK field:
        if (!seg.isAck()) {
            // RFC 9293: if the ACK bit is off,
            // RFC 9293: drop the segment
            // (this is handled by handler's auto release of all arrived segments)
            LOG.trace("{}[{}] Got `{}` with off ACK bit. Drop segment and return.", ctx.channel(), state, seg);
            // RFC 9293: and return
            return;
        }
        else {
            // RFC 9293: if the ACK bit is on,

            // RFC 9293: RFC 5961 [9], Section 5 describes a potential blind data injection attack,
            // RFC 9293: and mitigation that implementations MAY choose to include (MAY-12). TCP
            // RFC 9293: stacks that implement RFC 5961 MUST add an input check that the ACK value
            // RFC 9293: is acceptable only if it is in the range of
            // RFC 9293: ((SND.UNA - MAX.SND.WND) =< SEG.ACK =< SND.NXT). All incoming segments
            // RFC 9293: whose ACK value doesn't satisfy the above condition MUST be discarded and
            // RFC 9293: an ACK sent back. The new state variable MAX.SND.WND is defined as the
            // RFC 9293: largest window that the local sender has ever received from its peer
            // RFC 9293: (subject to window scaling) or may be hard-coded to a maximum permissible
            // RFC 9293: window value. When the ACK value is acceptable, the per-state processing
            // RFC 9293: below applies:

            final boolean acceptableAck = tcb.isAcceptableAck(seg);

            // FIXME:
            tcb.lastAdvertisedWindow = seg.window();

            switch (state) {
                case SYN_RECEIVED:
                    if (tcb.isAckOurSynOrFin(seg)) {
                        LOG.trace("{}[{}] Remote peer ACKnowledge `{}` receivable of our SYN. As we've already received his SYN the handshake is now completed on both sides.", ctx.channel(), state, seg);

                        cancelUserTimer();

                        // RFC 9293: If SND.UNA < SEG.ACK =< SND.NXT, then enter ESTABLISHED state
                        changeState(ctx, ESTABLISHED);

                        // RFC 9293: and continue processing with the variables below set to:
                        // RFC 9293: SND.WND <- SEG.WND
                        // RFC 9293: SND.WL1 <- SEG.SEQ
                        // RFC 9293: SND.WL2 <- SEG.ACK
                        tcb.updateSndWnd(ctx, seg);
                        ctx.fireUserEventTriggered(new ConnectionHandshakeCompleted(tcb.sndNxt(), tcb.rcvNxt()));

                        if (!acceptableAck) {
                            // RFC 9293: If the segment acknowledgment is not acceptable, form a
                            // RFC 9293: reset segment
                            // RFC 9293: <SEQ=SEG.ACK><CTL=RST>
                            // RFC 9293: and send it.
                            final Segment response = Segment.rst(seg.ack());
                            LOG.trace("{}[{}] SEG `{}` is not an acceptable ACK. Send RST `{}` and drop received SEG.", ctx.channel(), state, seg, response);
                            tcb.write(response);
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

                    // RFC 9293: In addition to the processing for the ESTABLISHED state,
                    if (establishedProcessing(ctx, seg, acceptableAck)) {
                        return;
                    }

                    if (ackOurFin) {
                        // RFC 9293: if the FIN segment is now acknowledged, then enter FIN-WAIT-2
                        // RFC 9293: and continue processing in that state.
                        // our FIN has been acknowledged
                        changeState(ctx, FIN_WAIT_2);
                    }
                    break;

                case FIN_WAIT_2:
                    // RFC 9293: In addition to the processing for the ESTABLISHED state,
                    if (establishedProcessing(ctx, seg, acceptableAck)) {
                        return;
                    }

                    // TODO:
                    //  RFC 9293: if the retransmission queue is empty, the user's CLOSE can be
                    //  RFC 9293: acknowledged ("ok") but do not delete the TCB.

                    break;

                case CLOSE_WAIT:
                    // RFC 9293: Do the same processing as for the ESTABLISHED state.
                    if (establishedProcessing(ctx, seg, acceptableAck)) {
                        return;
                    }
                    break;

                case CLOSING:
                    final boolean ackOurFin2 = tcb.isAckOurSynOrFin(seg);
                    // RFC 9293: In addition to the processing for the ESTABLISHED state,
                    if (establishedProcessing(ctx, seg, acceptableAck)) {
                        return;
                    }

                    if (ackOurFin2) {
                        // RFC 9293: if the ACK acknowledges our FIN, then enter the TIME-WAIT
                        // RFC 9293: state;
                        changeState(ctx, TIME_WAIT);

                        // start the time-wait timer
                        startTimeWaitTimer(ctx, userCallFuture);

                        // turn off the other timers
                        tcb.retransmissionQueue().cancelRetransmissionTimer();
                        cancelUserTimer();
                    }
                    else {
                        // RFC 9293: otherwise, ignore the segment.
                        LOG.trace("{}[{}] The received ACKnowledged `{}` does not match our sent FIN. Ignore it.", ctx.channel(), state, seg);
                        return;
                    }
                    break;

                case LAST_ACK:
                    // RFC 9293: The only thing that can arrive in this state is an acknowledgment
                    // FIXME: check if ACKed
                    //  RFC 9293: of our FIN. If our FIN is now acknowledged,
                    LOG.trace("{}[{}] Our sent FIN has been ACKnowledged by `{}`. Close sequence done.", ctx.channel(), state, seg);
                    // RFC 9293: delete the TCB,
                    deleteTcb();
                    // RFC 9293: enter the CLOSED state,
                    changeState(ctx, CLOSED);
                    if (userCallFuture != null) {
                        ctx.close(userCallFuture);
                    }
                    else {
                        ctx.close();
                    }
                    // RFC 9293: and return.
                    return;

                case TIME_WAIT:
                    //  RFC 9293: The only thing that can arrive in this state is a retransmission
                    //  RFC 9293: of the remote FIN. Acknowledge it,
                    final Segment response = Segment.ack(tcb.sndNxt(), tcb.rcvNxt());
                    LOG.trace("{}[{}] Write `{}`.", ctx.channel(), state, response);
                    tcb.write(response);

                    // FIXME:
                    //  RFC 9293: and restart the 2 MSL timeout.
            }
        }

        // RFC 9293: Sixth, check the URG bit:
        // (URG not supported! It is only kept by TCP for legacy reasons, see SHLD-13)

        // RFC 9293: Seventh, process the segment text:
        if (seg.content().readableBytes() > 0) {
            switch (state) {
                case ESTABLISHED:
                case FIN_WAIT_1:
                case FIN_WAIT_2:
                    // RFC 9293: Once in the ESTABLISHED state, it is possible to deliver segment
                    // RFC 9293: data to user RECEIVE buffers. Data from segments can be moved into
                    // RFC 9293: buffers until either the buffer is full or the segment is empty.

                    // TODO:
                    //  RFC 9293: If the segment empties and carries a PUSH flag, then the user is
                    //  RFC 9293: informed, when the buffer is returned, that a PUSH has been
                    //  RFC 9293: received.

                    // RFC 9293: When the TCP endpoint takes responsibility for delivering the data
                    // RFC 9293: to the user, it must also acknowledge the receipt of the data.

                    // RFC 9293: Once the TCP endpoint takes responsibility for the data, it
                    // RFC 9293: advances RCV.NXT over the data accepted, and adjusts RCV.WND as
                    // RFC 9293: appropriate to the current buffer availability. The total of
                    // RFC 9293: RCV.NXT and RCV.WND should not be reduced.

                    // RFC 9293: A TCP implementation MAY send an ACK segment acknowledging RCV.NXT
                    // RFC 9293: when a valid segment arrives that is in the window but not at the
                    // RFC 9293: left window edge (MAY-13).

                    // RFC 9293: Please note the window management suggestions in Section 3.8.

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

                    // RFC 9293: Send an acknowledgment of the form:
                    // RFC 9293: <SEQ=SND.NXT><ACK=RCV.NXT><CTL=ACK>

                    // RFC 9293: This acknowledgment should be piggybacked on a segment being
                    // RFC 9293: transmitted if possible without incurring undue delay.
                    // (this is done by TCB/OutgoingSegmentQueue)

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

                case CLOSE_WAIT:
                case CLOSING:
                case LAST_ACK:
                case TIME_WAIT:
                    // RFC 9293: This should not occur since a FIN has been received from the remote
                    // RFC 9293: side. Ignore the segment text.
                    LOG.trace("{}[{}] Got `{}`. This should not occur. Ignore the segment text.", ctx.channel(), state, seg);
            }
        }

        // RFC 9293: Eighth, check the FIN bit:
        if (seg.isFin()) {
            switch (state) {
                case CLOSED:
                case LISTEN:
                case SYN_SENT:
                    // RFC 9293: Do not process the FIN if the state is CLOSED, LISTEN, or SYN-SENT
                    // RFC 9293: since the SEG.SEQ cannot be validated; drop the segment and return.
                    // RFC 9293: we cannot validate SEG.SEQ.
                    // RFC 9293: drop the segment
                    // (this is handled by handler's auto release of all arrived segments)
                    return;
            }

            // RFC 9293: If the FIN bit is set, signal the user "connection closing"
            // signal user connection closing
            ctx.fireUserEventTriggered(HANDSHAKE_CLOSING_EVENT);
            // TODO:
            //  RFC 9293: and return any pending RECEIVEs with same message,
            // RFC 9293: advance RCV.NXT over the FIN,
            // RFC 9293: and send an acknowledgment for the FIN.
            // RFC 9293: Note that FIN implies PUSH for any segment text not yet delivered to the user.

            // advance receive state
            tcb.receiveBuffer().receive(ctx, tcb, seg);

            // send ACK for the FIN
            final Segment response = Segment.ack(tcb.sndNxt(), tcb.rcvNxt());
            LOG.trace("{}[{}] Got CLOSE request `{}` from remote peer. ACKnowledge receival with `{}`.", ctx.channel(), state, seg, response);
            tcb.write(response);

            switch (state) {
                case SYN_RECEIVED:
                case ESTABLISHED:
                    // RFC 9293: Enter the CLOSE-WAIT state.
                    changeState(ctx, CLOSE_WAIT);
                    break;

                case FIN_WAIT_1:
                    if (tcb.isAckOurSynOrFin(seg)) {
                        // RFC 9293: If our FIN has been ACKed (perhaps in this segment),
                        LOG.trace("{}[{}] Our FIN has been ACKnowledged. Close channel.", ctx.channel(), state, seg);

                        // RFC 9293: then enter TIME-WAIT,
                        changeState(ctx, TIME_WAIT);

                        // RFC 9293: start the time-wait timer,
                        startTimeWaitTimer(ctx, null);

                        // RFC 9293: turn off the other timers;
                        tcb.retransmissionQueue().cancelRetransmissionTimer();
                        cancelUserTimer();
                    }
                    else {
                        // RFC 9293: otherwise, enter the CLOSING state.
                        // RFC 9293: our FIN has been acknowledged
                        changeState(ctx, CLOSING);
                    }
                    break;

                case FIN_WAIT_2:
                    // RFC 9293: Enter the TIME-WAIT state.
                    changeState(ctx, TIME_WAIT);

                    // RFC 9293: Start the time-wait timer,
                    startTimeWaitTimer(ctx, userCallFuture);

                    // RFC 9293: turn off the other timers;
                    tcb.retransmissionQueue().cancelRetransmissionTimer();
                    cancelUserTimer();
                    break;

                case CLOSE_WAIT:
                case CLOSING:
                case LAST_ACK:
                    // RFC 9293: remain in the state.
                    break;

                case TIME_WAIT:
                    // FIXME:
                    //  RFC 9293: Remain in the TIME-WAIT state. Restart the 2 MSL time-wait timeout.
                    break;
            }
        }

        // RFC 9293: and return.
        return;
    }

    private void unexpectedSegment(final ChannelHandlerContext ctx,
                                   final Segment seg) {
        LOG.error("{}[{}] Got unexpected segment `{}`.", ctx.channel(), state, seg);
    }

    private boolean establishedProcessing(final ChannelHandlerContext ctx,
                                          final Segment seg,
                                          final boolean acceptableAck) {
        // TODO:
        //  RFC 9293: If SND.UNA < SEG.ACK =< SND.NXT, then set SND.UNA <- SEG.ACK.
        //  RFC 9293: Any segments on the retransmission queue that are thereby entirely
        //  RFC 9293: acknowledged are removed.
        //  RFC 9293: Users should receive positive acknowledgments for buffers that have been SENT
        //  RFC 9293: and fully acknowledged (i.e., SEND buffer should be returned with "ok"
        //  RFC 9293: response).
        //  RFC 9293: If the ACK is a duplicate (SEG.ACK =< SND.UNA), it can be ignored.
        //  RFC 9293: If the ACK acks something not yet sent (SEG.ACK > SND.NXT),
        //  RFC 9293: then send an ACK,
        //  RFC 9293: drop the segment,
        //  RFC 9293: and return.

        // update send window
        if (lessThanOrEqualTo(tcb.sndUna(), seg.ack()) && lessThanOrEqualTo(seg.ack(), tcb.sndNxt())) {
            // RFC 9293: If SND.UNA =< SEG.ACK =< SND.NXT, the send window should be updated.
            if (lessThan(tcb.sndWl1(), seg.seq()) || (tcb.sndWl1() == seg.seq() && lessThanOrEqualTo(tcb.sndWl2(), seg.ack()))) {
                // RFC 9293: If (SND.WL1 < SEG.SEQ or (SND.WL1 = SEG.SEQ and SND.WL2 =< SEG.ACK)),
                // RFC 9293: set SND.WND <- SEG.WND, set SND.WL1 <- SEG.SEQ, and
                // RFC 9293: set SND.WL2 <- SEG.ACK.
                tcb.updateSndWnd(ctx, seg);

                // RFC 9293: Note that SND.WND is an offset from SND.UNA, that SND.WL1 records the
                // RFC 9293: sequence number of the last segment used to update SND.WND, and that
                // RFC 9293: SND.WL2 records the acknowledgment number of the last segment used to
                // RFC 9293: update SND.WND. The check here prevents using old segments to update
                // RFC 9293: the window.
            }
        }

        final boolean duplicateAck = tcb.isDuplicateAck(seg);
        if (acceptableAck) {
            // advance send state
            final long ackedBytes = tcb.handleAcknowledgement(ctx, seg);
            if (ackedBytes > 0) {
                tcb.resetDuplicateAcks(ctx, seg, ackedBytes);
            }
        }
        if (duplicateAck) {
            if (false) {
                SackOption sackOption = (SackOption) seg.options().get(SACK);
                if (sackOption != null) {
                    // retransmit?
//                tcb.sendBuffer();
//                System.out.println();
                }
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

    /*
     * Timeouts
     */

    private void startUserTimer(final ChannelHandlerContext ctx,
                                final ChannelPromise promise) {
        if (userTimeout.toMillis() > 0) {
            // RFC 9293: If a timeout is specified, the current user timeout for this connection is
            // RFC 9293: changed to the new one.
            if (userTimeoutTimer != null) {
                userTimeoutTimer.cancel(false);
                userTimeoutTimer = null;
            }

            userTimeoutTimer = ctx.executor().schedule(() -> userTimeout(ctx, promise), userTimeout.toMillis(), MILLISECONDS);
        }
    }

    private void cancelUserTimer() {
        if (userTimeoutTimer != null) {
            userTimeoutTimer.cancel(false);
            userTimeoutTimer = null;
        }
    }

    private void startTimeWaitTimer(final ChannelHandlerContext ctx,
                                    final ChannelPromise promise) {
        // RFC 9293: When a connection is closed actively, it MUST linger in the TIME-WAIT state for
        // RFC 9293: a time 2xMSL (Maximum Segment Lifetime) (MUST-13)
        ctx.executor().schedule(() -> timeWaitTimeout(ctx, promise), msl.multipliedBy(2).toMillis(), MILLISECONDS);
    }

    /**
     * USER TIMEOUT event as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.8">RFC
     * 9293, Section 3.10.8</a>.
     */
    private void userTimeout(final ChannelHandlerContext ctx,
                             final ChannelPromise promise) {
        userTimeoutTimer = null;

        // RFC 9293: For any state if the user timeout expires,
        LOG.trace("{}[{}] USER TIMEOUT expired after {}ms. Close channel.", ctx.channel(), userTimeout.toMillis());
        // RFC 9293: flush all queues,
        // (this is done by deleteTcb)
        // RFC 9293: signal the user "error: connection aborted due to user timeout" in
        // RFC 9293: general and for any outstanding calls,
        final ConnectionHandshakeException cause = new ConnectionHandshakeException("User timeout for OPEN user call expired after " + userTimeout.toMillis() + "ms. Close channel.");
        if (promise != null) {
            promise.tryFailure(cause);
        }
        else {
            ctx.fireExceptionCaught(cause);
        }
        // RFC 9293: delete the TCB,
        deleteTcb();
        // RFC 9293: enter the CLOSED state,
        changeState(ctx, CLOSED);
        ctx.close();
        // RFC 9293: and return.
    }

    /**
     * TIME-WAIT TIMEOUT event as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.8">RFC
     * 9293, Section 3.10.8</a>.
     */
    private void timeWaitTimeout(final ChannelHandlerContext ctx,
                                 final ChannelPromise promise) {
        LOG.trace("{}[{}] TIME-WAIT TIMEOUT expired after {}ms. Close channel.", ctx.channel(), msl.multipliedBy(2).toMillis());

        // RFC 9293: If the time-wait timeout expires on a connection,
        // RFC 9293: delete the TCB,
        deleteTcb();

        // RFC 9293: enter the CLOSED state,
        changeState(ctx, CLOSED);
        if (promise != null) {
            promise.trySuccess();
        }

        // RFC 9293: and return.
        return;
    }
}
