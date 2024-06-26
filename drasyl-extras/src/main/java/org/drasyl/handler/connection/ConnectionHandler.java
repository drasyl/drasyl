/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.handler.connection.SegmentOption.TimestampsOption;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Boolean.FALSE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.connection.Segment.ACK;
import static org.drasyl.handler.connection.Segment.FIN;
import static org.drasyl.handler.connection.Segment.PSH;
import static org.drasyl.handler.connection.Segment.RST;
import static org.drasyl.handler.connection.Segment.SEG_HDR_SIZE;
import static org.drasyl.handler.connection.Segment.SYN;
import static org.drasyl.handler.connection.Segment.add;
import static org.drasyl.handler.connection.Segment.advanceSeq;
import static org.drasyl.handler.connection.Segment.greaterThan;
import static org.drasyl.handler.connection.Segment.lessThan;
import static org.drasyl.handler.connection.Segment.lessThanOrEqualTo;
import static org.drasyl.handler.connection.Segment.sub;
import static org.drasyl.handler.connection.SegmentOption.MAXIMUM_SEGMENT_SIZE;
import static org.drasyl.handler.connection.SegmentOption.TIMESTAMPS;
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
import static org.drasyl.handler.connection.TransmissionControlBlock.MAX_PORT;
import static org.drasyl.util.NumberUtil.max;
import static org.drasyl.util.NumberUtil.min;
import static org.drasyl.util.Preconditions.requireInRange;

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
 * implemented as well.
 * <p>
 * The <a href="https://www.rfc-editor.org/rfc/rfc9293.html#nagle">Nagle algorithm</a> is used as
 * "Silly Window Syndrome" avoidance algorithm. To improve performance of recovering from multiple
 * losses, the <a href="https://www.rfc-editor.org/rfc/rfc2018">RFC 2018 TCP Selective
 * Acknowledgment Options</a> is used in conjunction with <a href=""></a>.
 * <p>
 * The handler can be configured to perform an active or passive OPEN process.
 */
