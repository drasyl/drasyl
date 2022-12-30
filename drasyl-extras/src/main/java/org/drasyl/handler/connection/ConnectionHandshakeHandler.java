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
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.function.LongSupplier;

import static io.netty.channel.ChannelFutureListener.CLOSE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.connection.RetransmissionTimeoutApplier.ALPHA;
import static org.drasyl.handler.connection.RetransmissionTimeoutApplier.BETA;
import static org.drasyl.handler.connection.RetransmissionTimeoutApplier.LOWER_BOUND;
import static org.drasyl.handler.connection.RetransmissionTimeoutApplier.UPPER_BOUND;
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
import static org.drasyl.util.RandomUtil.randomInt;
import static org.drasyl.util.SerialNumberArithmetic.add;
import static org.drasyl.util.SerialNumberArithmetic.greaterThan;
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
    public static final ConnectionHandshakeException CONNECTION_REFUSED_EXCEPTION = new ConnectionHandshakeException("Connection refused");
    public static final ClosedChannelException CONNECTION_CLOSED_ERROR = new ClosedChannelException();
    static final Logger LOG = LoggerFactory.getLogger(ConnectionHandshakeHandler.class);
    static final int SEQ_NO_SPACE = 32;
    private static final ConnectionHandshakeIssued HANDSHAKE_ISSUED_EVENT = new ConnectionHandshakeIssued();
    private static final ConnectionHandshakeClosing HANDSHAKE_CLOSING_EVENT = new ConnectionHandshakeClosing();
    private static final ConnectionHandshakeException CONNECTION_CLOSING_ERROR = new ConnectionHandshakeException("Connection closing");
    private static final ConnectionHandshakeException CONNECTION_RESET_EXCEPTION = new ConnectionHandshakeException("Connection reset");
    private final Duration userTimeout;
    private final LongSupplier issProvider;
    private final boolean activeOpen;
    private final int mss; // maximum segment size
    protected ScheduledFuture<?> userTimeoutFuture;
    State state;
    private TransmissionControlBlock tcb;
    private ChannelPromise userCallFuture;
    private long flushUntil = -1;
    private long rtt = -1;
    private long srtt;
    private long rto;

    /**
     * @param userTimeout time in ms in which a handshake must taken place after issued
     * @param activeOpen  if {@code true} a handshake will be issued on
     *                    {@link #channelActive(ChannelHandlerContext)}. Otherwise the remote peer
     *                    must initiate the handshake
     */
    public ConnectionHandshakeHandler(final Duration userTimeout,
                                      final boolean activeOpen) {
        // window size sollte ein vielfaches von mss betragen
        this(userTimeout, () -> randomInt(Integer.MAX_VALUE - 1), activeOpen, CLOSED, 1254 * 10, null, 1254); // FIXME: good default values?
    }

    /**
     * @param userTimeout time in ms in which a handshake must taken place after issued
     * @param issProvider Provider to generate the initial send sequence number
     * @param activeOpen  Initiate active OPEN handshake process automatically on
     *                    {@link #channelActive(ChannelHandlerContext)}
     * @param state       Current synchronization state
     * @param mss         Maximum segment size
     * @param windowSize
     */
    @SuppressWarnings("java:S107")
    ConnectionHandshakeHandler(final Duration userTimeout,
                               final LongSupplier issProvider,
                               final boolean activeOpen,
                               final State state,
                               final int mss,
                               final TransmissionControlBlock tcb,
                               final int windowSize) {
        this.userTimeout = requireNonNegative(userTimeout);
        this.issProvider = requireNonNull(issProvider);
        this.activeOpen = activeOpen;
        this.state = requireNonNull(state);
        this.tcb = tcb;
        this.mss = mss;
    }

    public TransmissionControlBlock tcb() {
        return tcb;
    }

    public long rto() {
        return rto;
    }

    @Override
    public void close(final ChannelHandlerContext ctx,
                      final ChannelPromise promise) throws Exception {
        userCallClose(ctx, promise);
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (!(msg instanceof ByteBuf)) {
            final UnsupportedMessageTypeException exception = new UnsupportedMessageTypeException(msg, ByteBuf.class);
            ReferenceCountUtil.release(msg);
            promise.setFailure(exception);
        }
        else {
            // user call WRITE
            userCallSend(ctx, (ByteBuf) msg, promise);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        tryFlushingSendBuffer(ctx, true);
        tcb.outgoingSegmentQueue().flush(ctx);
    }

    private void tryFlushingSendBuffer(final ChannelHandlerContext ctx, boolean newFlush) {
        if (newFlush) {
            // merke dir wie viel byes wir jetzt im buffer haben und verwende auch nur bis dahin
            flushUntil = add(tcb.sndNxt, tcb.sendBuffer().readableBytes(), SEQ_NO_SPACE);
        }

        if (state != ESTABLISHED || flushUntil == -1 || tcb.sendBuffer().isEmpty()) {
            return;
        }

        int allowedBytesForNewTransmission = tcb.sequenceNumbersAllowedForNewDataTransmission();
        final int allowedBytesToFlush = (int) (this.flushUntil - tcb.sndNxt);
        LOG.trace("{}[{}] Flush of write buffer was triggered. {} sequence numbers are allowed to write to the network. {} bytes in send buffer. {} bytes allowed to flush. MSS={}", ctx.channel(), state, allowedBytesForNewTransmission, tcb.sendBuffer().readableBytes(), allowedBytesToFlush, mss);

        final int readableBytesInBuffer = tcb.sendBuffer().readableBytes();
        int remainingBytes = Math.min(Math.min(allowedBytesForNewTransmission, readableBytesInBuffer), allowedBytesToFlush);

        LOG.trace("{}[{}] Write {} bytes to network", ctx.channel(), state, remainingBytes);
        final boolean somethingWritten = remainingBytes > 0;
        while (remainingBytes > 0) {
            final ChannelPromise ackPromise = ctx.newPromise();
            final ByteBuf data = tcb.sendBuffer().remove(Math.min(mss, remainingBytes), ackPromise);
            remainingBytes -= data.readableBytes();
            final boolean isLast = remainingBytes == 0 || tcb.sendBuffer().isEmpty();
            final ConnectionHandshakeSegment seg;
            if (isLast) {
                seg = ConnectionHandshakeSegment.pshAck(tcb.sndNxt, tcb.rcvNxt, data);
            }
            else {
                seg = ConnectionHandshakeSegment.ack(tcb.sndNxt, tcb.rcvNxt, data);
            }
            tcb.sndNxt = add(tcb.sndNxt, data.readableBytes(), SEQ_NO_SPACE);
            LOG.trace("{}[{}] Write `{}` to network ({} bytes allowed to write to network left. {} writes will be contained in retransmission queue).", ctx.channel(), state, seg, tcb.sequenceNumbersAllowedForNewDataTransmission(), tcb.retransmissionQueue().size() + 1);
            tcb.outgoingSegmentQueue().add(seg, ctx.newPromise(), ackPromise);
        }
    }

    private void userCallSend(final ChannelHandlerContext ctx,
                              final ByteBuf data,
                              final ChannelPromise promise) {
        switch (state) {
            case CLOSED:
                data.release();
                promise.setFailure(new ConnectionHandshakeException("Connection does not exist"));
                break;

            case LISTEN:
                // channel was in passive OPEN mode. Now switch to active OPEN handshake
                LOG.trace("{}[{}] Write was performed while we're in passive OPEN mode. Switch to active OPEN mode, enqueue write operation, and initiate OPEN process.", ctx.channel(), state);

                // save promise for later, as it we need ACKnowledgment from remote peer
                userCallFuture = ctx.newPromise();

                // enqueue our write for transmission after entering ESTABLISHED state
                performActiveOpen(ctx);
                tcb.sendBuffer().add(data, promise);
                break;

            case SYN_SENT:
            case SYN_RECEIVED:
                // Queue the data for transmission after entering ESTABLISHED state.
                tcb.sendBuffer().add(data, promise);
                break;

            case ESTABLISHED:
                // add this message to or send buffer, to allow it to be sent together with other
                // data for transmission efficiency.
                LOG.trace("{}[{}] As connection is established, we can add the message `{}` to the write queue and trigger a queue flush.", ctx.channel(), state, data);
                tcb.sendBuffer().add(data, promise);
                break;

            default:
                // FIN-WAIT-1
                // FIN-WAIT-2
                // CLOSING
                // LAST-ACK
                LOG.trace("{}[{}] Channel is in process of being closed. Drop write `{}`.", ctx.channel(), state, data);
                data.release();
                promise.setFailure(CONNECTION_CLOSING_ERROR);
                break;
        }
    }

    private void performActiveOpen(final ChannelHandlerContext ctx) {
        createTcb(ctx);

        // send SYN
        final ConnectionHandshakeSegment seg = ConnectionHandshakeSegment.syn(tcb.iss);
        LOG.trace("{}[{}] Initiate OPEN process by sending `{}`.", ctx.channel(), state, seg);
        tcb.outgoingSegmentQueue().addAndFlush(ctx, seg);

        switchToNewState(ctx, SYN_SENT);

        // start user timeout guard
        applyUserTimeout(ctx, "OPEN", userCallFuture);

        ctx.fireUserEventTriggered(HANDSHAKE_ISSUED_EVENT);
    }

    private void createTcb(final ChannelHandlerContext ctx) {
        tcb = new TransmissionControlBlock(ctx.channel(), issProvider.getAsLong());
    }

    @SuppressWarnings("java:S128")
    private void userCallClose(final ChannelHandlerContext ctx,
                               final ChannelPromise promise) {
        LOG.trace("{}[{}] CLOSE call received.", ctx.channel(), state);

        switch (state) {
            case CLOSED:
                LOG.trace("{}[{}] Channel is already closed. Pass close call further through the pipeline.", ctx.channel(), state);
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
                // process with ESTABLISHED part

            case ESTABLISHED:
                // save promise for later, as it we need ACKnowledgment from remote peer
                userCallFuture = promise;

                // signal user connection closing
                ctx.fireUserEventTriggered(HANDSHAKE_CLOSING_EVENT);

                final ConnectionHandshakeSegment seg = ConnectionHandshakeSegment.finAck(tcb.sndNxt, tcb.rcvNxt);
                tcb.sndNxt++;
                LOG.trace("{}[{}] Initiate CLOSE sequence by sending `{}`.", ctx.channel(), state, seg);
                tcb.outgoingSegmentQueue().addAndFlush(ctx, seg);

                switchToNewState(ctx, FIN_WAIT_1);

                applyUserTimeout(ctx, "CLOSE", promise);
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

    /*
     * User Calls
     */

    private void switchToNewState(final ChannelHandlerContext ctx, final State newState) {
        LOG.trace("{}[{} -> {}] Switched to new state.", ctx.channel(), state, newState);
        state = newState;
    }

    private void applyUserTimeout(final ChannelHandlerContext ctx,
                                  final String userCall,
                                  final ChannelPromise promise) {
        if (userTimeout.toMillis() > 0) {
            if (userTimeoutFuture != null) {
                userTimeoutFuture.cancel(false);
            }
            userTimeoutFuture = ctx.executor().schedule(() -> {
                LOG.trace("{}[{}] User timeout for {} user call expired after {}ms. Close channel.", ctx.channel(), state, userCall, userTimeout.toMillis());
                switchToNewState(ctx, CLOSED);
                promise.tryFailure(new ConnectionHandshakeException("User timeout for " + userCall + " user call after " + userTimeout + "ms. Close channel."));
                ctx.channel().close();
            }, userTimeout.toMillis(), MILLISECONDS);
            userTimeoutFuture.addListener(new FutureListener() {
                @Override
                public void operationComplete(Future future) {
                    if (future.isCancelled() && "CLOSE".equals(userCall)) {
                        LOG.trace("{}[{}] User timeout for {} user call has been cancelled (vermutlich EmbeddedChannel close Aufruf?). Close channel immediately.", ctx.channel(), state, userCall, userTimeout);
                        switchToNewState(ctx, CLOSED);
                        ctx.channel().close();
                    }
                }
            });
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        gehtLos(ctx);
        ctx.fireChannelActive();
    }

    private void gehtLos(ChannelHandlerContext ctx) {
        srtt = (long) (ALPHA * srtt + (1 - ALPHA) * rtt);
        rto = Math.min(UPPER_BOUND, Math.max(LOWER_BOUND, (long) (BETA * srtt)));

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
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            gehtLos(ctx);
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        // cancel all timeout guards
        cancelTimeoutGuards();

        tcb.outgoingSegmentQueue().releaseAndFailAll(CONNECTION_CLOSED_ERROR);

        ctx.fireChannelInactive();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // cancel all timeout guards
        cancelTimeoutGuards();

        tcb.outgoingSegmentQueue().releaseAndFailAll(CONNECTION_CLOSED_ERROR);
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

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        tryFlushingSendBuffer(ctx, false);
        tcb.outgoingSegmentQueue().flush(ctx);

        ctx.fireChannelReadComplete();
    }

    private void segmentArrives(final ChannelHandlerContext ctx,
                                final ConnectionHandshakeSegment seg) {
        ReferenceCountUtil.touch(seg, "segmentArrives");
        LOG.trace("{}[{}] Read `{}`.", ctx.channel(), state, seg);

        // RTTM
        if (tcb != null) {
            tcb.rttMeasurement().segmentArrives(seg);
        }
        if (seg.tsEcr() > 0) {
            rtt = (int) (System.nanoTime() / 1_000_000 - seg.tsEcr());
            srtt = (long) (ALPHA * srtt + (1 - ALPHA) * rtt);
            rto = Math.min(UPPER_BOUND, Math.max(LOWER_BOUND, (long) (BETA * srtt)));
            LOG.trace("{}[{}] New RTT sample: RTT={}ms; SRTT={}ms; RTO={}ms", ctx.channel(), state, rtt, srtt, rto);
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

    /*
     * Arriving Segments
     */

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
        tcb.outgoingSegmentQueue().add(ctx, response);
    }

    private void segmentArrivesOnListenState(final ChannelHandlerContext ctx,
                                             final ConnectionHandshakeSegment seg) {
        ReferenceCountUtil.touch(seg, "segmentArrivesOnListenState");
        // check RST
        if (seg.isRst()) {
            // as we are already/still in CLOSED state, we can ignore the RST
            return;
        }

        // check ACK
        if (seg.isAck()) {
            // we are on a state were we have never sent anything that must be ACKed
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.rst(seg.ack());
            LOG.trace("{}[{}] We are on a state were we have never sent anything that must be ACKnowledged. Send RST `{}`.", ctx.channel(), state, response);
            tcb.outgoingSegmentQueue().add(ctx, response);
            return;
        }

        // check SYN
        if (seg.isSyn()) {
            LOG.trace("{}[{}] Remote peer initiates handshake by sending a SYN `{}` to us.", ctx.channel(), state, seg);

            if (userTimeout.toMillis() > 0) {
                // create handshake timeguard
                ctx.executor().schedule(() -> {
                    if (state != ESTABLISHED && state != CLOSED) {
                        LOG.trace("{}[{}] Handshake initiated by remote port has not been completed within {}ms. Abort handshake, close channel.", ctx.channel(), state, userTimeout);
                        switchToNewState(ctx, CLOSED);
                        ctx.channel().close();
                    }
                }, userTimeout.toMillis(), MILLISECONDS);
            }

            // yay, peer SYNced with us
            switchToNewState(ctx, SYN_RECEIVED);

            createTcb(ctx);

            // synchronize receive state
            tcb.rcvNxt = add(seg.seq(), 1, SEQ_NO_SPACE);
            tcb.irs = seg.seq();

            // send SYN/ACK
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.synAck(tcb.iss, tcb.rcvNxt);
            LOG.trace("{}[{}] ACKnowlede the received segment and send our SYN `{}`.", ctx.channel(), state, response);
            tcb.outgoingSegmentQueue().add(ctx, response);
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
        if (seg.isAck() && (lessThanOrEqualTo(seg.ack(), tcb.iss, SEQ_NO_SPACE) || greaterThan(seg.ack(), tcb.sndNxt, SEQ_NO_SPACE))) {
            // segment ACKed something we never sent
            LOG.trace("{}[{}] Get got an ACKnowledgement `{}` for an Segment we never sent. Seems like remote peer is synchronized to another connection.", ctx.channel(), state, seg);
            if (seg.isRst()) {
                LOG.trace("{}[{}] As the RST bit is set. It doesn't matter as we will reset or connection now.", ctx.channel(), state);
            }
            else {
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.rst(seg.ack());
                LOG.trace("{}[{}] Inform remote peer about the desynchronization state by sending an `{}` and dropping the inbound Segment.", ctx.channel(), state, response);
                tcb.outgoingSegmentQueue().add(ctx, response);
            }
            return;
        }
        final boolean acceptableAck = tcb.isAcceptableAck(seg);

        // check RST
        if (seg.isRst()) {
            if (acceptableAck) {
                LOG.trace("{}[{}] Segment `{}` is an acceptable ACKnowledgement. Inform user, drop segment, enter CLOSED state.", ctx.channel(), state, seg);
                switchToNewState(ctx, CLOSED);
                tcb.delete(CONNECTION_RESET_EXCEPTION);
                ctx.fireExceptionCaught(CONNECTION_RESET_EXCEPTION);
                ctx.channel().close();
            }
            else {
                LOG.trace("{}[{}] Segment `{}` is not an acceptable ACKnowledgement. Drop it.", ctx.channel(), state, seg);
            }
            return;
        }

        // check SYN
        if (seg.isSyn()) {
            // synchronize receive state
            tcb.rcvNxt = add(seg.seq(), 1, SEQ_NO_SPACE);
            tcb.irs = seg.seq();
            if (seg.isAck()) {
                // advance send state
                tcb.sndUna = seg.ack();
                checkForAckedSegmentsInRetransmissionQueue(ctx);
            }

            final boolean ourSynHasBeenAcked = greaterThan(tcb.sndUna, tcb.iss, SEQ_NO_SPACE);
            if (ourSynHasBeenAcked) {
                LOG.trace("{}[{}] Remote peer has ACKed our SYN package and sent us his SYN `{}`. Handshake on our side is completed.", ctx.channel(), state, seg);

                cancelTimeoutGuards();
                switchToNewState(ctx, ESTABLISHED);
                userCallFuture.setSuccess();
                userCallFuture = null;

                // ACK
                if (flushUntil == -1 || tcb.sendBuffer().isEmpty()) {
                    final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(tcb.sndNxt, tcb.rcvNxt);
                    LOG.trace("{}[{}] ACKnowlede the received segment with a `{}` so the remote peer can complete the handshake as well.", ctx.channel(), state, response);
                    tcb.outgoingSegmentQueue().add(ctx, response);
                }
                else {
                    LOG.trace("{}[{}] We've pending data in our write queue. Flush this queue, it will piggyback an ACK.", ctx.channel(), state);
                    tryFlushingSendBuffer(ctx, false);
                }

                ctx.fireUserEventTriggered(new ConnectionHandshakeCompleted(tcb.sndNxt, tcb.rcvNxt));
            }
            else {
                switchToNewState(ctx, SYN_RECEIVED);
                // SYN/ACK
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.synAck(tcb.iss, tcb.rcvNxt);
                LOG.trace("{}[{}] Write `{}`.", ctx.channel(), state, response);
                tcb.outgoingSegmentQueue().add(ctx, response);
            }
        }
    }

    @SuppressWarnings("java:S3776")
    private void segmentArrivesOnOtherStates(final ChannelHandlerContext ctx,
                                             final ConnectionHandshakeSegment seg) {
        ReferenceCountUtil.touch(seg, "segmentArrivesOnOtherStates " + seg.toString());
        // check SEQ
        final boolean validSeg = seg.seq() == tcb.rcvNxt;
        final boolean acceptableAck = tcb.isAcceptableAck(seg);

        if (!validSeg && !acceptableAck) {
            // not expected seq
            if (!seg.isRst()) {
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(tcb.sndNxt, tcb.rcvNxt);
                LOG.trace("{}[{}] We got an unexpected Segment `{}`. Send an ACKnowledgement `{}` for the Segment we actually expect.", ctx.channel(), state, seg, response);
                tcb.outgoingSegmentQueue().add(ctx, response);
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
                        tcb.delete(CONNECTION_REFUSED_EXCEPTION);
                        ctx.fireExceptionCaught(CONNECTION_REFUSED_EXCEPTION);
                        ctx.channel().close();
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
                    tcb.delete(CONNECTION_RESET_EXCEPTION);
                    ctx.fireExceptionCaught(CONNECTION_RESET_EXCEPTION);
                    ctx.channel().close();
                    return;

                default:
                    // CLOSING
                    // LAST-ACK
                    LOG.trace("{}[{}] We got `{}`. Close channel.", ctx.channel(), state, seg);
                    switchToNewState(ctx, CLOSED);
                    tcb.delete(CONNECTION_CLOSED_ERROR);
                    ctx.channel().close();
                    return;
            }
        }

        // check ACK
        if (seg.isAck()) {
            switch (state) {
                case SYN_RECEIVED:
                    if (lessThanOrEqualTo(tcb.sndUna, seg.ack(), SEQ_NO_SPACE) && lessThanOrEqualTo(seg.ack(), tcb.sndNxt, SEQ_NO_SPACE)) {
                        LOG.trace("{}[{}] Remote peer ACKnowledge `{}` receivable of our SYN. As we've already received his SYN the handshake is now completed on both sides.", ctx.channel(), state, seg);

                        cancelTimeoutGuards();
                        switchToNewState(ctx, ESTABLISHED);
                        ctx.fireUserEventTriggered(new ConnectionHandshakeCompleted(tcb.sndNxt, tcb.rcvNxt));

                        if (!acceptableAck) {
                            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.rst(seg.ack());
                            LOG.trace("{}[{}] Segment `{}` is not an acceptable ACKnowledgement. Send RST `{}` and drop received Segment.", ctx.channel(), state, seg, response);
                            tcb.outgoingSegmentQueue().add(ctx, response);
                            return;
                        }

                        // advance send state
                        tcb.sndUna = seg.ack();
                        checkForAckedSegmentsInRetransmissionQueue(ctx);
                    }
                    break;

                case ESTABLISHED:
                    establishedProcessing(ctx, seg, acceptableAck);
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
                        return;
                    }
                    break;

                case LAST_ACK:
                    // our FIN has been ACKed
                    LOG.trace("{}[{}] Our sent FIN has been ACKnowledged by `{}`. Close sequence done.", ctx.channel(), state, seg);
                    switchToNewState(ctx, CLOSED);
                    tcb.delete(CONNECTION_CLOSED_ERROR);
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
                    final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(tcb.sndNxt, tcb.rcvNxt);
                    LOG.trace("{}[{}] Write `{}`.", ctx.channel(), state, response);
                    tcb.outgoingSegmentQueue().add(ctx, response);
            }
        }
        else if (seg.content().isReadable()) {
            LOG.trace("{}[{}] Got `{}` with off ACK bit. Drop segment and return.", ctx.channel(), state, seg);
            return;
        }

        // check URG here...

        // process the segment text
        final int readableBytes = seg.len();
        if (readableBytes > 0) {
            switch (state) {
                case ESTABLISHED:
                case FIN_WAIT_1:
                case FIN_WAIT_2:
                    tcb.receiveBuffer().add(seg.content().retain()); // wir rufen am ende IMMER release auf. hier müssen wir daher mal retainen
                    tcb.rcvNxt = add(tcb.rcvNxt, readableBytes, SEQ_NO_SPACE);

                    if (seg.isPsh()) {
                        final ByteBuf byteBuf = tcb.receiveBuffer().remove(tcb.receiveBuffer().readableBytes());
                        LOG.trace("{}[{}] Got `{}`. Add to receive buffer and pass `{}` inbound to channel.", ctx.channel(), state, seg, byteBuf);
                        ctx.fireChannelRead(byteBuf);
                    }
                    else {
                        LOG.trace("{}[{}] Got `{}`. Add to receive buffer and wait for next segment.", ctx.channel(), state, seg);
                    }

                    // Ack receival of segment text
                    final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(tcb.sndNxt, tcb.rcvNxt);
                    LOG.trace("{}[{}] ACKnowledge receival of `{}` by sending `{}`.", ctx.channel(), state, seg, response);
                    tcb.outgoingSegmentQueue().add(ctx, response);

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
            tcb.rcvNxt = add(seg.seq(), 1, SEQ_NO_SPACE);

            // send ACK for the FIN
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(tcb.sndNxt, tcb.rcvNxt);
            LOG.trace("{}[{}] Got CLOSE request `{}` from remote peer. ACKnowledge receival with `{}`.", ctx.channel(), state, seg, response);
            tcb.outgoingSegmentQueue().add(ctx, response);

            switch (state) {
                case SYN_RECEIVED:
                case ESTABLISHED:
                    // signal user connection closing
                    ctx.fireUserEventTriggered(HANDSHAKE_CLOSING_EVENT);

                    // wir haben keinen CLOSE_WAIT state, wir gehen also direkt zu LAST_ACK
                    // daher verschicken wir schon hier das FIN, was es sonst zwischen CLOSE_WAIT und LAST_ACK geben würde
                    LOG.trace("{}[{}] This channel is going to close now. Trigger channel close.", ctx.channel(), state);
                    final ConnectionHandshakeSegment response2 = ConnectionHandshakeSegment.fin(tcb.sndNxt);
                    tcb.sndNxt++;
                    LOG.trace("{}[{}] As we're already waiting for this. We're sending our last Segment `{}` and start waiting for the final ACKnowledgment.", ctx.channel(), state, response2);
                    tcb.outgoingSegmentQueue().add(ctx, response2);
                    switchToNewState(ctx, LAST_ACK);
                    break;

                case FIN_WAIT_1:
                    if (acceptableAck) {
                        // our FIN has been acknowledged
                        LOG.trace("{}[{}] Our FIN has been ACKnowledged. Close channel.", ctx.channel(), state, seg);
                        switchToNewState(ctx, CLOSED);
                        tcb.delete(CONNECTION_CLOSED_ERROR);
                    }
                    else {
                        switchToNewState(ctx, CLOSING);
                        tcb.delete(CONNECTION_CLOSING_ERROR);
                    }
                    break;

                case FIN_WAIT_2:
                    LOG.trace("{}[{}] Wait for our ACKnowledgment `{}` to be written to the network. Then close the channel.", ctx.channel(), state, response);
                    switchToNewState(ctx, CLOSED);
                    tcb.outgoingSegmentQueue().add(ctx, response, userCallFuture).addListener(CLOSE);
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
            LOG.trace("{}[{}] Got `{}`. Advance SND.UNA from {} to {} (+{}).", ctx.channel(), state, seg, tcb.sndUna, seg.ack(), (int) (seg.ack() - tcb.sndUna));
            // advance send state
            tcb.sndUna = seg.ack();
            checkForAckedSegmentsInRetransmissionQueue(ctx);
        }
        if (lessThan(seg.ack(), tcb.sndUna, SEQ_NO_SPACE)) {
            // ACK is duplicate. ignore
            LOG.error("{}[{}] Got old ACK. Ignore.", ctx.channel(), state);
            return true;
        }
        if (greaterThan(seg.ack(), tcb.sndUna, SEQ_NO_SPACE)) {
            // FIXME: add support for window!
            // something not yet sent has been ACKed
            LOG.error("{}[{}] something not yet sent has been ACKed.", ctx.channel(), state);
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(tcb.sndNxt, tcb.rcvNxt);
            LOG.trace("{}[{}] Write `{}`.", ctx.channel(), state, response);
            tcb.outgoingSegmentQueue().add(ctx, response);
            return true;
        }
        return false;
    }

    private void checkForAckedSegmentsInRetransmissionQueue(ChannelHandlerContext ctx) {
        ConnectionHandshakeSegment current = tcb.retransmissionQueue().current();
        if (current != null) {
            long lastSegmentToBeAcked = add(current.seq(), current.len(), SEQ_NO_SPACE);
            while (lessThanOrEqualTo(lastSegmentToBeAcked, tcb.sndUna, SEQ_NO_SPACE)) {
                LOG.trace("{}[{}] Segment `{}` has been fully ACKnowledged. Remove from retransmission queue. {} writes remain in retransmission queue.", ctx.channel(), state, current, tcb.retransmissionQueue().size() - 1);
                tcb.retransmissionQueue().removeAndSucceedCurrent();

                current = tcb.retransmissionQueue().current();
                if (current == null) {
                    break;
                }
                lastSegmentToBeAcked = add(current.seq(), current.len(), SEQ_NO_SPACE);
            }
        }
    }

    private void cancelTimeoutGuards() {
        if (userTimeoutFuture != null) {
            userTimeoutFuture.cancel(false);
        }
    }

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
}