@SuppressWarnings({
        "java:S125",
        "java:S128",
        "java:S131",
        "java:S138",
        "java:S1066",
        "java:S1142",
        "java:S1151",
        "java:S1192",
        "java:S1541",
        "java:S1845",
        "java:S1871",
        "java:S3626",
        "java:S3776",
        "java:S6541",
        "UnnecessaryReturnStatement",
        "IfStatementWithIdenticalBranches",
        "DuplicateBranchesInSwitch",
        "StatementWithEmptyBody",
        "ConstantValue"
})
public class ConnectionHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionHandler.class);
    private final int requestedLocalPort;
    private final int remotePort;
    private final ConnectionConfig config;
    TransmissionControlBlock tcb;
    ScheduledFuture<?> userTimer;
    ScheduledFuture<?> retransmissionTimer;
    ScheduledFuture<?> timeWaitTimer;
    ScheduledFuture<?> zeroWindowProber;
    private ChannelPromise establishedPromise;
    private boolean userCallReceiveAlreadyEnqueued;
    private boolean userCallCloseAlreadyEnqueued;
    private ChannelPromise closedPromise;
    private boolean readPending;
    private ChannelHandlerContext ctx;
    private ChannelPromise segmentizedFuture;
    private long segmentizedRemainingBytes;

    @SuppressWarnings("java:S107")
    ConnectionHandler(final int requestedLocalPort,
                      final int remotePort,
                      final ConnectionConfig config,
                      final TransmissionControlBlock tcb,
                      final ScheduledFuture<?> userTimer,
                      final ScheduledFuture<?> retransmissionTimer,
                      final ScheduledFuture<?> timeWaitTimer,
                      final ChannelPromise establishedPromise,
                      final boolean userCallReceiveAlreadyEnqueued,
                      final boolean userCallCloseAlreadyEnqueued,
                      final ChannelPromise closedPromise,
                      final ChannelHandlerContext ctx) {
        this.requestedLocalPort = requireInRange(requestedLocalPort, 0, MAX_PORT);
        this.remotePort = requireInRange(remotePort, 0, MAX_PORT);
        this.config = requireNonNull(config);
        this.tcb = tcb;
        this.userTimer = userTimer;
        this.retransmissionTimer = retransmissionTimer;
        this.timeWaitTimer = timeWaitTimer;
        this.establishedPromise = establishedPromise;
        this.closedPromise = closedPromise;
        this.ctx = ctx;
        this.userCallReceiveAlreadyEnqueued = userCallReceiveAlreadyEnqueued;
        this.userCallCloseAlreadyEnqueued = userCallCloseAlreadyEnqueued;
    }

    public ConnectionHandler(final int requestedLocalPort,
                             final int remotePort,
                             final ConnectionConfig config) {
        this(requestedLocalPort, remotePort, config, null, null, null, null, null, false, false, null, null);
    }

    public ConnectionHandler(final int requestedLocalPort,
                             final int remotePort) {
        this(requestedLocalPort, remotePort, ConnectionConfig.newBuilder().build());
    }

    public ConnectionHandler(final int remotePort,
                             final ConnectionConfig config) {
        this(0, remotePort, config);
    }

    public ConnectionHandler() {
        this(0, ConnectionConfig.DEFAULT);
    }

    @Override
    public String toString() {
        return "ConnectionHandler{" +
                ", tcb=" + tcb +
                '}';
    }

    /*
     * Handler Events
     */

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.ctx = ctx;
        if (ctx.channel().isActive()) {
            initHandler(ctx);
        }
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        // release all resources/timers
        deleteTcb();
        establishedPromise.tryFailure(new ConnectionClosingException(ctx.channel()));
        ctx.channel().closeFuture().addListener(new PromiseNotifier<>(false, closedPromise));
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
        if (tcb != null) {
            tcb.pushAndSegmentizeData(ctx);
        }
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
        if (tcb != null) {
            tcb.sendBuffer().fail(new ConnectionClosingException(ctx.channel()));
            tcb.retransmissionQueue().release();
        }
        deleteTcb();
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof Segment) {
            segmentArrives(ctx, (Segment) msg);
        }
        else {
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        if (tcb != null) {
            tcb.flush(ctx);
        }

        ctx.read();
    }

    /*
     * User Calls
     */

    /**
     * OPEN call as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.1">RFC
     * 9293, Section 3.10.1</a>.
     */
    void userCallOpen(final ChannelHandlerContext ctx) {
        assert ctx.executor().inEventLoop();
        LOG.trace("{} OPEN call received.", ctx.channel());

        switch (state()) {
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
                // (not applicable to us)

                if (!config.activeOpen()) {
                    // RFC 9293: If passive, enter the LISTEN state
                    LOG.trace("{} Handler is configured to perform passive OPEN process. Go to {} state and wait for remote peer to initiate OPEN process.", ctx.channel(), LISTEN);
                    changeState(ctx, LISTEN);

                    tcb.ensureLocalPortIsSelected(requestedLocalPort);

                    // RFC 9293: and return.
                    ctx.read();

                    return;
                }
                else {
                    LOG.trace("{} Handler is configured to perform active OPEN process. ChannelActive event acts as implicit OPEN call.", ctx.channel());

                    tcb.ensureLocalPortIsSelected(requestedLocalPort);
                    tcb.remotePort(remotePort);

                    // RFC 9293: If active and the remote socket is unspecified, return "error: remote
                    // RFC 9293: socket unspecified";
                    // (not applicable to us)

                    // RFC 9293: if active and the remote socket is specified, issue a SYN segment.
                    // RFC 9293: An initial send sequence number (ISS) is selected.
                    tcb.selectIss();

                    // RFC 9293: A SYN segment of the form <SEQ=ISS><CTL=SYN> is sent.
                    // RFC 7323: Send a <SYN> segment of the form:
                    // RFC 7323: <SEQ=ISS><CTL=SYN><TSval=Snd.TSclock>
                    // (timestamps option is automatically added by formSegment)
                    final Segment seg = formSegment(ctx, tcb.iss(), SYN);
                    LOG.trace("{} Initiate OPEN process by sending `{}`.", ctx.channel(), seg);
                    tcb.sendAndFlush(ctx, seg);

                    // RFC 9293: Set SND.UNA to ISS, SND.NXT to ISS+1,
                    tcb.initSndUnaSndNxt();

                    // RFC 9293: enter SYN-SENT state,
                    changeState(ctx, SYN_SENT);

                    // RFC 9293: and return.
                    ctx.read();

                    return;

                    // RFC 9293: If the caller does not have access to the local socket specified,
                    // RFC 9293: return "error: connection illegal for this process". If there is no
                    // RFC 9293: room to create a new connection, return "error: insufficient
                    // RFC 9293: resources".
                    // (not applicable to us)
                }

            case LISTEN:
                // RFC 9293: If the OPEN call is active and the remote socket is specified, then
                // RFC 9293: change the connection from passive to active,
                LOG.trace("{} Handler is configured to perform passive OPEN process. Got OPEN call. Switch to active OPEN.", ctx.channel(), LISTEN);

                tcb.ensureLocalPortIsSelected(requestedLocalPort);
                tcb.remotePort(remotePort);

                // RFC 9293: select an ISS.
                tcb.selectIss();

                // RFC 9293: Send a SYN segment,
                final Segment seg = formSegment(ctx, tcb.iss(), SYN);
                LOG.trace("{} Initiate OPEN process by sending `{}`.", ctx.channel(), seg);
                tcb.sendAndFlush(ctx, seg);

                // RFC 9293: set SND.UNA to ISS, SND.NXT to ISS+1.
                tcb.initSndUnaSndNxt();

                // RFC 9293: Enter SYN-SENT state.
                changeState(ctx, SYN_SENT);

                // RFC 9293: Data associated with SEND may be sent with SYN segment or queued for
                // RFC 9293: transmission after entering ESTABLISHED state. The urgent bit if
                // RFC 9293: requested in the command must be sent with the data segments sent as a
                // RFC 9293: result of this command. If there is no room to queue the request,
                // RFC 9293: respond with "error: insufficient resources". If the remote socket was
                // RFC 9293: not specified, then return "error: remote socket unspecified".
                // (not applicable to us)

                ctx.read();

                return;

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
                throw new ConnectionAlreadyExistsException(ctx.channel());
        }
    }

    /**
     * SEND call as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.2">RFC
     * 9293, Section 3.10.2</a>.
     */
    private void userCallSend(final ChannelHandlerContext ctx,
                              final ByteBuf data,
                              final ChannelPromise promise) {
        LOG.trace("{} SEND call received.", ctx.channel());

        switch (state()) {
            case CLOSED:
                // RFC 9293: If the user does not have access to such a connection, then return
                // RFC 9293: "error: connection illegal for this process".
                // (not applicable to us)

                // RFC 9293: Otherwise, return "error: connection does not exist".
                LOG.trace("{} Connection is already closed. Reject data `{}`.", ctx.channel(), data);
                promise.tryFailure(new ConnectionDoesNotExistException(ctx.channel()));
                ReferenceCountUtil.safeRelease(data);
                break;

            case LISTEN:
                // RFC 9293: If the remote socket is specified, then change the connection from
                // RFC 9293: passive to active,
                LOG.trace("{} SEND user call was requested while we're in passive OPEN mode. Switch to active OPEN mode, initiate OPEN process, and enqueue {} bytes for transmission after connection has been established.", ctx.channel(), data.readableBytes());

                tcb.ensureLocalPortIsSelected(requestedLocalPort);
                tcb.remotePort(remotePort);

                // RFC 9293: select an ISS.
                tcb.selectIss();

                // RFC 9293: Send a SYN segment,
                // RFC 7323: Send a SYN segment containing the options: <TSval=Snd.TSclock>.
                // (timestamps option is automatically added by formSegment)
                final Segment seg = formSegment(ctx, tcb.iss(), SYN);
                LOG.trace("{} Initiate OPEN process by sending `{}`.", ctx.channel(), seg);
                tcb.send(ctx, seg);

                // RFC 9293: set SND.UNA to ISS, SND.NXT to ISS+1.
                tcb.initSndUnaSndNxt();

                // RFC 9293: Enter SYN-SENT state.
                changeState(ctx, SYN_SENT);

                // RFC 9293: Data associated with SEND may be sent with SYN segment or queued for
                // RFC 9293: transmission after entering ESTABLISHED state.
                tcb.enqueueData(data, promise);

                tcb.flush(ctx);

                // RFC 9293: The urgent bit if requested in the command must be sent with the data
                // RFC 9293: segments sent as a result of this command.
                // (URG not supported! It is only kept by TCP for legacy reasons, see SHLD-13)

                // RFC 9293: If there is no room to queue the request, respond with "error:
                // RFC 9293: insufficient resources". If the remote socket was not specified, then
                // RFC 9293: return "error: remote socket unspecified".
                // (not applicable to us)

                ctx.read();

                break;

            case SYN_SENT:
            case SYN_RECEIVED:
                // RFC 9293: Queue the data for transmission after entering ESTABLISHED state.
                LOG.trace("{} Queue {} byte(s) for transmission after entering ESTABLISHED state.", ctx.channel(), data.readableBytes());
                tcb.enqueueData(data, promise);

                // RFC 9293: If no space to queue, respond with "error: insufficient resources".
                // (not applicable to us)
                break;

            case ESTABLISHED:
            case CLOSE_WAIT:
                // RFC 9293: Segmentize the buffer and send it with a piggybacked acknowledgment
                // RFC 9293: (acknowledgment value = RCV.NXT).
                // RFC 7323: If the Snd.TS.OK flag is set, then include the TCP Timestamps option
                // RFC 7323: <TSval=Snd.TSclock,TSecr=TS.Recent> in each data segment.
                // (timestamps option is automatically added by formSegment)
                LOG.trace("{} Connection is established. Enqueue {} byte(s) for transmission.", ctx.channel(), data.readableBytes());
                tcb.enqueueData(data, promise);

                // RFC 9293: If there is insufficient space to remember this buffer, simply return
                // RFC 9293: "error: insufficient resources".
                // (not applicable to us)
                break;

            case FIN_WAIT_1:
            case FIN_WAIT_2:
            case CLOSING:
            case LAST_ACK:
            case TIME_WAIT:
                // RFC 9293: Return "error: connection closing" and do not service request.
                LOG.trace("{} Connection is in process of being closed. Reject data `{}`.", ctx.channel(), data);
                promise.tryFailure(new ConnectionClosingException(ctx.channel()));
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
        LOG.trace("{} RECEIVE call received (state={}).", ctx.channel(), state());
        switch (state()) {
            case CLOSED:
                assert tcb == null;
                // RFC 9293: If the user does not have access to such a connection, return
                // RFC 9293: "error: connection illegal for this process".
                // RFC 9293: Otherwise, return "error: connection does not exist".
                // (not applicable to us)
                break;

            case LISTEN:
            case SYN_SENT:
            case SYN_RECEIVED:
                // RFC 9293: Queue for processing after entering ESTABLISHED state.
                if (!userCallReceiveAlreadyEnqueued) {
                    userCallReceiveAlreadyEnqueued = true;
                    establishedPromise.addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            userCallReceive(ctx);
                        }
                    });
                }

                // RFC 9293: If there is no room to queue this request, respond with "error:
                // RFC 9293: insufficient resources".
                // (not applicable to us)
                break;

            case ESTABLISHED:
            case FIN_WAIT_1:
            case FIN_WAIT_2:
                // RFC 9293: If insufficient incoming segments are queued to satisfy the
                // RFC 9293: request, queue the request.
                if (!tcb.receiveBuffer().isReadable()) {
                    readPending = true;
                    break;
                }
                readPending = false;

                // RFC 9293: If there is no queue space to remember the RECEIVE, respond with
                // RFC 9293: "error: insufficient resources".
                // (not applicable to us)

                // RFC 9293: Reassemble queued incoming segments into receive buffer and return
                // RFC 9293: to user.
                tcb.receiveBuffer().fireRead(ctx, tcb);

                // RFC 9293: Mark "push seen" (PUSH) if this is the case.
                // (not applicable to us)

                // RFC 9293: If RCV.UP is in advance of the data currently being passed to the
                // RFC 9293: user, notify the user of the presence of urgent data.
                // (URG not supported! It is only kept by TCP for legacy reasons, see SHLD-13)

                // RFC 9293: When the TCP endpoint takes responsibility for delivering data to
                // RFC 9293: the user, that fact must be communicated to the sender via an
                // RFC 9293: acknowledgment. The formation of such an acknowledgment is
                // RFC 9293: described below in the discussion of processing an incoming
                // RFC 9293: segment.
                // (ACK is generated on channelReadComplete)
                break;

            case CLOSE_WAIT:
                // RFC 9293: Since the remote side has already sent FIN, RECEIVEs must be
                // RFC 9293: satisfied by data already on hand, but not yet delivered to the
                // RFC 9293: user.
                if (!tcb.receiveBuffer().isReadable()) {
                    // RFC 9293: If no text is awaiting delivery, the RECEIVE will get an
                    // RFC 9293: "error: connection closing" response.
                    // (not applicable to us)
                }
                else {
                    // RFC 9293: Otherwise, any remaining data can be used to satisfy the
                    // RFC 9293: RECEIVE.
                    tcb.receiveBuffer().fireRead(ctx, tcb);
                }
                break;

            case CLOSING:
            case LAST_ACK:
            case TIME_WAIT:
                // RFC 9293: Return "error: connection closing".
                // (not applicable to us)
        }
    }

    /**
     * CLOSE call as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.4">RFC
     * 9293, Section 3.10.4</a>.
     */
    @SuppressWarnings("java:S128")
    private void userCallClose(final ChannelHandlerContext ctx,
                               final ChannelPromise promise) {
        LOG.trace("{} CLOSE call received.", ctx.channel(), state());

        switch (state()) {
            case CLOSED:
                // RFC 9293: If the user does not have access to such a connection, return
                // RFC 9293: "error: connection illegal for this process".
                // (not applicable to us)

                // RFC 9293: Otherwise, return "error: connection does not exist".
                // (not applicable to us. We instead are start listening to the initial close
                // request result)
                closedPromise.addListener(new PromiseNotifier<>(promise));
                return;

            case LISTEN:
                // RFC 9293: Any outstanding RECEIVEs are returned with "error: closing" responses.
                // (not applicable to us)

                // RFC 9293: Delete TCB,
                deleteTcb();

                // RFC 9293: enter CLOSED state,
                // (this happens implicitly when TCB is deleted)

                closedPromise.addListener(new PromiseNotifier<>(promise));
                ctx.close(closedPromise);

                // RFC 9293: and return.
                return;

            case SYN_SENT:
                // RFC 9293: Delete the TCB
                // RFC 9293: and return "error: closing" responses to any queued SENDs,
                tcb.sendBuffer().fail(new ConnectionClosingException(ctx.channel()));
                tcb.retransmissionQueue().release();
                // RFC 9293: or RECEIVEs.
                // (not applicable to us)
                // (deleteTcb must be called last)
                deleteTcb();

                // enter CLOSED state
                // (this happens implicitly when TCB is deleted)

                closedPromise.addListener(new PromiseNotifier<>(promise));
                ctx.close(closedPromise);

                break;

            case SYN_RECEIVED:
                if (tcb.sendBuffer().isEmpty()) {
                    // RFC 9293: If no SENDs have been issued and there is no pending data to send,
                    // RFC 9293: then form a FIN segment and send it,
                    final Segment seg = formSegment(ctx, tcb.sndNxt(), tcb.rcvNxt(), (byte) (FIN | ACK));
                    LOG.trace("{} Abort handshake by sending `{}`.", ctx.channel(), seg);
                    tcb.sendAndFlush(ctx, seg);

                    // RFC 9293: and enter FIN-WAIT-1 state;
                    changeState(ctx, FIN_WAIT_1);

                    closedPromise.addListener(new PromiseNotifier<>(promise));
                }
                else {
                    // RFC 9293: otherwise, queue for processing after entering ESTABLISHED state.
                    if (!userCallCloseAlreadyEnqueued) {
                        userCallCloseAlreadyEnqueued = true;
                        establishedPromise.addListener((ChannelFutureListener) future -> {
                            if (future.isSuccess()) {
                                userCallClose(ctx, promise);
                            }
                        });
                    }
                }
                break;

            case ESTABLISHED:
                // RFC 9293: Queue this until all preceding SENDs have been segmentized,
                precedingSendsHaveBeenSegmentized(ctx).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        // RFC 9293: then form a FIN segment and send it.
                        final Segment seg = formSegment(ctx, tcb.sndNxt(), tcb.rcvNxt(), (byte) (FIN | ACK));
                        LOG.trace("{} Initiate CLOSE sequence by sending `{}`.", ctx.channel(), seg);
                        tcb.sendAndFlush(ctx, seg);

                        // RFC 9293: In any case, enter FIN-WAIT-1 state.
                        changeState(ctx, FIN_WAIT_1);
                    }
                });

                closedPromise.addListener(new PromiseNotifier<>(promise));
                break;

            case FIN_WAIT_1:
            case FIN_WAIT_2:
                // RFC 9293: Strictly speaking, this is an error and should receive an
                // RFC 9293: "error: connection closing" response.
                // RFC 9293: An "ok" response would be acceptable, too, as long as a second FIN is
                // RFC 9293: not emitted (the first FIN may be retransmitted, though).
                promise.tryFailure(new ConnectionClosingException(ctx.channel()));

            case CLOSE_WAIT:
                // RFC 9293: Queue this request until all preceding SENDs have been segmentized;
                precedingSendsHaveBeenSegmentized(ctx).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        // RFC 9293: then send a FIN segment,
                        final Segment seg = formSegment(ctx, tcb.sndNxt(), tcb.rcvNxt(), (byte) (FIN | ACK));
                        tcb.sendAndFlush(ctx, seg);

                        // RFC 9293: enter LAST-ACK state.
                        changeState(ctx, LAST_ACK);
                    }
                });

                closedPromise.addListener(new PromiseNotifier<>(promise));
                break;

            case CLOSING:
            case LAST_ACK:
            case TIME_WAIT:
                // RFC 9293: Respond with "error: connection closing".
                promise.tryFailure(new ConnectionClosingException(ctx.channel()));
        }
    }

    private ChannelPromise precedingSendsHaveBeenSegmentized(final ChannelHandlerContext ctx) {
        assert segmentizedFuture == null;
        segmentizedRemainingBytes = tcb.sendBuffer().length();
        final ChannelPromise toReturn = ctx.newPromise();
        segmentizedFuture = toReturn;
        if (segmentizedRemainingBytes == 0) {
            // no preceding SENDs, complete future immediately
            segmentizedFuture.setSuccess();
        }
        segmentizedFuture.addListener((ChannelFutureListener) future -> {
            segmentizedFuture = null;
            segmentizedRemainingBytes = 0;
        });
        return toReturn;
    }

    /**
     * ABORT call as described in <a
     * href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.5">RFC 9293, Section
     * 3.10.5</a>.
     */
    @SuppressWarnings("java:S128")
    public void userCallAbort() {
        assert ctx == null || ctx.executor().inEventLoop();
        LOG.trace("{} ABORT call received.", ctx != null ? ctx.channel() : "[NOCHANNEL]", state());

        switch (state()) {
            case CLOSED:
                // RFC 9293: If the user should not have access to such a connection, return "error:
                // RFC 9293: connection illegal for this process".
                // (not applicable to us)

                // RFC 9293: Otherwise, return "error: connection does not exist".
                throw new ConnectionDoesNotExistException(ctx.channel());

            case LISTEN:
                // RFC 9293: Any outstanding RECEIVEs should be returned with "error: connection
                // RFC 9293: reset" responses.
                // (not applicable to us)

                // RFC 9293: Delete TCB,
                deleteTcb();

                // RFC 9293: enter CLOSED state,
                // (this happens implicitly when TCB is deleted)

                ctx.close(closedPromise);

                // RFC 9293: and return.
                return;

            case SYN_SENT:
                // RFC 9293: All queued SENDs
                tcb.sendBuffer().fail(new ConnectionResetException(ctx.channel()));
                // RFC 9293: and RECEIVEs should be given "connection reset" notification.
                // (not applicable to us)

                // RFC 9293: Delete the TCB,
                deleteTcb();

                // RFC 9293: enter CLOSED state,
                // (this happens implicitly when TCB is deleted)

                ctx.close(closedPromise);

                // RFC 9293: and return.
                return;

            case SYN_RECEIVED:
            case ESTABLISHED:
            case FIN_WAIT_1:
            case FIN_WAIT_2:
            case CLOSE_WAIT:
                // RFC 9293: Send a reset segment:
                // RFC 9293: <SEQ=SND.NXT><CTL=RST>
                final Segment seg = formSegment(ctx, tcb.sndNxt(), RST);

                // RFC 9293: All queued SENDs
                tcb.sendBuffer().fail(new ConnectionResetException(ctx.channel()));
                // RFC 9293: and RECEIVEs should be given "connection reset" notification;
                // (not applicable to us)

                // RFC 9293: all segments queued for transmission (except for the RST
                // RFC 9293: formed above) or retransmission should be flushed.
                tcb.retransmissionQueue().release();
                // (RST send must be called after flush)
                tcb.sendAndFlush(ctx, seg);

                // RFC 9293: Delete the TCB,
                deleteTcb();

                // RFC 9293: enter CLOSED state,
                // (this happens implicitly when TCB is deleted)

                ctx.close(closedPromise);

                // RFC 9293: and return.
                return;

            case CLOSING:
            case LAST_ACK:
            case TIME_WAIT:
                // RFC 9293: Respond with "ok" and delete the TCB,
                deleteTcb();

                // RFC 9293: enter CLOSED state,
                // (this happens implicitly when TCB is deleted)

                ctx.close(closedPromise);

                // RFC 9293: and return.
                return;
        }
    }

    /**
     * STATUS call as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.6">RFC
     * 9293, Section 3.10.6</a>.
     */
    public ConnectionHandshakeStatus userCallStatus() {
        LOG.trace("{} STATUS call received.", ctx != null ? ctx.channel() : "[NOCHANNEL]", state());

        switch (state()) {
            case CLOSED:
                // RFC 9293: If the user should not have access to such a connection, return
                // RFC 9293: "error: connection illegal for this process".
                // (not applicable to us)

                // RFC 9293: Otherwise, return "error: connection does not exist".
                throw new ConnectionDoesNotExistException(ctx.channel());

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
                return new ConnectionHandshakeStatus(state(), tcb);
        }

        // unreachable statement
        return null;
    }

    /*
     * Helper Methods
     */

    private void createTcb(final ChannelHandlerContext ctx) {
        assert tcb == null;
        tcb = config.tcbSupplier().apply(config, ctx.channel());
        LOG.trace("{} TCB created: {}", ctx.channel(), tcb);
    }

    void deleteTcb() {
        if (tcb != null) {
            LOG.trace("{} TCB deleted: {}", ctx.channel(), tcb);
            tcb.delete();
            tcb = null;
        }

        cancelUserTimer(ctx);
        cancelRetransmissionTimer(ctx);
        cancelTimeWaitTimer(ctx);
    }

    void changeState(final ChannelHandlerContext ctx, final State newState) {
        LOG.trace("{} Change to {} state.", ctx.channel(), newState);
        if (tcb != null) {
            assert state() != newState : "Illegal state change from " + state() + " to " + newState;
            tcb.state(newState);
        }
    }

    private void initHandler(final ChannelHandlerContext ctx) {
        if (tcb == null) {
            establishedPromise = ctx.newPromise();
            closedPromise = ctx.newPromise();
            userCallOpen(ctx);
        }
    }

    /**
     * SEGMENT ARRIVES event as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.7">RFC
     * 9293, Section 3.10.7</a>.
     */
    private void segmentArrives(final ChannelHandlerContext ctx,
                                final Segment seg) {
        LOG.trace("{} Read `{}`.", ctx.channel(), seg);

        try {
            switch (state()) {
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
            response = formSegment(ctx, seg.dstPort(), seg.srcPort(), 0, add(seg.seq(), seg.len()), (byte) (RST | ACK));
        }
        else {
            // RFC 9293: If the ACK bit is on,
            // RFC 9293: <SEQ=SEG.ACK><CTL=RST>
            response = formSegment(ctx, seg.dstPort(), seg.srcPort(), seg.ack(), RST);
        }
        LOG.trace("{} As we're already on CLOSED state, this channel is going to be removed soon. Reset remote peer `{}`.", ctx.channel(), response);
        ctx.writeAndFlush(response);

        // RFC 9293: Return.
        return;
    }

    private void segmentArrivesOnListenState(final ChannelHandlerContext ctx,
                                             final Segment seg) {
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
            final Segment response = formSegment(ctx, seg.dstPort(), seg.srcPort(), seg.ack(), RST);
            LOG.trace("{} We are on a state were we have never sent anything that must be ACKnowledged. Send RST `{}`.", ctx.channel(), response);
            ctx.writeAndFlush(response);

            // RFC 9293: Return.
            return;
        }

        // RFC 9293: Third, check for a SYN:
        if (seg.isSyn()) {
            // RFC 9293: If the SYN bit is set, check the security. If the security/compartment on
            // RFC 9293: the incoming segment does not exactly match the security/compartment in
            // RFC 9293: the TCB, then send a reset and return.
            // RFC 9293: <SEQ=0><ACK=SEG.SEQ+SEG.LEN><CTL=RST,ACK>
            // (not applicable to us)

            LOG.trace("{} Remote peer initiates handshake by sending a SYN `{}` to us.", ctx.channel(), seg);

            tcb.remotePort(seg.srcPort());

            if (config.timestamps()) {
                // RFC 7323: Check for a TSopt option;
                final TimestampsOption tsOpt = (TimestampsOption) seg.options().get(TIMESTAMPS);
                if (tsOpt != null) {
                    LOG.trace("{} RTT measurement: < {}", ctx.channel(), tsOpt);
                    final long segTsVal = tsOpt.tsVal;
                    LOG.trace("{} RTT measurement: Set TS.Recent to SEG.TSval and turn on Snd.TS.OK.", ctx.channel(), segTsVal);

                    // RFC 7323: if one is found, save SEG.TSval in the variable TS.Recent
                    tcb.tsRecent(ctx, segTsVal);

                    // RFC 7323: and turn on the Snd.TS.OK bit in the connection control block
                    tcb.turnOnSndTsOk();
                }
            }

            // RFC 9293: Set RCV.NXT to SEG.SEQ+1, IRS is set to SEG.SEQ,
            tcb.rcvNxt(advanceSeq(seg.seq(), 1));
            tcb.irs(seg.seq());

            // RFC 9293: and any other control or text should be queued for processing later.
            // (not applicable to us, as we do not implement T/TCP or TCP Fast Open)
            final boolean anyOtherControlOrText = !seg.isOnlySyn() && seg.content().isReadable();
            assert !anyOtherControlOrText : "not supported (yet)";

            LOG.trace("{} TCB synchronized: {}", ctx.channel(), tcb);

            // RFC 9293: TCP endpoints MUST implement [...] receiving the MSS Option (MUST-14).
            final Integer mss = (Integer) seg.options().get(MAXIMUM_SEGMENT_SIZE);
            if (mss != null) {
                LOG.trace("{} Remote peer sent MSS {}. Set SendMSS to {}.", ctx.channel(), mss, tcb.sendMss(), tcb.sendMss());
                tcb.sendMss(mss);
            }

            // RFC 9293: ISS should be selected
            tcb.selectIss();

            // RFC 9293: and a SYN segment sent of the form:
            // RFC 9293: <SEQ=ISS><ACK=RCV.NXT><CTL=SYN,ACK>
            final Segment response = formSegment(ctx, tcb.iss(), tcb.rcvNxt(), (byte) (SYN | ACK));

            if (config.timestamps()) {
                // RFC 7323: If the Snd.TS.OK bit is on, include a TSopt
                // RFC 7323: <TSval=Snd.TSclock,TSecr=TS.Recent> in this segment.
                // (timestamps option is automatically added by formSegment)
                LOG.trace("{} RTT measurement: Include TSopt to segment and set Last.ACK.sent to RCV.NXT.", ctx.channel());

                // RFC 7323: Last.ACK.sent is set to RCV.NXT.
                tcb.lastAckSent(ctx, tcb.rcvNxt());
            }

            LOG.trace("{} ACKnowledge the received segment and send our SYN `{}`.", ctx.channel(), response);
            tcb.send(ctx, response);

            // RFC 9293: SND.NXT is set to ISS+1 and SND.UNA to ISS.
            tcb.initSndUnaSndNxt();

            // RFC 9293: The connection state should be changed to SYN-RECEIVED.
            changeState(ctx, SYN_RECEIVED);

            // RFC 9293: Note that any other incoming control or data (combined with SYN) will be
            // RFC 9293: processed in the SYN-RECEIVED state, but processing of SYN and ACK should
            // RFC 9293: not be repeated. If the listen was not fully specified (i.e., the remote
            // RFC 9293: socket was not fully specified), then the unspecified fields should be
            // RFC 9293: filled in now.

            return;
        }

        // RFC 9293: Fourth, other data or control:
        // RFC 9293: This should not be reached.
        // RFC 9293: Drop the segment
        // (this is handled by handler's auto release of all arrived segments)

        // RFC 9293: and return. Any other control or data-bearing segment (not containing SYN) must
        // RFC 9293: have an ACK and thus would have been discarded by the ACK processing in the
        // RFC 9293: second step, unless it was first discarded by RST checking in the first step.
        return;
    }

    @SuppressWarnings("java:S3776")
    private void segmentArrivesOnSynSentState(final ChannelHandlerContext ctx,
                                              final Segment seg) {
        // RFC 9293: First, check the ACK bit:
        if (seg.isAck()) {
            // RFC 9293: If the ACK bit is set,
            if (lessThanOrEqualTo(seg.ack(), tcb.iss()) || greaterThan(seg.ack(), tcb.sndNxt())) {
                // RFC 9293: If SEG.ACK =< ISS or SEG.ACK > SND.NXT, send a reset (unless the RST
                // RFC 9293: bit is set, if so drop the segment and return)
                LOG.trace("{} We got an ACK `{}` for an SEG we never sent. Seems like remote peer is synchronized to another connection.", ctx.channel(), seg);
                if (seg.isRst()) {
                    LOG.trace("{} As the RST bit is set. It doesn't matter as we will reset or connection now.", ctx.channel(), state());
                }
                else {
                    final Segment response = formSegment(ctx, seg.ack(), RST);
                    LOG.trace("{} Inform remote peer about the desynchronization state by sending an `{}` and dropping the inbound SEG.", ctx.channel(), response);
                    tcb.send(ctx, response);
                }

                // RFC 9293: and discard the segment.
                // (this is handled by handler's auto release of all arrived segments)
                // RFC 9293: Return.
                return;
            }
        }

        // RFC 9293: Second, check the RST bit:
        if (seg.isRst()) {
            // RFC 9293: If the RST bit is set,

            // RFC 9293: A potential blind reset attack is described in RFC 5961 [9]. The
            // RFC 9293: mitigation described in that document has specific applicability explained
            // RFC 9293: therein, and is not a substitute for cryptographic protection (e.g., IPsec
            // RFC 9293: or TCP-AO). A TCP implementation that supports the mitigation described in
            // RFC 9293: RFC 5961 SHOULD first check that the sequence number exactly matches
            // RFC 9293: RCV.NXT prior to executing the action in the next paragraph.
            if (seg.seq() != tcb.rcvNxt()) {
                LOG.trace("{} SEG `{}` has an unexpected SEQ. Blind reset attack!? Discard SEQ!", ctx.channel(), seg);
                return;
            }

            // RFC 9293: If the ACK was acceptable,
            if (seg.isAck() && lessThan(tcb.sndUna(), seg.ack()) && lessThanOrEqualTo(seg.ack(), tcb.sndNxt())) {
                LOG.trace("{} SEG `{}` is an acceptable ACK. Inform user, drop segment, enter CLOSED state.", ctx.channel(), seg);

                // RFC 9293: then signal to the user "error: connection reset",
                ctx.fireExceptionCaught(new ConnectionResetException(ctx.channel()));

                // RFC 9293: drop the segment,
                // (this is handled by handler's auto release of all arrived segments)

                // RFC 9293: enter CLOSED state,
                // (this happens implicitly when TCB is deleted)

                // RFC 9293: delete TCB,
                deleteTcb();

                ctx.close(closedPromise);

                // RFC 9293: and return.
                return;
            }
            else {
                // RFC 9293: Otherwise (no ACK), drop the segment
                LOG.trace("{} SEG `{}` is not an acceptable ACK. Drop it.", ctx.channel(), seg);

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
        // (not applicable to us)

        // RFC 9293: Fourth, check the SYN bit:
        // RFC 9293: This step should be reached only if the ACK is ok, or there is no ACK, and the
        // RFC 9293: segment did not contain a RST.
        if (seg.isSyn()) {
            // RFC 9293: If the SYN bit is on and the security/compartment is acceptable,
            // RFC 9293: then RCV.NXT is set to SEG.SEQ+1, IRS is set to SEG.SEQ.
            tcb.rcvNxt(advanceSeq(seg.seq(), 1));
            tcb.irs(seg.seq());

            if (seg.isAck()) {
                if (lessThan(tcb.sndUna(), seg.ack()) && lessThanOrEqualTo(seg.ack(), tcb.sndNxt())) {
                    // RFC 9293: SND.UNA should be advanced to equal SEG.ACK (if there is an ACK),
                    tcb.sndUna(ctx, seg.ack());

                    // RFC 9293: and any segments on the retransmission queue that are thereby
                    // RFC 9293: acknowledged should be removed.
                    removeAcknowledgedSegmentsFromRetransmissionQueue(ctx);
                }
            }

            LOG.trace("{} TCB synchronized: {}", ctx.channel(), tcb);

            if (config.timestamps()) {
                // RFC 7323: Check for a TSopt option;
                final TimestampsOption tsOpt = (TimestampsOption) seg.options().get(TIMESTAMPS);
                if (tsOpt != null) {
                    LOG.trace("{} RTT measurement: < {}", ctx.channel(), tsOpt);
                    final long segTsVal = tsOpt.tsVal;
                    LOG.trace("{} RTT measurement: Set TS.Recent to SEG.TSval and turn on Snd.TS.OK.", ctx.channel());

                    // RFC 7323: if one is found, save SEG.TSval in variable TS.Recent
                    tcb.tsRecent(ctx, segTsVal);

                    // RFC 7323: and turn on the Snd.TS.OK bit in the connection control block.
                    tcb.turnOnSndTsOk();

                    // RFC 7323: If the ACK bit is set, use Snd.TSclock - SEG.TSecr as the initial
                    // RFC 7323: RTT estimate.
                    if (seg.isAck()) {
                        final long segTsEcr = tsOpt.tsEcr;

                        final long r = config.clock().time() - segTsEcr;
                        final float newRttVar = (float) (r / 2.0);
                        final int newRto = (int) (tcb.sRtt() + max(config.clock().g(), config.k() * tcb.rttVar()));
                        LOG.trace("{} RTT measurement: Set SRTT to R = {}, RTTVAR to R/2 = {}, and RTO to `SRTT+max(G,K*RTTVAR) = {}+max({},{}*{})`", ctx.channel(), r, newRttVar, newRto, tcb.sRtt(), config.clock().g(), config.k(), tcb.rttVar());

                        // RFC 6298:       the host MUST set
                        // RFC 6298:       SRTT <- R
                        tcb.sRtt(ctx, r);

                        // RFC 6298:       RTTVAR <- R/2
                        tcb.rttVar(ctx, newRttVar);

                        // RFC 6298:       RTO <- SRTT + max (G, K*RTTVAR)
                        // RFC 6298: where K = 4
                        tcb.rto(ctx, newRto);
                    }
                }
            }

            if (greaterThan(tcb.sndUna(), tcb.iss())) {
                LOG.trace("{} Remote peer has ACKed our SYN and sent us its SYN `{}`. Handshake on our side is completed.", ctx.channel(), seg);

                // these are not contained in RFC9293 but without them transmission will not start
                tcb.sndWnd(ctx, seg.wnd());
                tcb.sndWl1(seg.seq());
                tcb.sndWl2(seg.ack());

                // RFC 9293: If SND.UNA > ISS (our SYN has been ACKed), change the connection state
                // RFC 9293: to ESTABLISHED,
                changeState(ctx, ESTABLISHED);

                // RFC 9293: TCP endpoints MUST implement [...] receiving the MSS Option (MUST-14).
                final Integer mss = (Integer) seg.options().get(MAXIMUM_SEGMENT_SIZE);
                if (mss != null) {
                    LOG.trace("{} Remote peer sent MSS {}. Set SendMSS to {}.", ctx.channel(), mss, tcb.sendMss(), tcb.sendMss());
                    tcb.sendMss(mss);
                }

                // RFC 9293: form an ACK segment
                // RFC 9293: <SEQ=SND.NXT><ACK=RCV.NXT><CTL=ACK>
                // RFC 9293: and send it. Data or controls that were queued for transmission MAY be
                // RFC 9293: included. Some TCP implementations suppress sending this segment when
                // RFC 9293: the received segment contains data that will anyways generate an
                // RFC 9293: acknowledgment in the later processing steps, saving this extra
                // RFC 9293: acknowledgment of the SYN from being sent.
                // RFC 7323: If the Snd.TS.OK bit is on, include a TSopt option
                // RFC 7323: <TSval=Snd.TSclock,TSecr=TS.Recent> in this <ACK> segment.
                // (timestamps option is automatically added by formSegment)
                final Segment response = formSegment(ctx, tcb.sndNxt(), tcb.rcvNxt(), ACK);
                LOG.trace("{} ACKnowledge the received segment with a `{}` so the remote peer can complete the handshake as well.", ctx.channel(), response);
                tcb.outgoingSegmentQueue().add(ctx, response);

                // RFC 7323: Last.ACK.sent is set to RCV.NXT.
                // (is automatically done by formSegment)

                // RFC 9293: If there are other controls or text in the segment, then continue
                // RFC 9293: processing at the sixth step under Section 3.10.7.4 where the URG bit
                // RFC 9293: is checked;
                // (not applicable to us, as we do not implement T/TCP or TCP Fast Open)
                final boolean anyOtherControlOrText = seg.content().isReadable();
                assert !anyOtherControlOrText : "not supported (yet)";

                tcb.trySendingPreviouslyUnsentData(ctx);

                // inform user
                final ConnectionHandshakeCompleted evt = new ConnectionHandshakeCompleted();
                LOG.trace("{} Trigger user event `{}`.", ctx.channel(), evt);
                ctx.fireUserEventTriggered(evt);

                // process queued operations
                establishedPromise.setSuccess();

                // RFC 9293: otherwise, return.
                return;
            }
            else {
                // RFC 9293: Otherwise, enter SYN-RECEIVED,
                changeState(ctx, SYN_RECEIVED);

                // RFC 9293: form a SYN,ACK segment
                // RFC 9293: <SEQ=ISS><ACK=RCV.NXT><CTL=SYN,ACK>
                // RFC 9293: and send it.
                final Segment response = formSegment(ctx, tcb.iss(), tcb.rcvNxt(), (byte) (SYN | ACK));
                LOG.trace("{} Write `{}`.", ctx.channel(), response);
                tcb.send(ctx, response);

                // RFC 9293: Set the variables:
                // RFC 9293: SND.WND <- SEG.WND
                tcb.sndWnd(ctx, seg.wnd());
                // RFC 9293: SND.WL1 <- SEG.SEQ
                tcb.sndWl1(seg.seq());
                // RFC 9293: SND.WL2 <- SEG.ACK
                tcb.sndWl2(seg.ack());

                // RFC 9293: If there are other controls or text in the segment, queue them for
                // RFC 9293: processing after the ESTABLISHED state has been reached,
                // (not applicable to us, as we do not implement T/TCP or TCP Fast Open)
                final boolean anyOtherControlOrText = !seg.isOnlySyn() && seg.content().isReadable();
                assert !anyOtherControlOrText : "not supported (yet)";

                // RFC 9293: return.
                return;
            }

            // RFC 9293: Note that it is legal to send and receive application data on SYN
            // RFC 9293: segments (this is the "text in the segment" mentioned above). There has
            // RFC 9293: been significant misinformation and misunderstanding of this topic
            // RFC 9293: historically. Some firewalls and security devices consider this
            // RFC 9293: suspicious. However, the capability was used in T/TCP [21] and is used
            // RFC 9293: in TCP Fast Open (TFO) [48], so is important for implementations and
            // RFC 9293: network devices to permit.
        }

        // RFC 9293: Fifth, if neither of the SYN or RST bits is set,
        // RFC 9293: then drop the segment
        // (this is handled by handler's auto release of all arrived segments)

        // RFC 9293: and return.
    }

    private void removeAcknowledgedSegmentsFromRetransmissionQueue(final ChannelHandlerContext ctx) {
        final boolean somethingWasAcked = tcb.retransmissionQueue().removeAcknowledged(ctx, tcb);

        if (somethingWasAcked) {
            if (tcb.retransmissionQueue().isEmpty()) {
                LOG.trace("{} All outstanding data has been acknowledged. Turn off the RETRANSMISSION timer.", ctx.channel());
                cancelUserTimer(ctx);
                // RFC 6298: (5.2) When all outstanding data has been acknowledged, turn off the
                // RFC 6298:       retransmission timer.
                cancelRetransmissionTimer(ctx);
            }
            else {
                LOG.trace("{} New, but not all outstanding data ({} segments still in queue) has been acknowledged. Restart the retransmission timer.", ctx.channel(), tcb.retransmissionQueue().size());
                restartUserTimer(ctx);
                // RFC 6298: (5.3) When an ACK is received that acknowledges new data, restart the
                // RFC 6298:       retransmission timer so that it will expire after RTO seconds
                // RFC 6298:       (for the current value of RTO).
                restartRetransmissionTimer(ctx, tcb);
            }
        }
    }

    @SuppressWarnings("java:S3776")
    private void segmentArrivesOnOtherStates(final ChannelHandlerContext ctx,
                                             final Segment seg) {
        // RFC 9293: First, check sequence number:

        switch (state()) {
            case SYN_RECEIVED:
            case ESTABLISHED:
            case FIN_WAIT_1:
            case FIN_WAIT_2:
            case CLOSE_WAIT:
            case CLOSING:
            case LAST_ACK:
            case TIME_WAIT:
                // RFC 9293: Segments are processed in sequence. Initial tests on arrival are used
                // RFC 9293: to discard old duplicates, but further processing is done in SEG.SEQ
                // RFC 9293: order. If a segment's contents straddle the boundary between old and
                // RFC 9293: new, only the new parts are processed.

                Boolean acceptableSeg = null;
                if (config.timestamps()) {
                    // RFC 7323: Check whether the segment contains a Timestamps option
                    final TimestampsOption tsOpt = (TimestampsOption) seg.options().get(TIMESTAMPS);
                    if (tsOpt != null) {
                        // RFC 7323: and if bit Snd.TS.OK is on. If so:
                        final long segTsVal = tsOpt.tsVal;
                        if (segTsVal < tcb.tsRecent() && !seg.isRst()) {
                            // RFC 7323: If SEG.TSval < TS.Recent and the RST bit is off:

                            // TODO:
                            //  RFC 7323: If the connection has been idle more than 24 days, save
                            //  RFC 7323: SEG.TSval in variable TS.Recent,

                            // RFC 7323: else the segment is not acceptable; follow the steps below
                            // RFC 7323: for an unacceptable segment.
                            acceptableSeg = false;
                        }
                        else if (segTsVal >= tcb.tsRecent() && seg.seq() <= tcb.lastAckSent()) {
                            // RFC 7323: If SEG.TSval >= TS.Recent and SEG.SEQ <= Last.ACK.sent,
                            // RFC 7323: then save SEG.TSval in variable TS.Recent.
                            LOG.trace("{} RTT measurement: Save SEG.TSval in TS.Recent.", ctx.channel());
                            tcb.tsRecent(ctx, segTsVal);
                        }
                    }
                }

                // RFC 9293: In general, the processing of received segments MUST be implemented to
                // RFC 9293: aggregate ACK segments whenever possible (MUST-58). For example, if the
                // RFC 9293: TCP endpoint is processing a series of queued segments, it MUST process
                // RFC 9293: them all before sending any ACK segments (MUST-59).

                if (acceptableSeg == null) {
                    // RFC 9293: There are four cases for the acceptability test for an incoming
                    // RFC 9293: segment:
                    // RFC 9293: Segment Length Receive Window  Test
                    // RFC 9293: 0              0               SEG.SEQ = RCV.NXT
                    if (seg.len() == 0 && tcb.rcvWnd() == 0) {
                        acceptableSeg = seg.seq() == tcb.rcvNxt();
                    }
                    // RFC 9293: 0              >0              RCV.NXT =< SEG.SEQ < RCV.NXT+RCV.WND
                    else if (seg.len() == 0 && tcb.rcvWnd() > 0) {
                        acceptableSeg = lessThanOrEqualTo(tcb.rcvNxt(), seg.seq()) && lessThan(seg.seq(), add(tcb.rcvNxt(), tcb.rcvWnd()));
                    }
                    // RFC 9293: >0             0               not acceptable
                    else if (seg.len() > 0 && tcb.rcvWnd() == 0) {
                        acceptableSeg = false;
                    }
                    // RFC 9293: >0             >0              RCV.NXT =< SEG.SEQ < RCV.NXT+RCV.WND
                    // RFC 9293:                                or
                    // RFC 9293:                                RCV.NXT =< SEG.SEQ+SEG.LEN-1 < RCV.NXT+RCV.WND
                    else {
                        acceptableSeg = (lessThanOrEqualTo(tcb.rcvNxt(), seg.seq()) && lessThan(seg.seq(), add(tcb.rcvNxt(), tcb.rcvWnd()))) ||
                                (lessThanOrEqualTo(tcb.rcvNxt(), add(seg.seq(), seg.len() - 1L)) && greaterThan(add(seg.seq(), seg.len() - 1L), add(tcb.rcvNxt(), tcb.rcvWnd())));
                    }
                }

                // RFC 9293: In implementing sequence number validation as described here, please
                // RFC 9293: note Appendix A.2.

                // RFC 9293: If the RCV.WND is zero, no segments will be acceptable, but special
                // RFC 9293: allowance should be made to accept valid ACKs, URGs, and RSTs.

                if (FALSE.equals(acceptableSeg)) {
                    // RFC 9293: If an incoming segment is not acceptable, an acknowledgment should
                    // RFC 9293: be sent in reply (unless the RST bit is set, if so drop the segment
                    // RFC 9293: and return):
                    // RFC 9293: <SEQ=SND.NXT><ACK=RCV.NXT><CTL=ACK>
                    if (!seg.isRst()) {
                        final Segment response = formSegment(ctx, tcb.sndNxt(), tcb.rcvNxt(), ACK);
                        LOG.trace("{} We got an not acceptable SEG `{}`. Send an ACK `{}` for the SEG we actually expect.", ctx.channel(), seg, response);

                        // RFC 7323: Last.ACK.sent is set to SEG.ACK of the acknowledgment.
                        // (is automatically done by formSegment)

                        // RFC 7323: If the Snd.TS.OK bit is on, include the Timestamps option
                        // RFC 7323: <TSval=Snd.TSclock,TSecr=TS.Recent> in this <ACK> segment.
                        // (timestamps option is automatically added by formSegment)

                        tcb.send(ctx, response);

                        // RFC 9293: After sending the acknowledgment, drop the unacceptable segment and
                        // RFC 9293: return.
                        // (this is handled by handler's auto release of all arrived segments)

                        return;
                    }

                    // RFC 9293: Note that for the TIME-WAIT state, there is an improved algorithm
                    // RFC 9293: described in [40] for handling incoming SYN segments that utilizes
                    // RFC 9293: timestamps rather than relying on the sequence number check
                    // RFC 9293: described here. When the improved algorithm is implemented, the
                    // RFC 9293: logic above is not applicable for incoming SYN segments with
                    // RFC 9293: Timestamp Options, received on a connection in the TIME-WAIT state.
                    // (not applicable to us)

                    // RFC 9293: In the following it is assumed that the segment is the idealized
                    // RFC 9293: segment that begins at RCV.NXT and does not exceed the window. One
                    // RFC 9293: could tailor actual segments to fit this assumption by trimming off
                    // RFC 9293: any portions that lie outside the window (including SYN and FIN)
                    // RFC 9293: and only processing further if the segment then begins at RCV.NXT.
                    // RFC 9293: Segments with higher beginning sequence numbers SHOULD be held for
                    // RFC 9293: later processing (SHLD-31).
                    // (this is done by ReceiveBuffer)
                }
        }

        // RFC 9293: Second, check the RST bit:

        // RFC 9293: RFC 5961 [9], Section 3 describes a potential blind reset attack and optional
        // RFC 9293: mitigation approach. This does not provide a cryptographic protection (e.g.,
        // RFC 9293: as in IPsec or TCP-AO) but can be applicable in situations described in
        // RFC 9293: RFC 5961. For stacks implementing the protection described in RFC 5961, the
        // RFC 9293: three checks below apply; otherwise, processing for these states is indicated
        // RFC 9293: further below.
        // RFC 9293:
        if (seg.isRst() && !(lessThanOrEqualTo(tcb.rcvNxt(), seg.seq()) && lessThan(seg.seq(), add(tcb.rcvNxt(), tcb.rcvWnd())))) {
            // RFC 9293: 1)    If the RST bit is set and the sequence number is outside the current
            // RFC 9293:       receive window, silently drop the segment.
            LOG.trace("{} SEG `{}` is outside the current receive window. Blind reset attack!? Drop the segment.", ctx.channel(), seg);
            return;
        }
        else if (seg.isRst() && seg.seq() == tcb.rcvNxt()) {
            // RFC 9293: 2)    If the RST bit is set and the sequence number exactly matches the
            // RFC 9293:       next expected sequence number (RCV.NXT), then TCP endpoints MUST
            // RFC 9293:       reset the connection in the manner prescribed below according to the
            // RFC 9293:        connection state.
        }
        else if (seg.isRst() && seg.seq() != tcb.rcvNxt()) {
            // RFC 9293: 3)    If the RST bit is set and the sequence number does not exactly match
            // RFC 9293:       the next expected sequence value, yet is within the current receive
            // RFC 9293:       window, TCP endpoints MUST send an acknowledgment (challenge ACK):
            // RFC 9293:       <SEQ=SND.NXT><ACK=RCV.NXT><CTL=ACK>
            final Segment response = formSegment(ctx, tcb.sndNxt(), tcb.rcvNxt(), ACK);
            LOG.trace("{} SEG `{}` has not expected SEQ. Blind reset attack!? Send challenge ACK `{}`.", ctx.channel(), seg, response);
            tcb.send(ctx, response);

            // RFC 9293:       After sending the challenge ACK, TCP endpoints MUST drop the
            // RFC 9293:       unacceptable segment and stop processing the incoming packet
            // RFC 9293:       further.
            // (this is handled by handler's auto release of all arrived segments)

            // RFC 9293:       Note that RFC 5961 and Errata ID 4772 [99] contain additional
            // RFC 9293:       considerations for ACK throttling in an implementation.
            return;
        }

        switch (state()) {
            case SYN_RECEIVED:
                if (seg.isRst()) {
                    // RFC 9293: If the RST bit is set,
                    if (!config.activeOpen()) {
                        // RFC 9293: If this connection was initiated with a passive OPEN (i.e.,
                        // RFC 9293: came from the LISTEN state), then return this connection to
                        // RFC 9293: LISTEN state
                        LOG.trace("{} We got `{}`. Remote peer is not longer interested in a connection. We're going back to the LISTEN state.", ctx.channel(), seg);
                        changeState(ctx, LISTEN);

                        // RFC 9293: and return. The user need not be informed.
                        // RFC 9293: In either case, the retransmission queue should be flushed.
                        tcb.retransmissionQueue().release();
                        return;
                    }
                    else {
                        // RFC 9293: If this connection was initiated with an active OPEN (i.e.,
                        // RFC 9293: came from SYN-SENT state), then the connection was refused;
                        // RFC 9293: signal the user "connection refused".
                        LOG.trace("{} We got `{}`. Connection has been refused by remote peer.", ctx.channel(), seg);
                        final ConnectionRefusedException evt = new ConnectionRefusedException(ctx.channel());
                        LOG.trace("{} Trigger user event `{}`.", ctx.channel(), evt);
                        ctx.fireExceptionCaught(evt);
                    }

                    // RFC 9293: In either case, the retransmission queue should be flushed.
                    tcb.retransmissionQueue().release();

                    if (config.activeOpen()) {
                        // RFC 9293: And in the active OPEN case, enter the CLOSED state
                        // (this happens implicitly when TCB is deleted)

                        // RFC 9293: and delete the TCB,
                        deleteTcb();

                        ctx.close(closedPromise);

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
                    // RFC 9293: If the RST bit is set, then any outstanding RECEIVEs
                    // (not applicable to us)
                    // RFC 9293: and SEND should receive "reset" responses.
                    tcb.sendBuffer().fail(new ConnectionResetException(ctx.channel()));

                    // RFC 9293: All segment queues should be flushed.
                    tcb.retransmissionQueue().release();

                    LOG.trace("{} We got `{}`. Remote peer is not longer interested in a connection. Close channel.", ctx.channel(), seg);

                    // RFC 9293: Users should also receive an unsolicited general
                    // RFC 9293: "connection reset" signal.
                    final ConnectionResetException evt = new ConnectionResetException(ctx.channel());
                    LOG.trace("{} Trigger user event `{}`.", ctx.channel(), evt);
                    ctx.fireExceptionCaught(evt);

                    // RFC 9293: Enter the CLOSED state, delete the TCB
                    // (this happens implicitly when TCB is deleted)

                    // RFC 9293: delete the TCB,
                    deleteTcb();

                    ctx.close(closedPromise);

                    // RFC 9293: and return.
                    return;
                }
                break;

            case CLOSING:
            case LAST_ACK:
            case TIME_WAIT:
                if (seg.isRst()) {
                    // RFC 9293: If the RST bit is set, then enter the CLOSED state,
                    // (this happens implicitly when TCB is deleted)

                    LOG.trace("{} We got `{}`. Close channel.", ctx.channel(), seg);

                    // RFC 9293: delete the TCB,
                    deleteTcb();

                    ctx.close(closedPromise);

                    // RFC 9293: and return.
                    return;
                }
        }

        // RFC 9293: Third, check security:
        // (not applicable to us)

        // RFC 9293: Fourth, check the SYN bit:
        if (seg.isSyn()) {
            switch (state()) {
                case SYN_RECEIVED:
                    if (!config.activeOpen()) {
                        // RFC 9293: If the connection was initiated with a passive OPEN, then
                        // RFC 9293: return this connection to the LISTEN state and return.
                        LOG.trace("{} We got an additional SYN `{}`. As this connection was initiated with a passive OPEN return to LISTEN state.", ctx.channel(), seg);
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
                    if (state().synchronizedConnection()) {
                        // RFC 9293: If the SYN bit is set in these synchronized states, it may be
                        // RFC 9293: either a legitimate new connection attempt (e.g., in the case
                        // RFC 9293: of TIME-WAIT), an error where the connection should be reset,
                        // RFC 9293: or the result of an attack attempt, as described in
                        // RFC 9293: RFC 5961 [9]. For the TIME-WAIT state, new connections can be
                        // RFC 9293: accepted if the Timestamp Option is used and meets expectations
                        // RFC 9293: (per [40]). For all other cases, RFC 5961 provides a mitigation
                        // RFC 9293: with applicability to some situations, though there are also
                        // RFC 9293: alternatives that offer cryptographic protection (see
                        // RFC 9293: Section 7). RFC 5961 recommends that in these synchronized
                        // RFC 9293: states, if the SYN bit is set, irrespective of the sequence
                        // RFC 9293: number, TCP endpoints MUST send a "challenge ACK" to the remote
                        // RFC 9293: peer:
                        // RFC 9293: <SEQ=SND.NXT><ACK=RCV.NXT><CTL=ACK>
                        final Segment response = formSegment(ctx, tcb.sndNxt(), tcb.rcvNxt(), ACK);
                        LOG.trace("{} We got `{}` while we're in a synchronized state. Peer might be crashed. Send challenge ACK `{}`.", ctx.channel(), seg, response);
                        tcb.send(ctx, response);

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
        boolean becameEstablished = false;
        if (!seg.isAck()) {
            // RFC 9293: if the ACK bit is off,
            // RFC 9293: drop the segment
            // (this is handled by handler's auto release of all arrived segments)
            LOG.trace("{} Got `{}` with off ACK bit. Drop segment and return.", ctx.channel(), seg);
            // RFC 9293: and return
            return;
        }
        else {
            // RFC 9293: if the ACK bit is on,

            // RFC 9293: RFC 5961 [9], Section 5 describes a potential blind data injection attack,
            // RFC 9293: and mitigation that implementations MAY choose to include (MAY-12). TCP
            // RFC 9293: stacks that implement RFC 5961 MUST add an input check that the ACK value
            // RFC 9293: is acceptable only if it is in the range of
            // RFC 9293: ((SND.UNA - MAX.SND.WND) =< SEG.ACK =< SND.NXT).
            if (!(lessThanOrEqualTo(sub(tcb.sndUna(), tcb.maxSndWnd()), seg.ack()) && lessThanOrEqualTo(seg.ack(), tcb.sndNxt()))) {
                // RFC 9293: All incoming segments whose ACK value doesn't satisfy the above
                // RFC 9293: condition MUST be discarded
                // (this is handled by handler's auto release of all arrived segments)

                // RFC 9293: and an ACK sent back.
                final Segment response = formSegment(ctx, tcb.sndNxt(), tcb.rcvNxt(), ACK);
                LOG.trace("{} Write `{}`.", ctx.channel(), response);
                tcb.send(ctx, response);

                return;

                // RFC 9293: The new state variable MAX.SND.WND is defined as the
                // RFC 9293: largest window that the local sender has ever received from its peer
                // RFC 9293: (subject to window scaling) or may be hard-coded to a maximum permissible
                // RFC 9293: window value.
            }

            final boolean acceptableAck = lessThan(tcb.sndUna(), seg.ack()) && lessThanOrEqualTo(seg.ack(), tcb.sndNxt());
            // RFC 9293: When the ACK value is acceptable, the per-state processing below applies:

            switch (state()) {
                case SYN_RECEIVED:
                    if (lessThan(tcb.sndUna(), seg.ack()) && lessThanOrEqualTo(seg.ack(), tcb.sndNxt())) {
                        LOG.trace("{} Remote peer ACKnowledge `{}` receivable of our SYN. As we've already received his SYN the handshake is now completed on both sides.", ctx.channel(), seg);

                        // RFC 9293: If SND.UNA < SEG.ACK =< SND.NXT, then enter ESTABLISHED state
                        changeState(ctx, ESTABLISHED);
                        becameEstablished = true;

                        // RFC 9293: and continue processing with the variables below set to:
                        // RFC 9293: SND.WND <- SEG.WND
                        tcb.sndWnd(ctx, seg.wnd());
                        // RFC 9293: SND.WL1 <- SEG.SEQ
                        tcb.sndWl1(seg.seq());
                        // RFC 9293: SND.WL2 <- SEG.ACK
                        tcb.sndWl2(seg.ack());
                    }
                    else if (!acceptableAck) {
                        // RFC 9293: If the segment acknowledgment is not acceptable, form a
                        // RFC 9293: reset segment
                        // RFC 9293: <SEQ=SEG.ACK><CTL=RST>
                        // RFC 9293: and send it.
                        final Segment response = formSegment(ctx, seg.ack(), RST);
                        LOG.trace("{} SEG `{}` is not an acceptable ACK. Send RST `{}` and drop received SEG.", ctx.channel(), seg, response);
                        tcb.send(ctx, response);
                    }

                case ESTABLISHED:
                    if (establishedStateProcessing(ctx, seg)) {
                        return;
                    }
                    break;

                case FIN_WAIT_1:
                    final boolean ackOurFin = lessThan(tcb.sndUna(), seg.ack()) && lessThanOrEqualTo(seg.ack(), tcb.sndNxt());

                    // RFC 9293: In addition to the processing for the ESTABLISHED state,
                    if (establishedStateProcessing(ctx, seg)) {
                        return;
                    }

                    if (ackOurFin) {
                        // RFC 9293: if the FIN segment is now acknowledged, then enter FIN-WAIT-2
                        // RFC 9293: and continue processing in that state.
                        changeState(ctx, FIN_WAIT_2);
                    }
                    break;

                case FIN_WAIT_2:
                    // RFC 9293: In addition to the processing for the ESTABLISHED state,
                    if (establishedStateProcessing(ctx, seg)) {
                        return;
                    }

                    // RFC 9293: if the retransmission queue is empty, the user's CLOSE can be
                    // RFC 9293: acknowledged ("ok") but do not delete the TCB.

                    break;

                case CLOSE_WAIT:
                    // RFC 9293: Do the same processing as for the ESTABLISHED state.
                    if (establishedStateProcessing(ctx, seg)) {
                        return;
                    }
                    break;

                case CLOSING:
                    final boolean ackOurFin2 = lessThan(tcb.sndUna(), seg.ack()) && lessThanOrEqualTo(seg.ack(), tcb.sndNxt());
                    // RFC 9293: In addition to the processing for the ESTABLISHED state,
                    if (establishedStateProcessing(ctx, seg)) {
                        return;
                    }

                    if (ackOurFin2) {
                        // RFC 9293: if the ACK acknowledges our FIN, then enter the TIME-WAIT
                        // RFC 9293: state;
                        changeState(ctx, TIME_WAIT);

                        // RFC 9293: start the time-wait timer
                        restartTimeWaitTimer(ctx);

                        // RFC 9293: turn off the other timers
                        cancelUserTimer(ctx);
                        cancelRetransmissionTimer(ctx);
                    }
                    else {
                        // RFC 9293: otherwise, ignore the segment.
                        LOG.trace("{} The received ACKnowledged `{}` does not match our sent FIN. Ignore it.", ctx.channel(), seg);
                        return;
                    }
                    break;

                case LAST_ACK:
                    // RFC 9293: The only thing that can arrive in this state is an acknowledgment
                    // RFC 9293: of our FIN. If our FIN is now acknowledged,
                    LOG.trace("{} Our sent FIN has been ACKnowledged by `{}`. Close sequence done.", ctx.channel(), seg);

                    // RFC 9293: delete the TCB,
                    deleteTcb();

                    ctx.close(closedPromise);

                    // RFC 9293: enter the CLOSED state,
                    // (this happens implicitly when TCB is deleted)

                    // RFC 9293: and return.
                    return;

                case TIME_WAIT:
                    // RFC 9293: The only thing that can arrive in this state is a retransmission
                    // RFC 9293: of the remote FIN. Acknowledge it,
                    if (seg.isFin()) {
                        final Segment response = formSegment(ctx, tcb.sndNxt(), tcb.rcvNxt(), ACK);
                        LOG.trace("{} Write `{}`.", ctx.channel(), response);
                        tcb.send(ctx, response);

                        // RFC 9293: and restart the 2 MSL timeout.
                        restartTimeWaitTimer(ctx);
                    }
            }
        }

        // RFC 9293: Sixth, check the URG bit:
        // (URG not supported! It is only kept by TCP for legacy reasons, see SHLD-13)

        boolean doFireRead = false;
        boolean doEmitClosing = false;
        try {
            // RFC 9293: Seventh, process the segment text:
            if (seg.content().readableBytes() > 0) {
                switch (state()) {
                    case ESTABLISHED:
                    case FIN_WAIT_1:
                    case FIN_WAIT_2:
                        // RFC 9293: Once in the ESTABLISHED state, it is possible to deliver segment
                        // RFC 9293: data to user RECEIVE buffers. Data from segments can be moved into
                        // RFC 9293: buffers until either the buffer is full or the segment is empty.
                        final boolean outOfOrder = seg.seq() != tcb.rcvNxt();
                        tcb.receiveBuffer().receive(ctx, tcb, seg);

                        // RFC 9293: If the segment empties and carries a PUSH flag, then the user is
                        // RFC 9293: informed, when the buffer is returned, that a PUSH has been
                        // RFC 9293: received.
                        if (seg.isPsh()) {
                            LOG.trace("{} Got `{}`. Add to RCV.BUF and trigger channelRead because PUSH flag is set.", ctx.channel(), seg);
                            doFireRead = true;
                        }
                        else if (readPending) {
                            readPending = false;
                            LOG.trace("{} Got `{}`. Add to RCV.BUF and trigger channelRead because a RECEIVE call has been queued.", ctx.channel(), seg);
                            doFireRead = true;
                        }
                        else {
                            LOG.trace("{} Got `{}`. Add to RCV.BUF and wait for more segment.", ctx.channel(), seg);
                        }

                        // RFC 9293: When the TCP endpoint takes responsibility for delivering the data
                        // RFC 9293: to the user, it must also acknowledge the receipt of the data.

                        // RFC 9293: Once the TCP endpoint takes responsibility for the data, it
                        // RFC 9293: advances RCV.NXT over the data accepted, and adjusts RCV.WND as
                        // RFC 9293: appropriate to the current buffer availability. The total of
                        // RFC 9293: RCV.NXT and RCV.WND should not be reduced.
                        // (is done by ReceiveBuffer#receive)

                        // RFC 9293: A TCP implementation MAY send an ACK segment acknowledging RCV.NXT
                        // RFC 9293: when a valid segment arrives that is in the window but not at the
                        // RFC 9293: left window edge (MAY-13).

                        // RFC 9293: Please note the window management suggestions in Section 3.8.

                        // RFC 9293: Send an acknowledgment of the form:
                        // RFC 9293: <SEQ=SND.NXT><ACK=RCV.NXT><CTL=ACK>
                        final Segment response = formSegment(ctx, tcb.sndNxt(), tcb.rcvNxt(), ACK);

                        // RFC 9293: This acknowledgment should be piggybacked on a segment being
                        // RFC 9293: transmitted if possible without incurring undue delay.
                        // (this is done by TCB/OutgoingSegmentQueue)

                        LOG.trace("{} ACKnowledge receival of `{}` by sending `{}`.", ctx.channel(), seg, response);
                        tcb.send(ctx, response);
                        if (outOfOrder) {
                            // send immediate ACK for out-of-order segments
                            tcb.flush(ctx);
                        }
                        else {
                            // otherwise, flush is automatically performed on channelReadComplete
                            // TODO:
                            //  RFC 9293: an ACK should not be excessively delayed; in particular, the
                            //  RFC 9293: delay MUST be less than 0.5 seconds (MUST-40)
                        }

                        break;

                    case CLOSE_WAIT:
                    case CLOSING:
                    case LAST_ACK:
                    case TIME_WAIT:
                        // RFC 9293: This should not occur since a FIN has been received from the remote
                        // RFC 9293: side. Ignore the segment text.
                        LOG.trace("{} Got `{}`. This should not occur. Ignore the segment text.", ctx.channel(), seg);
                }
            }

            // RFC 9293: Eighth, check the FIN bit:
            if (seg.isFin()) {
                // RFC 9293: Do not process the FIN if the state is CLOSED, LISTEN, or SYN-SENT
                // RFC 9293: since the SEG.SEQ cannot be validated; drop the segment and return.
                // RFC 9293: we cannot validate SEG.SEQ.
                // RFC 9293: drop the segment
                // (can not be reached with our implementation)

                // RFC 9293: If the FIN bit is set, signal the user "connection closing"
                // do not inform user immediately, ensure that current execution is completed first
                doEmitClosing = true;

                // RFC 9293: and return any pending RECEIVEs with same message,
                // (not applicable to us)

                // RFC 9293: advance RCV.NXT over the FIN,
                tcb.receiveBuffer().receive(ctx, tcb, seg);

                // RFC 9293: and send an acknowledgment for the FIN.
                final Segment response = formSegment(ctx, tcb.sndNxt(), tcb.rcvNxt(), ACK);
                LOG.trace("{} Got CLOSE request `{}` from remote peer. ACKnowledge receival with `{}`.", ctx.channel(), seg, response);
                tcb.sendAndFlush(ctx, response);

                // RFC 9293: Note that FIN implies PUSH for any segment text not yet delivered to the user.
                doFireRead = true;

                switch (state()) {
                    case SYN_RECEIVED:
                    case ESTABLISHED:
                        // RFC 9293: Enter the CLOSE-WAIT state.
                        changeState(ctx, CLOSE_WAIT);

                        cancelTimeWaitTimer(ctx);
                        break;

                    case FIN_WAIT_1:
                        if (lessThan(tcb.sndUna(), seg.ack()) && lessThanOrEqualTo(seg.ack(), tcb.sndNxt())) {
                            // RFC 9293: If our FIN has been ACKed (perhaps in this segment),
                            LOG.trace("{} Our FIN has been ACKnowledged. Close channel.", ctx.channel(), seg);

                            // RFC 9293: then enter TIME-WAIT,
                            changeState(ctx, TIME_WAIT);

                            // RFC 9293: start the time-wait timer,
                            restartTimeWaitTimer(ctx);

                            // RFC 9293: turn off the other timers;
                            cancelUserTimer(ctx);
                            cancelRetransmissionTimer(ctx);
                        }
                        else {
                            // RFC 9293: otherwise, enter the CLOSING state.
                            changeState(ctx, CLOSING);
                        }
                        break;

                    case FIN_WAIT_2:
                        // RFC 9293: Enter the TIME-WAIT state.
                        changeState(ctx, TIME_WAIT);

                        // RFC 9293: Start the time-wait timer,
                        restartTimeWaitTimer(ctx);

                        // RFC 9293: turn off the other timers;
                        cancelUserTimer(ctx);
                        cancelRetransmissionTimer(ctx);
                        break;

                    case CLOSE_WAIT:
                        // RFC 9293: Remain in the CLOSE-WAIT state.
                        break;

                    case CLOSING:
                        // RFC 9293: Remain in the CLOSING state.
                        break;

                    case LAST_ACK:
                        // RFC 9293: Remain in the LAST-ACK state.
                        break;

                    case TIME_WAIT:
                        // RFC 9293: Remain in the TIME-WAIT state.
                        // RFC 9293: Restart the 2 MSL time-wait timeout.
                        restartTimeWaitTimer(ctx);
                        break;
                }
            }
        }
        finally {
            // tasks to do at the end to ensure that current execution is already completed
            if (becameEstablished) {
                tcb.trySendingPreviouslyUnsentData(ctx);

                // inform user
                final ConnectionHandshakeCompleted evt = new ConnectionHandshakeCompleted();
                LOG.trace("{} Trigger user event `{}`.", ctx.channel(), evt);
                ctx.fireUserEventTriggered(evt);

                // process queued operations
                ctx.executor().execute(() -> establishedPromise.setSuccess());
            }

            // read at the end to ensure that current execution is already completed
            if (doFireRead) {
                tcb.receiveBuffer().fireRead(ctx, tcb);
            }

            if (doEmitClosing) {
                final ConnectionClosing evt = new ConnectionClosing(state());
                LOG.trace("{} Trigger user event `{}`.", ctx.channel(), evt);
                ctx.fireUserEventTriggered(evt);
            }
        }

        // RFC 9293: and return.
        return;
    }

    /**
     * @return {@code true} if segment processing should be stopped
     */
    private boolean establishedStateProcessing(final ChannelHandlerContext ctx,
                                               final Segment seg) {
        // short overview of RCFs used in this method...
        // RFC 9293: basic TCP
        //  RFC 7323: TCP Timestamps Option and RTTM Mechanism
        //  RFC 5681: congestion control algorithms

        final boolean isRfc9293Duplicate = lessThanOrEqualTo(seg.ack(), tcb.sndUna());

        // RFC 5681: An acknowledgment is considered a "duplicate" in the following algorithms when
        // RFC 5681: (a) the receiver of the ACK has outstanding data, (b) the incoming
        // RFC 5681: acknowledgment carries no data, (c) the SYN and FIN bits are both off, (d) the
        // RFC 5681: acknowledgment number is equal to the greatest acknowledgment received on the
        // RFC 5681: given connection (TCP.UNA from [RFC793]) and (e) the advertised window in the
        // RFC 5681: incoming acknowledgment equals the advertised window in the last incoming acknowledgment.
        final boolean isRfc5681Duplicate = !tcb.retransmissionQueue().isEmpty() &&
                seg.len() == 0 &&
                !seg.isSyn() && !seg.isFin() &&
                seg.ack() == tcb.sndUna() &&
                seg.wnd() == tcb.lastAdvertisedWindow();

        long ackedBytes = 0;
        // RFC 9293: If SND.UNA < SEG.ACK =< SND.NXT, then set SND.UNA <- SEG.ACK.
        if (lessThan(tcb.sndUna(), seg.ack()) && lessThanOrEqualTo(seg.ack(), tcb.sndNxt())) {
            LOG.trace("{} Got `{}`. Advance SND.UNA.", ctx.channel(), seg);
            ackedBytes = tcb.sndUna(ctx, seg.ack());

            // RFC 7323: Also compute a new estimate of round-trip time.
            final int newRto;
            if (config.timestamps()) {
                final TimestampsOption tsOpt = (TimestampsOption) seg.options().get(TIMESTAMPS);
                if (tsOpt != null) {
                    final long rDash;
                    if (tcb.sndTsOk()) {
                        // RFC 7323: If Snd.TS.OK bit is on, use Snd.TSclock - SEG.TSecr;
                        final long segTsEcr = tsOpt.tsEcr;

                        // RFC 6298: (2.3) When a subsequent RTT measurement R' is made,
                        rDash = config.clock().time() - segTsEcr;
                    }
                    else {
                        // RFC 7323: otherwise, use the elapsed time since the first segment in the
                        // RFC 7323: retransmission queue was sent.
                        rDash = config.clock().time() - tcb.retransmissionQueue().firstSegmentSentTime();
                    }
                    LOG.trace("{} RTT measurement: Subsequent RTT measurement R' made = {}ms.", ctx.channel(), rDash);

                    // RFC 6298:       a host MUST set
                    // RFC 6298:       RTTVAR <- (1 - beta) * RTTVAR + beta * |SRTT - R'|
                    // RFC 6298:       SRTT <- (1 - alpha) * SRTT + alpha * R'
                    // RFC 6298:       The value of SRTT used in the update to RTTVAR is its value before
                    // RFC 6298:       updating SRTT itself using the second assignment. That is, updating
                    // RFC 6298:       RTTVAR and SRTT MUST be computed in the above order.
                    // RFC 6298:       The above SHOULD be computed using alpha=1/8 and beta=1/4 (as
                    // RFC 6298:       suggested in [JK88]).
                    // (replaced by RFC 7323)

                    // RFC 7323: Taking multiple RTT samples per window would shorten the history calculated
                    // RFC 7323: by the RTO mechanism in [RFC6298], and the below algorithm aims to maintain
                    // RFC 7323: a similar history as originally intended by [RFC6298].

                    // RFC 7323: It is roughly known how many samples a congestion window worth of data will
                    // RFC 7323: yield, not accounting for ACK compression, and ACK losses. Such events will
                    // RFC 7323: result in more history of the path being reflected in the final value for
                    // RFC 7323: RTO, and are uncritical. This modification will ensure that a similar
                    // RFC 7323: amount of time is taken into account for the RTO estimation, regardless of
                    // RFC 7323: how many samples are taken per window:

                    // RFC 7323: ExpectedSamples = ceiling(FlightSize / (SMSS * 2))
                    final long expectedSamples = max((long) Math.ceil((double) tcb.flightSize() / (tcb.smss() * 2)), 1L);
                    // RFC 7323: alpha' = alpha / ExpectedSamples
                    final double alphaDash = config.alpha() / expectedSamples;
                    // RFC 7323: beta' = beta / ExpectedSamples
                    final double betaDash = config.beta() / expectedSamples;
                    // RFC 7323: Note that the factor 2 in ExpectedSamples is due to "Delayed ACKs".

                    // RFC 7323: Instead of using alpha and beta in the algorithm of [RFC6298], use alpha'
                    // RFC 7323: and beta' instead:
                    // RFC 7323: RTTVAR <- (1 - beta') * RTTVAR + beta' * |SRTT - R'|
                    tcb.rttVar(ctx, (float) ((1 - betaDash) * tcb.rttVar() + betaDash * Math.abs(tcb.sRtt() - rDash)));
                    // RFC 7323: SRTT <- (1 - alpha') * SRTT + alpha' * R'
                    // RFC 7323: (for each sample R')
                    tcb.sRtt(ctx, (float) ((1 - alphaDash) * tcb.sRtt() + alphaDash * rDash));

                    // RFC 6298:       After the computation, a host MUST update
                    // RFC 6298:       RTO <- SRTT + max (G, K*RTTVAR)
                    newRto = (int) Math.ceil(tcb.sRtt() + max(config.clock().g(), config.k() * tcb.rttVar()));

                }
                else {
                    newRto = (int) config.rto().toMillis();
                }
            }
            else {
                // RFC 6298: Note that after retransmitting, once a new RTT measurement is obtained
                // RFC 6298: (which can only happen when new data has been sent and acknowledged), the
                // RFC 6298: computations outlined in Section 2 are performed, including the
                // RFC 6298: computation of RTO, which may result in "collapsing" RTO back down after
                // RFC 6298: it has been subject to exponential back off (rule 5.5).
                newRto = (int) config.rto().toMillis();
            }

            tcb.rto(ctx, newRto);
        }

        // RFC 9293: Any segments on the retransmission queue that are thereby entirely
        // RFC 9293: acknowledged are removed.
        removeAcknowledgedSegmentsFromRetransmissionQueue(ctx);

        // RFC 9293: Users should receive positive acknowledgments for buffers that have been SENT
        // RFC 9293: and fully acknowledged (i.e., SEND buffer should be returned with "ok"
        // RFC 9293: response).
        // (this is done by the write promises)

        if (isRfc5681Duplicate) {
            tcb.incrementDuplicateAcks();
            LOG.trace("{} Congestion Control: Fast Retransmit/Fast Recovery: Got duplicate ACK {}#{}. {} unACKed bytes remaining.", ctx.channel(), seg.ack(), tcb.duplicateAcks(), tcb.flightSize());

            if (tcb.duplicateAcks() == 3) {
                // Reno
                // RFC 5681: 2.  When the third duplicate ACK is received, a TCP MUST
                // RFC 5681:     set ssthresh to no more than the value given in equation (4).
                // RFC 5681:     When [RFC3042] is in use, additional data sent in limited
                // RFC 5681:     transmit MUST NOT be included in this calculation.
                // RFC 5681: ssthresh = max (FlightSize / 2, 2*SMSS)            (4)
                LOG.trace("{} Congestion Control: Fast Recovery: Got third duplicate ACK in a row: Set ssthresh to `max(FlightSize/2,2*SMSS) = max({}/2,2*{})`.", ctx.channel(), tcb.flightSize(), tcb.smss());
                tcb.ssthresh(ctx, max(tcb.flightSize() / 2, 2L * tcb.smss()));

                // RFC 5681: 3. The lost segment starting at SND.UNA MUST be retransmitted
                final Segment retransmission = nextSegmentOnRetransmissionQueue(ctx, tcb);
                assert retransmission != null;
                LOG.trace("{} Congestion Control: Fast Retransmit: Got third duplicate ACK in a row. Retransmit lost segment `{}`.", ctx.channel(), retransmission);
                ctx.writeAndFlush(retransmission);

                // RFC 5681:    and cwnd set to ssthresh plus 3*SMSS. This artificially
                // RFC 5681:    "inflates" the congestion window by the number of segments
                // RFC 5681:    (three) that have left the network and which the receiver has
                // RFC 5681:    buffered.
                LOG.trace("{} Congestion Control: Fast Retransmit: Got third duplicate ACK in a row. Inflate cwnd to `ssthresh plus 3*SMSS`.", ctx.channel());
                tcb.cwnd(ctx, tcb.ssthresh() + 3L * tcb.smss());
            }
            else if (tcb.duplicateAcks() > 3) {
                // RFC 5681: 4. For each additional duplicate ACK received (after the third), cwnd
                // RFC 5681:    MUST be incremented by SMSS. This artificially inflates the
                // RFC 5681:    congestion window in order to reflect the additional segment that
                // RFC 5681:    has left the network.
                LOG.trace("{} Congestion Control: Fast Recovery: Got additional duplicate ACK (#{}). Increment cwnd by SMSS.", ctx.channel(), tcb.duplicateAcks());
                tcb.cwnd(ctx, tcb.cwnd() + tcb.smss());

                // RFC 5681: 5.  When previously unsent data is available and the new value of
                // RFC 5681:     cwnd and the receiver's advertised window allow, a TCP SHOULD
                // RFC 5681:     send 1*SMSS bytes of previously unsent data.
                tcb.trySendingPreviouslyUnsentData(ctx);
            }
        }
        else if (tcb.duplicateAcks() != 0) {
            // Reno
            if (ackedBytes > 0) {
                // RFC 5681: the "fast recovery" algorithm governs the transmission of new data
                // RFC 5681: until a non-duplicate ACK arrives.
                LOG.trace("{} Congestion Control: Fast Recovery: Got non-duplicate ACK. Exit Fast Recovery.", ctx.channel(), state());

                // exit fast recovery procedure
                tcb.resetDuplicateAcks();

                // RFC 5681: 6.  When the next ACK arrives that acknowledges previously
                // RFC 5681:     unacknowledged data, a TCP MUST set cwnd to ssthresh (the value
                // RFC 5681:     set in step 2). This is termed "deflating" the window.
                LOG.trace("{} Congestion Control: Fast Recovery: Got non-duplicate ACK. Deflate cwnd to ssthresh.", ctx.channel());
                tcb.cwnd(ctx, tcb.ssthresh());
            }
        }
        else {
            if (tcb.doSlowStart()) {
                // RFC 5681: During slow start, a TCP increments cwnd by at most SMSS bytes for
                // RFC 5681: each ACK received that cumulatively acknowledges new data.
                // RFC 5681: While traditionally TCP implementations have increased cwnd by
                // RFC 5681: precisely SMSS bytes upon receipt of an ACK covering new data, we
                // RFC 5681: RECOMMEND that TCP implementations increase cwnd, per:
                // RFC 5681:
                // RFC 5681:    cwnd += min (N, SMSS)                      (2)
                // RFC 5681:
                // RFC 5681: where N is the number of previously unacknowledged bytes acknowledged
                // RFC 5681: in the incoming ACK.
                final long n = ackedBytes;
                if (n > 0) {
                    final long increment = min(n, tcb.smss());
                    LOG.trace("{} Congestion Control: Slow Start: {} new bytes has ben ACKed. Increase cwnd by {}.", ctx.channel(), ackedBytes, increment);
                    tcb.cwnd(ctx, tcb.cwnd() + increment);
                }
            }
            else {
                // RFC 5681: During congestion avoidance, cwnd is incremented by roughly 1
                // RFC 5681: full-sized segment per round-trip time (RTT).

                // RFC 5681: The RECOMMENDED way to increase cwnd during congestion avoidance is to
                // RFC 5681: count the number of bytes that have been acknowledged by ACKs for new
                // RFC 5681: data. (A drawback of this implementation is that it requires
                // RFC 5681: maintaining an additional state variable.) When the number of bytes
                // RFC 5681: acknowledged reaches cwnd, then cwnd can be incremented by up to SMSS
                // RFC 5681: bytes. Note that during congestion avoidance, cwnd MUST NOT be
                // RFC 5681: increased by more than SMSS bytes per RTT. This method both allows TCPs
                // RFC 5681: to increase cwnd by one segment per RTT in the face of delayed ACKs and
                // RFC 5681: provides robustness against ACK Division attacks.

                // RFC 5681: Another common formula that a TCP MAY use to update cwnd during
                // RFC 5681: congestion avoidance is given in equation (3):
                // RFC 5681:
                // RFC 5681:    cwnd += SMSS*SMSS/cwnd                     (3)
                // RFC 5681:
                // RFC 5681: This adjustment is executed on every incoming ACK that acknowledges new
                // RFC 5681: data. Equation (3) provides an acceptable approximation to the
                // RFC 5681: underlying principle of increasing cwnd by 1 full-sized segment per
                // RFC 5681: RTT. (Note that for a connection in which the receiver is acknowledging
                // RFC 5681: every-other packet, (3) is less aggressive than allowed -- roughly
                // RFC 5681: increasing cwnd every second RTT.)

                // RFC 5681: Implementation Note: Since integer arithmetic is usually used in TCP
                // RFC 5681: implementations, the formula given in equation (3) can fail to increase
                // RFC 5681: cwnd when the congestion window is larger than SMSS*SMSS. If the above
                // RFC 5681: formula yields 0, the result SHOULD be rounded up to 1 byte.
                final long increment = (long) Math.ceil(((double) tcb.smss() * tcb.smss()) / tcb.cwnd());
                LOG.trace("{} Congestion Control: Congestion Avoidance: {} new bytes has ben ACKed. Increase cwnd by {}.", ctx.channel(), ackedBytes, increment);
                tcb.cwnd(ctx, tcb.cwnd() + increment);
            }
        }

        if (isRfc9293Duplicate) {
            // RFC 9293: If the ACK is a duplicate (SEG.ACK =< SND.UNA), it can be ignored.
            LOG.trace("{} As SEG `{}` does not acknowledge any new data, we can now stop processing this SEG's acknowledgement.", ctx.channel(), seg);
            return false;
        }
        tcb.lastAdvertisedWindow(seg.wnd());

        if (greaterThan(seg.ack(), tcb.sndNxt())) {
            // RFC 9293: If the ACK acks something not yet sent (SEG.ACK > SND.NXT),
            LOG.trace("{} something not yet sent has been ACKed: SND.NXT={}; SEG={}", ctx.channel(), tcb.sndNxt(), seg);

            // RFC 9293: then send an ACK,
            final Segment response = formSegment(ctx, tcb.sndNxt(), tcb.rcvNxt(), ACK);
            LOG.trace("{} Write `{}`.", ctx.channel(), response);
            tcb.send(ctx, response);

            // RFC 9293: drop the segment,
            // (this is handled by handler's auto release of all arrived segments)

            // RFC 9293: and return.
            return true;
        }

        if (lessThanOrEqualTo(tcb.sndUna(), seg.ack()) && lessThanOrEqualTo(seg.ack(), tcb.sndNxt())) {
            // RFC 9293: If SND.UNA =< SEG.ACK =< SND.NXT, the send window should be updated.
            if (lessThan(tcb.sndWl1(), seg.seq()) || (tcb.sndWl1() == seg.seq() && lessThanOrEqualTo(tcb.sndWl2(), seg.ack()))) {
                // RFC 9293: If (SND.WL1 < SEG.SEQ or (SND.WL1 = SEG.SEQ and SND.WL2 =< SEG.ACK)),
                // RFC 9293: set SND.WND <- SEG.WND,
                tcb.sndWnd(ctx, seg.wnd());
                // RFC 9293: set SND.WL1 <- SEG.SEQ,
                tcb.sndWl1(seg.seq());
                // RFC 9293: and set SND.WL2 <- SEG.ACK.
                tcb.sndWl2(seg.ack());

                if (tcb.sndWnd() == 0) {
                    LOG.trace("{} SND.WND is now zero. Create zero-window probing timer.", ctx.channel());
                    startZeroWindowProbing(ctx);
                }
                else {
                    LOG.trace("{} SND.WND is not longer zero. Cancel zero-window probing timer, if present.", ctx.channel());
                    cancelZeroWindowProbing(ctx);
                }
            }
        }

        // RFC 9293: Note that SND.WND is an offset from SND.UNA, that SND.WL1 records the
        // RFC 9293: sequence number of the last segment used to update SND.WND, and that
        // RFC 9293: SND.WL2 records the acknowledgment number of the last segment used to
        // RFC 9293: update SND.WND. The check here prevents using old segments to update
        // RFC 9293: the window.

        if (ackedBytes > 0) {
            // something was ACKed and therefore has left the network. Try to send more.
            tcb.trySendingPreviouslyUnsentData(ctx);
        }

        return false;
    }

    Segment formSegment(final ChannelHandlerContext ctx,
                        final int srcPort,
                        final int dstPort,
                        final long seq,
                        final long ack,
                        final byte ctl,
                        final ByteBuf data) {
        final EnumMap<SegmentOption, Object> options = new EnumMap<>(SegmentOption.class);
        // SEG.WND is set in OutgoingSegmentQueue#flush
        final Segment seg = new Segment(srcPort, dstPort, seq, ack, ctl, 0, options, data);

        if ((ctl & SYN) != 0) {
            // RFC 9293: TCP implementations SHOULD send an MSS Option in every SYN segment
            // RFC 9293: when its receive MSS differs from the default 536 for IPv4 or 1220 for IPv6
            // RFC 9293: (SHLD-5),
            // (not applicable to us)

            // RFC 9293: and MAY send it always (MAY-3).

            // RFC 9293: The MSS value to be sent in an MSS Option must be less than or equal to:
            // RFC 9293: MMS_R - 20
            // 20 is the TCP header size
            final int mss = config.mmsR() - SEG_HDR_SIZE;
            assert mss > 0;
            options.put(MAXIMUM_SEGMENT_SIZE, mss);
        }

        if (config.timestamps()) {
            if (tcb != null && tcb.sndTsOk()) {
                // RFC 9293: the TSopt MUST be sent in every non-<RST> segment for the duration of
                // RFC 9293: the connection, and SHOULD be sent in an <RST> segment (see Section 5.2
                // RFC 9293: for details). The TCP SHOULD remember this state by setting a flag,
                // RFC 9293: referred to as Snd.TS.OK, to one.
                final TimestampsOption tsOpt = new TimestampsOption(config.clock().time(), tcb.tsRecent());
                options.put(TIMESTAMPS, tsOpt);
                if ((ctl & ACK) != 0) {
                    tcb.lastAckSent(ctx, ack);
                }
                LOG.trace("{} RTT measurement: > {}", ctx.channel(), tsOpt);
            }
            else if ((ctl & SYN) != 0) {
                // RFC 9293: Once TSopt has been successfully negotiated, that is both <SYN> and
                // RFC 9293: <SYN,ACK> contain TSopt,
                final TimestampsOption tsOpt = new TimestampsOption(config.clock().time());
                options.put(TIMESTAMPS, tsOpt);
            }
        }

        return seg;
    }

    Segment formSegment(final ChannelHandlerContext ctx,
                        final long seq,
                        final long ack,
                        final byte ctl,
                        final ByteBuf data) {
        return formSegment(ctx, tcb.localPort(), tcb.remotePort(), seq, ack, ctl, data);
    }

    private Segment formSegment(final ChannelHandlerContext ctx,
                                final int srcPort,
                                final int dstPort,
                                final long seq,
                                final long ack,
                                final byte ctl) {
        return formSegment(ctx, srcPort, dstPort, seq, ack, ctl, Unpooled.EMPTY_BUFFER);
    }

    private Segment formSegment(final ChannelHandlerContext ctx,
                                final long seq,
                                final long ack,
                                final byte ctl) {
        return formSegment(ctx, tcb.localPort(), tcb.remotePort(), seq, ack, ctl);
    }

    private Segment formSegment(final ChannelHandlerContext ctx,
                                final int srcPort,
                                final int dstPort,
                                final long seq,
                                final byte ctl) {
        return formSegment(ctx, srcPort, dstPort, seq, 0, ctl);
    }

    private Segment formSegment(final ChannelHandlerContext ctx,
                                final long seq,
                                final byte ctl) {
        return formSegment(ctx, tcb.localPort(), tcb.remotePort(), seq, ctl);
    }

    long segmentizeAndSendData(final ChannelHandlerContext ctx, long bytes) {
        if (segmentizedFuture != null && segmentizedRemainingBytes < bytes) {
            bytes = segmentizedRemainingBytes;
        }

        final AtomicBoolean doPush = new AtomicBoolean();
        final ChannelPromise promise = ctx.newPromise();
        final ByteBuf data = tcb.sendBuffer().read(bytes, doPush, promise);
        byte ctl = ACK;
        if (doPush.get()) {
            ctl |= PSH;
        }
        final Segment segment = formSegment(ctx, tcb.sndNxt(), tcb.rcvNxt(), ctl, data);
        tcb.send(ctx, segment, promise);
        if (segmentizedFuture != null) {
            segmentizedRemainingBytes -= bytes;
            if (segmentizedRemainingBytes == 0) {
                segmentizedFuture.trySuccess();
            }
        }
        return bytes;
    }

    /*
     * Timeouts
     */

    /**
     * USER TIMEOUT event as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.8">RFC
     * 9293, Section 3.10.8</a>.
     */
    void userTimeout(final ChannelHandlerContext ctx) {
        userTimer = null;

        // RFC 9293: For any state if the user timeout expires,
        LOG.trace("{} USER timer timeout after {}ms. Close channel.", ctx.channel(), config.userTimeout().toMillis());
        // RFC 9293: flush all queues,
        final ConnectionAbortedDueToUserTimeoutException e = new ConnectionAbortedDueToUserTimeoutException(ctx.channel(), config.userTimeout());
        if (tcb != null) {
            tcb.sendBuffer().fail(e);
            tcb.retransmissionQueue().release();
            tcb.receiveBuffer().release();
        }

        // RFC 9293: signal the user "error: connection aborted due to user timeout" in
        // RFC 9293: general and for any outstanding calls,
        ctx.fireExceptionCaught(e);

        // RFC 9293: delete the TCB,
        deleteTcb();

        // RFC 9293: enter the CLOSED state,
        // (this happens implicitly when TCB is deleted)

        ctx.close(closedPromise);

        // RFC 9293: and return.
        return;
    }

    void cancelUserTimer(final ChannelHandlerContext ctx) {
        if (userTimer != null) {
            userTimer.cancel(false);
            userTimer = null;
            LOG.trace("{} USER timer cancelled.", ctx.channel(), state());
        }
    }

    void restartUserTimer(final ChannelHandlerContext ctx) {
        if (userTimer != null) {
            userTimer.cancel(false);
            LOG.trace("{} USER timer restarted: Timeout {}ms.", ctx.channel(), config.userTimeout().toMillis());
        }
        else {
            LOG.trace("{} USER timer created: Timeout {}ms.", ctx.channel(), config.userTimeout().toMillis());
        }

        userTimer = ctx.executor().schedule(() -> userTimeout(ctx), config.userTimeout().toMillis(), MILLISECONDS);
    }

    void startRetransmissionTimer(final ChannelHandlerContext ctx,
                                  final TransmissionControlBlock tcb) {
        assert retransmissionTimer == null;

        final long rto = tcb.rto();
        LOG.trace("{} RETRANSMISSION timer created: Timeout {}ms.", ctx.channel(), rto);
        retransmissionTimer = ctx.executor().schedule(() -> retransmissionTimeout(ctx, tcb, rto), rto, MILLISECONDS);
    }

    /**
     * RETRANSMISSION TIMEOUT event as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.8">RFC
     * 9293, Section 3.10.8</a>.
     */
    void retransmissionTimeout(final ChannelHandlerContext ctx,
                               final TransmissionControlBlock tcb,
                               final long rto) {
        retransmissionTimer = null;

        // RFC 6298: (5.4) Retransmit the earliest segment that has not been acknowledged by the
        // RFC 6298:       TCP receiver.
        final Segment retransmission = nextSegmentOnRetransmissionQueue(ctx, tcb);
        assert retransmission != null;
        LOG.trace("{} RETRANSMISSION timer timeout after {}ms! Retransmit `{}`. {} unACKed bytes remaining.", ctx.channel(), rto, retransmission, tcb.flightSize());
        ctx.writeAndFlush(retransmission);

        // RFC 6298: (5.5) The host MUST set RTO <- RTO * 2 ("back off the timer"). The maximum
        // RFC 6298:       value discussed in (2.5) above may be used to provide an upper bound
        // RFC 6298:       to this doubling operation.
        LOG.trace("{} RETRANSMISSION timer timeout: Double RTO (\"back off the timer\").", ctx.channel());
        tcb.rto(ctx, tcb.rto() * 2);

        // RFC 6298: (5.6) Start the retransmission timer, such that it expires after RTO
        // RFC 6298:       seconds (for the value of RTO after the doubling operation outlined
        // RFC 6298:       in 5.5).
        startRetransmissionTimer(ctx, tcb);

        // RFC 5681: When a TCP sender detects segment loss using the retransmission timer and
        // RFC 5681: the given segment has not yet been resent by way of the retransmission
        // RFC 5681: timer, the value of ssthresh MUST be set to no more than the value given in
        // RFC 5681: equation (4):
        // RFC 5681: ssthresh = max (FlightSize / 2, 2*SMSS) (4)
        LOG.trace("{} Congestion Control: Segment loss. Set ssthresh to `max(FlightSize/2,2*SMSS) = max({}/2,2*{})`.", ctx.channel(), tcb.flightSize(), tcb.smss());
        tcb.ssthresh(ctx, max(tcb.flightSize() / 2, 2L * tcb.smss()));

        // RFC 5681: Furthermore, upon a timeout (as specified in [RFC2988]) cwnd MUST be set to
        // RFC 5681: no more than the loss window, LW, which equals 1 full-sized segment
        // RFC 5681: (regardless of the value of IW).  Therefore, after retransmitting the
        // RFC 5681: dropped segment the TCP sender uses the slow start algorithm to increase
        // RFC 5681: the window from 1 full-sized segment to the new value of ssthresh, at which
        // RFC 5681: point congestion avoidance again takes over.
        LOG.trace("{} Congestion Control: Timeout. Set cmd to no more than the loss window, which equals to 1 full-sized segment", ctx.channel());
        tcb.cwnd(ctx, tcb.effSndMss());
    }

    private Segment nextSegmentOnRetransmissionQueue(final ChannelHandlerContext ctx,
                                                     final TransmissionControlBlock tcb) {
        final Segment seg = tcb.retransmissionQueue().nextSegment();
        if (seg != null) {
            final ByteBuf copy = seg.content().copy();
            return formSegment(ctx, seg.seq(), seg.ack(), seg.ctl(), copy);
        }
        return null;
    }

    void cancelRetransmissionTimer(final ChannelHandlerContext ctx) {
        if (retransmissionTimer != null) {
            retransmissionTimer.cancel(false);
            retransmissionTimer = null;
            LOG.trace("{} RETRANSMISSION timer cancelled.", ctx.channel());
        }
    }

    void restartRetransmissionTimer(final ChannelHandlerContext ctx,
                                    final TransmissionControlBlock tcb) {
        if (retransmissionTimer != null) {
            retransmissionTimer.cancel(false);
        }

        final long rto = tcb.rto();
        LOG.trace("{} RETRANSMISSION timer restarted: Timeout {}ms.", ctx.channel(), rto);
        retransmissionTimer = ctx.executor().schedule(() -> retransmissionTimeout(ctx, tcb, rto), rto, MILLISECONDS);
    }

    private void restartTimeWaitTimer(final ChannelHandlerContext ctx) {
        // RFC 9293: When a connection is closed actively, it MUST linger in the TIME-WAIT state for
        // RFC 9293: a time 2xMSL (Maximum Segment Lifetime) (MUST-13)
        final long timeWaitTimeout = config.msl().multipliedBy(2).toMillis();
        if (timeWaitTimer != null) {
            timeWaitTimer.cancel(false);
            LOG.trace("{} USER timer restarted: Timeout {}ms.", ctx.channel(), timeWaitTimeout);
        }
        else {
            LOG.trace("{} USER timer created: Timeout {}ms.", ctx.channel(), timeWaitTimeout);
        }

        timeWaitTimer = ctx.executor().schedule(() -> timeWaitTimeout(ctx), timeWaitTimeout, MILLISECONDS);
    }

    /**
     * TIME-WAIT TIMEOUT event as described in <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.10.8">RFC
     * 9293, Section 3.10.8</a>.
     */
    void timeWaitTimeout(final ChannelHandlerContext ctx) {
        timeWaitTimer = null;

        final long timeWaitTimeout = config.msl().multipliedBy(2).toMillis();
        LOG.trace("{} TIME-WAIT timer timeout after {}ms. Close channel.", ctx.channel(), timeWaitTimeout);

        // RFC 9293: If the time-wait timeout expires on a connection,
        // RFC 9293: delete the TCB,
        deleteTcb();

        // RFC 9293: enter the CLOSED state,
        // (this happens implicitly when TCB is deleted)

        ctx.close(closedPromise);

        // RFC 9293: and return.
        return;
    }

    private void cancelTimeWaitTimer(final ChannelHandlerContext ctx) {
        if (timeWaitTimer != null) {
            timeWaitTimer.cancel(false);
            timeWaitTimer = null;
            LOG.trace("{} TIME-WAIT timer cancelled.", ctx.channel(), state());
        }
    }

    private void startZeroWindowProbing(final ChannelHandlerContext ctx) {
        if (zeroWindowProber == null && !tcb.sendBuffer().isEmpty()) {
            // RFC 9293: The transmitting host SHOULD send the first zero-window probe when a zero
            // RFC 9293: window has existed for the retransmission timeout period (SHLD-29)
            // RFC 9293: (Section 3.8.1), and SHOULD increase exponentially the interval between
            // RFC 9293: successive probes (SHLD-30).
            final long rto = tcb.rto();
            LOG.trace("{} Zero-window probing timer created: Timeout {}ms.", ctx.channel(), rto);
            zeroWindowProber = ctx.executor().schedule(() -> {
                zeroWindowProber = null;

                LOG.trace("{} Zero-window has existed for {}ms. Send a 1 byte probe to check if receiver is really still unable to receive data.", ctx.channel(), rto);
                if (segmentizeAndSendData(ctx, 1) > 0) {
                    tcb.flush(ctx);
                }
            }, rto, MILLISECONDS);
        }
    }

    private void cancelZeroWindowProbing(final ChannelHandlerContext ctx) {
        if (zeroWindowProber != null) {
            zeroWindowProber.cancel(false);
            zeroWindowProber = null;
            LOG.trace("{} Zero-window probing timer cancelled.", ctx.channel(), state());
        }
    }

    private State state() {
        if (tcb == null) {
            return CLOSED;
        }
        else {
            return tcb.state();
        }
    }
}
