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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CoalescingBufferQueue;
import io.netty.channel.PendingWriteQueue;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.LongSupplier;

import static io.netty.channel.ChannelFutureListener.CLOSE;
import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
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
 * once the handshake has been issued. The handshake process will result either in a {@link
 * ConnectionHandshakeCompleted} event or {@link ConnectionHandshakeException} exception.
 */
@SuppressWarnings({ "java:S138", "java:S1142", "java:S1151", "java:S1192", "java:S1541" })
public class ConnectionHandshakeHandler extends ChannelDuplexHandler {
    public static final ConnectionHandshakeException CONNECTION_REFUSED_EXCEPTION = new ConnectionHandshakeException("Connection refused");
    public static final ClosedChannelException CONNECTION_CLOSED_ERROR = new ClosedChannelException();
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionHandshakeHandler.class);
    private static final ConnectionHandshakeIssued HANDSHAKE_ISSUED_EVENT = new ConnectionHandshakeIssued();
    private static final ConnectionHandshakeClosing HANDSHAKE_CLOSING_EVENT = new ConnectionHandshakeClosing();
    private static final ConnectionHandshakeException CONNECTION_CLOSING_ERROR = new ConnectionHandshakeException("Connection closing");
    private static final ConnectionHandshakeException CONNECTION_RESET_EXCEPTION = new ConnectionHandshakeException("Connection reset");
    private static final int SEQ_NO_SPACE = 32;
    final TransmissionControlBlock tcb;
    private final Duration userTimeout;
    private final LongSupplier issProvider;
    private final boolean activeOpen;
    private final int mss; // maximum segment size
    protected ScheduledFuture<?> userTimeoutFuture;
    State state;
    SendBuffer sendBuffer;
    RetransmissionQueue retransmissionQueue;
    ReceiveBuffer receiveBuffer;
    OutgoingSegmentQueue outgoingSegmentQueue;
    private ChannelPromise userCallFuture;
    private long flushUntil = -1;

    /**
     * @param userTimeout time in ms in which a handshake must taken place after issued
     * @param activeOpen  if {@code true} a handshake will be issued on {@link
     *                    #channelActive(ChannelHandlerContext)}. Otherwise the remote peer must
     *                    initiate the handshake
     */
    public ConnectionHandshakeHandler(final Duration userTimeout,
                                      final boolean activeOpen) {
        this(userTimeout, () -> randomInt(Integer.MAX_VALUE - 1), activeOpen, CLOSED, 0, 0, 0, 1254, 65_536); // FIXME: good default values?
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
     * @param mss         Maximum segment size
     * @param windowSize
     */
    @SuppressWarnings("java:S107")
    ConnectionHandshakeHandler(final Duration userTimeout,
                               final LongSupplier issProvider,
                               final boolean activeOpen,
                               final State state,
                               final int sndUna,
                               final int sndNxt,
                               final int rcvNxt,
                               final int mss,
                               final int windowSize) {
        this.userTimeout = requireNonNegative(userTimeout);
        this.issProvider = requireNonNull(issProvider);
        this.activeOpen = activeOpen;
        this.state = requireNonNull(state);
        this.tcb = new TransmissionControlBlock(sndUna, sndNxt, rcvNxt, windowSize);
        this.mss = mss;
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
        outgoingSegmentQueue.writeAndFlushAny();
    }

    private void tryFlushingSendBuffer(final ChannelHandlerContext ctx, boolean newFlush) {
        if (newFlush) {
            // merke dir wie viel byes wir jetzt im buffer haben und verwende auch nur bis dahin
            flushUntil = add(tcb.sndNxt, sendBuffer.readableBytes(), SEQ_NO_SPACE);
        }

        if (state != ESTABLISHED || flushUntil == -1 || sendBuffer.isEmpty()) {
            return;
        }

        int allowedBytesForNewTransmission = tcb.sequenceNumbersAllowedForNewDataTransmission();
        final int allowedBytesToFlush = (int) (this.flushUntil - tcb.sndNxt);
        LOG.trace("{}[{}] Flush of write buffer was triggered. {} sequence numbers are allowed to write to the network. {} bytes in send buffer. {} bytes allowed to flush. MSS={}", ctx.channel(), state, allowedBytesForNewTransmission, sendBuffer.readableBytes(), allowedBytesToFlush, mss);

        final int readableBytesInBuffer = sendBuffer.readableBytes();
        int remainingBytes = Math.min(Math.min(allowedBytesForNewTransmission, readableBytesInBuffer), allowedBytesToFlush);

        LOG.trace("{}[{}] Write {} bytes to network", ctx.channel(), state, remainingBytes);
        final boolean somethingWritten = remainingBytes > 0;
        while (remainingBytes > 0) {
            final ChannelPromise ackPromise = ctx.newPromise();
            final ByteBuf data = sendBuffer.remove(Math.min(mss, remainingBytes), ackPromise);
            remainingBytes -= data.readableBytes();
            final boolean isLast = remainingBytes == 0 || sendBuffer.isEmpty();
            final ConnectionHandshakeSegment seg;
            if (isLast) {
                seg = ConnectionHandshakeSegment.pshAck(tcb.sndNxt, tcb.rcvNxt, data);
            }
            else {
                seg = ConnectionHandshakeSegment.ack(tcb.sndNxt, tcb.rcvNxt, data);
            }
            tcb.sndNxt = add(tcb.sndNxt, data.readableBytes(), SEQ_NO_SPACE);
            LOG.trace("{}[{}] Write `{}` to network ({} bytes allowed to write to network left. {} writes will be contained in retransmission queue).", ctx.channel(), state, seg, tcb.sequenceNumbersAllowedForNewDataTransmission(), retransmissionQueue.size() + 1);
            retransmissionQueue.add(seg, ackPromise);
            outgoingSegmentQueue.add(seg.copy());
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
                sendBuffer.add(data, promise);
                performActiveOpen(ctx);
                break;

            case SYN_SENT:
            case SYN_RECEIVED:
                // Queue the data for transmission after entering ESTABLISHED state.
                sendBuffer.add(data, promise);
                break;

            case ESTABLISHED:
                // add this message to or send buffer, to allow it to be sent together with other
                // data for transmission efficiency.
                LOG.trace("{}[{}] As connection is established, we can add the message `{}` to the write queue and trigger a queue flush.", ctx.channel(), state, data);
                sendBuffer.add(data, promise);
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
        // initiate send state
        tcb.iss = issProvider.getAsLong();
        tcb.sndUna = tcb.iss;
        tcb.sndNxt = add(tcb.iss, 1, SEQ_NO_SPACE);

        // send SYN
        final ConnectionHandshakeSegment seg = ConnectionHandshakeSegment.syn(tcb.iss);
        LOG.trace("{}[{}] Initiate OPEN process by sending `{}`.", ctx.channel(), state, seg);
        outgoingSegmentQueue.addAndFlush(seg);

        switchToNewState(ctx, SYN_SENT);

        // start user timeout guard
        applyUserTimeout(ctx, "OPEN", userCallFuture);

        ctx.fireUserEventTriggered(HANDSHAKE_ISSUED_EVENT);
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
                outgoingSegmentQueue.addAndFlush(seg);

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
                LOG.trace("{}[{}] User timeout for {} user call expired after {}ms. Close channel.", ctx.channel(), state, userCall, userTimeout);
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
        this.sendBuffer = new SendBuffer(ctx);
        this.retransmissionQueue = new RetransmissionQueue(ctx);
        this.receiveBuffer = new ReceiveBuffer(ctx);
        this.outgoingSegmentQueue = new OutgoingSegmentQueue(ctx);

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

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        // cancel all timeout guards
        cancelTimeoutGuards();

        outgoingSegmentQueue.releaseAndFailAll(CONNECTION_CLOSED_ERROR);

        ctx.fireChannelInactive();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // cancel all timeout guards
        cancelTimeoutGuards();

        outgoingSegmentQueue.releaseAndFailAll(CONNECTION_CLOSED_ERROR);
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
        outgoingSegmentQueue.writeAndFlushAny();

        ctx.fireChannelReadComplete();
    }

    private void segmentArrives(final ChannelHandlerContext ctx,
                                final ConnectionHandshakeSegment seg) {
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
        outgoingSegmentQueue.add(response);
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
            outgoingSegmentQueue.add(response);
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

            // synchronize receive state
            tcb.rcvNxt = add(seg.seq(), 1, SEQ_NO_SPACE);
            tcb.irs = seg.seq();

            // initiate send state
            tcb.iss = issProvider.getAsLong();
            tcb.sndUna = tcb.iss;
            tcb.sndNxt = add(tcb.iss, 1, SEQ_NO_SPACE);

            // send SYN/ACK
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.synAck(tcb.iss, tcb.rcvNxt);
            LOG.trace("{}[{}] ACKnowlede the received segment and send our SYN `{}`.", ctx.channel(), state, response);
            outgoingSegmentQueue.add(response);
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
                outgoingSegmentQueue.add(response);
            }
            return;
        }
        final boolean acceptableAck = tcb.isAcceptableAck(seg);

        // check RST
        if (seg.isRst()) {
            if (acceptableAck) {
                LOG.trace("{}[{}] Segment `{}` is an acceptable ACKnowledgement. Inform user, drop segment, enter CLOSED state.", ctx.channel(), state, seg);
                switchToNewState(ctx, CLOSED);
                sendBuffer.releaseAndFailAll(CONNECTION_RESET_EXCEPTION);
                retransmissionQueue.releaseAndFailAll(CONNECTION_RESET_EXCEPTION);
                receiveBuffer.releaseAndFailAll(CONNECTION_RESET_EXCEPTION);
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
            }

            final boolean ourSynHasBeenAcked = greaterThan(tcb.sndUna, tcb.iss, SEQ_NO_SPACE);
            if (ourSynHasBeenAcked) {
                LOG.trace("{}[{}] Remote peer has ACKed our SYN package and sent us his SYN `{}`. Handshake on our side is completed.", ctx.channel(), state, seg);

                cancelTimeoutGuards();
                switchToNewState(ctx, ESTABLISHED);
                userCallFuture.setSuccess();
                userCallFuture = null;

                // ACK
                if (flushUntil == -1 || sendBuffer.isEmpty()) {
                    final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(tcb.sndNxt, tcb.rcvNxt);
                    LOG.trace("{}[{}] ACKnowlede the received segment with a `{}` so the remote peer can complete the handshake as well.", ctx.channel(), state, response);
                    outgoingSegmentQueue.add(response);
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
                outgoingSegmentQueue.add(response);
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
                outgoingSegmentQueue.add(response);
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
                        sendBuffer.releaseAndFailAll(CONNECTION_REFUSED_EXCEPTION);
                        retransmissionQueue.releaseAndFailAll(CONNECTION_REFUSED_EXCEPTION);
                        receiveBuffer.releaseAndFailAll(CONNECTION_REFUSED_EXCEPTION);
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
                    sendBuffer.releaseAndFailAll(CONNECTION_RESET_EXCEPTION);
                    retransmissionQueue.releaseAndFailAll(CONNECTION_RESET_EXCEPTION);
                    receiveBuffer.releaseAndFailAll(CONNECTION_RESET_EXCEPTION);
                    ctx.fireExceptionCaught(CONNECTION_RESET_EXCEPTION);
                    ctx.channel().close();
                    return;

                default:
                    // CLOSING
                    // LAST-ACK
                    LOG.trace("{}[{}] We got `{}`. Close channel.", ctx.channel(), state, seg);
                    switchToNewState(ctx, CLOSED);
                    sendBuffer.releaseAndFailAll(CONNECTION_CLOSED_ERROR);
                    retransmissionQueue.releaseAndFailAll(CONNECTION_CLOSED_ERROR);
                    receiveBuffer.releaseAndFailAll(CONNECTION_CLOSED_ERROR);
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
                            outgoingSegmentQueue.add(response);
                            return;
                        }

                        // advance send state
                        tcb.sndUna = seg.ack();
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
                    sendBuffer.releaseAndFailAll(CONNECTION_CLOSED_ERROR);
                    retransmissionQueue.releaseAndFailAll(CONNECTION_CLOSED_ERROR);
                    receiveBuffer.releaseAndFailAll(CONNECTION_CLOSED_ERROR);
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
                    outgoingSegmentQueue.add(response);
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
                    receiveBuffer.add(seg.content().retain()); // wir rufen am ende IMMER release auf. hier müssen wir daher mal retainen
                    tcb.rcvNxt = add(tcb.rcvNxt, readableBytes, SEQ_NO_SPACE);

                    if (seg.isPsh()) {
                        final ByteBuf byteBuf = receiveBuffer.remove(receiveBuffer.readableBytes());
                        LOG.trace("{}[{}] Got `{}`. Add to receive buffer and pass `{}` inbound to channel.", ctx.channel(), state, seg, byteBuf);
                        ctx.fireChannelRead(byteBuf);
                    }
                    else {
                        LOG.trace("{}[{}] Got `{}`. Add to receive buffer and wait for next segment.", ctx.channel(), state, seg);
                    }

                    // Ack receival of segment text
                    final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(tcb.sndNxt, tcb.rcvNxt);
                    LOG.trace("{}[{}] ACKnowledge receival by sending `{}`.", ctx.channel(), state, response);
                    outgoingSegmentQueue.add(response);

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
            outgoingSegmentQueue.add(response);

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
                    outgoingSegmentQueue.add(response2);
                    switchToNewState(ctx, LAST_ACK);
                    break;

                case FIN_WAIT_1:
                    if (acceptableAck) {
                        // our FIN has been acknowledged
                        LOG.trace("{}[{}] Our FIN has been ACKnowledged. Close channel.", ctx.channel(), state, seg);
                        switchToNewState(ctx, CLOSED);
                        sendBuffer.releaseAndFailAll(CONNECTION_CLOSED_ERROR);
                        retransmissionQueue.releaseAndFailAll(CONNECTION_CLOSED_ERROR);
                        receiveBuffer.releaseAndFailAll(CONNECTION_CLOSED_ERROR);
                    }
                    else {
                        switchToNewState(ctx, CLOSING);
                        sendBuffer.releaseAndFailAll(CONNECTION_CLOSING_ERROR);
                        retransmissionQueue.releaseAndFailAll(CONNECTION_CLOSING_ERROR);
                        receiveBuffer.releaseAndFailAll(CONNECTION_CLOSING_ERROR);
                    }
                    break;

                case FIN_WAIT_2:
                    LOG.trace("{}[{}] Wait for our ACKnowledgment `{}` to be written to the network. Then close the channel.", ctx.channel(), state, response);
                    switchToNewState(ctx, CLOSED);
                    outgoingSegmentQueue.add(response, userCallFuture).addListener(CLOSE);
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

            // FIXME: sollten wir das nicht immer machen, wenn tcb.sndUna erhöht wird?
            // FIXME: wie vereinen wir retransmissionQueue mit RetransmissionQueue?
            // FIXME: remove ACKnowledged segment from retransission queue. complete queue
            ConnectionHandshakeSegment current = retransmissionQueue.current();
            if (current != null) {
                long lastAckedSegment = add(current.seq(), current.len(), SEQ_NO_SPACE);
                while (lessThanOrEqualTo(lastAckedSegment, tcb.sndUna, SEQ_NO_SPACE)) {
                    LOG.trace("{}[{}] Segment `{}` has been fully ACKnowledged. Remove from retransmission queue. {} writes remain in retransmission queue.", ctx.channel(), state, current, retransmissionQueue.size() - 1);
                    retransmissionQueue.removeAndSucceedCurrent();

                    current = retransmissionQueue.current();
                    if (current == null) {
                        break;
                    }
                    lastAckedSegment = add(current.seq(), current.len(), SEQ_NO_SPACE);
                }
            }
        }
        if (lessThan(seg.ack(), tcb.sndUna, SEQ_NO_SPACE)) {
            // ACK is duplicate. ignore
            return true;
        }
        if (greaterThan(seg.ack(), tcb.sndUna, SEQ_NO_SPACE)) {
            // something not yet sent has been ACKed
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(tcb.sndNxt, tcb.rcvNxt);
            LOG.trace("{}[{}] Write `{}`.", ctx.channel(), state, response);
            outgoingSegmentQueue.add(response);
            return true;
        }
        return false;
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

    /**
     * A {@link ChannelFutureListener} that retransmit not acknowledged segments.
     */
    private class RetransmissionTimeoutApplier implements ChannelFutureListener {
        private static final long LOWER_BOUND = 100; // lower bound for retransmission (e.g., 1 second)
        private static final long UPPER_BOUND = 60_000; // upper bound for retransmission (e.g., 1 minute)
        private static final int RTT = 20; // as we're currently not aware of the actual RTT, we use this fixed value
        private static final float ALPHA = .9F; // smoothing factor (e.g., .8 to .9)
        private static final float BETA = 1.7F; // delay variance factor (e.g., 1.3 to 2.0)
        private final ChannelHandlerContext ctx;
        private final ConnectionHandshakeSegment seg;
        private final long srtt;

        RetransmissionTimeoutApplier(final ChannelHandlerContext ctx,
                                     final ConnectionHandshakeSegment seg) {
            this(ctx, seg, RTT);
        }

        RetransmissionTimeoutApplier(final ChannelHandlerContext ctx,
                                     final ConnectionHandshakeSegment seg,
                                     final long srtt) {
            this.ctx = requireNonNull(ctx);
            this.seg = requireNonNull(seg);
            this.srtt = requirePositive(srtt);
        }

        @SuppressWarnings({ "unchecked", "java:S2164" })
        @Override
        public void operationComplete(final ChannelFuture future) {
            if (future.isSuccess()) {
                if (!seg.isOnlyAck() && !seg.isRst()) {
                    // schedule retransmission
                    final long newSrtt = (long) (ALPHA * srtt + (1 - ALPHA) * RTT);
                    final long rto = Math.min(UPPER_BOUND, Math.max(LOWER_BOUND, (long) (BETA * newSrtt)));
                    ctx.executor().schedule(() -> {
                        if (future.channel().isOpen() && state != CLOSED && lessThanOrEqualTo(tcb.sndUna, seg.seq(), SEQ_NO_SPACE)) {
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

    // es kann sein, dass wir in einem Rutsch (durch mehrere channelReads) Segmente empfangen und die dann z.B. alle jeweils ACKen
    // zum Zeitpunkt des channelReads wissen wir noch nicht, ob noch mehr kommt
    // daher speichern wir die nachrichten und warten auf ein channelReadComplete. Dort gucken wir dann, ob wir z.B. ACKs zusammenfassen können/etc.
    class OutgoingSegmentQueue {
        private final ChannelHandlerContext ctx;
        // FIXME: update channel writability?
        private final ArrayDeque<OutgoingSegment> queue = new ArrayDeque<>();

        OutgoingSegmentQueue(final ChannelHandlerContext ctx) {
            this.ctx = requireNonNull(ctx);
        }

        public ChannelPromise add(final ConnectionHandshakeSegment seg,
                                  final ChannelPromise promise) {
            queue.add(new OutgoingSegment(seg, promise));
            return promise;
        }

        public ChannelPromise add(final ConnectionHandshakeSegment seg) {
            return add(seg, ctx.newPromise());
        }

        public ChannelPromise addAndFlush(ConnectionHandshakeSegment seg) {
            ChannelPromise promise = add(seg);
            writeAndFlushAny();
            return promise;
        }

        public void writeAndFlushAny() {
            if (queue.isEmpty()) {
                return;
            }

            final int size = queue.size();
            LOG.trace("{}[{}] Channel read complete. Now check if we can repackage/cumulate {} outgoing segments.", ctx.channel(), state, size);
            if (size == 1) {
                final OutgoingSegment entry = queue.remove();
                final ConnectionHandshakeSegment seg = entry.seg();
                final ChannelPromise promise = entry.writePromise();
                write(seg, promise);
                ctx.flush();
                return;
            }

            // multiple segments in queue. Check if we can cumulate them
            OutgoingSegment current = queue.poll();
            while (!queue.isEmpty()) {
                OutgoingSegment next = queue.remove();

                if (current.seg().isOnlyAck() && next.seg().isOnlyAck() && current.seg().seq() == next.seg().seq() && lessThanOrEqualTo(current.seg().seq(), next.seg().seq(), SEQ_NO_SPACE)) {
                    // cumulate ACKs
                    LOG.trace("{}[{}] Outgoing queue: Current segment `{}` is followed by segment `{}` with same flags set, same SEQ, and >= ACK. We can purge current segment.", ctx.channel(), state, current.seg(), next.seg());
                    current.seg().release();
                    next.writePromise().addListener(new PromiseNotifier<>(current.writePromise()));
                }
                else if (current.seg().isOnlyAck() && current.seg().seq() == next.seg().seq() && current.seg().len() == 0) {
                    // piggyback ACK
                    LOG.trace("{}[{}] Outgoing queue: Piggyback current ACKnowledgement `{}` to next segment `{}`.", ctx.channel(), state, current.seg(), next.seg());
                    next = new OutgoingSegment(ConnectionHandshakeSegment.piggybackAck(next.seg(), current.seg()), current.writePromise(), current.ackPromise());
                    next.writePromise().addListener(new PromiseNotifier<>(current.writePromise()));
                }
                else {
                    write(current.seg(), current.writePromise());
                }

                current = next;
            }

            write(current.seg(), current.writePromise());
            ctx.flush();
        }

        private void write(final ConnectionHandshakeSegment seg,
                           final ChannelPromise promise) {
            final boolean mustBeAcked = mustBeAcked(seg);
            ctx.write(seg, promise).addListener(CLOSE_ON_FAILURE);
            if (mustBeAcked) {
                promise.addListener(new RetransmissionTimeoutApplier(ctx, seg));
            }
        }

        private boolean mustBeAcked(ConnectionHandshakeSegment seg) {
            return (!seg.isOnlyAck() && !seg.isRst()) || seg.len() != 0;
        }

        public void releaseAndFailAll(final Throwable cause) {
            OutgoingSegment entry;
            while ((entry = queue.poll()) != null) {
                ConnectionHandshakeSegment seg = entry.seg();
                ChannelPromise promise = entry.writePromise();

                seg.release();
                promise.tryFailure(cause);
            }
        }

        class OutgoingSegment {
            private final ConnectionHandshakeSegment seg;
            private final ChannelPromise writePromise;
            private final ChannelPromise ackPromise;

            OutgoingSegment(final ConnectionHandshakeSegment seg,
                            final ChannelPromise writePromise,
                            final ChannelPromise ackPromise) {
                this.seg = seg;
                this.writePromise = writePromise;
                this.ackPromise = ackPromise;
            }

            OutgoingSegment(final ConnectionHandshakeSegment seg,
                            final ChannelPromise writePromise) {
                this(seg, writePromise, ctx.newPromise());
            }

            public ConnectionHandshakeSegment seg() {
                return seg;
            }

            public ChannelPromise writePromise() {
                return writePromise;
            }

            public ChannelPromise ackPromise() {
                return ackPromise;
            }
        }
    }

    /**
     * <pre>
     *       Send Sequence Space
     *
     *                    1         2          3          4
     *               ----------|----------|----------|----------
     *                      SND.UNA    SND.NXT    SND.UNA
     *                                           +SND.WND
     *
     *         1 - old sequence numbers which have been acknowledged
     *         2 - sequence numbers of unacknowledged data
     *         3 - sequence numbers allowed for new data transmission
     *         4 - future sequence numbers which are not yet allowed
     *  </pre>
     * <pre>
     *          Receive Sequence Space
     *
     *                        1          2          3
     *                    ----------|----------|----------
     *                           RCV.NXT    RCV.NXT
     *                                     +RCV.WND
     *
     *         1 - old sequence numbers which have been acknowledged
     *         2 - sequence numbers allowed for new reception
     *         3 - future sequence numbers which are not yet allowed
     * </pre>
     */
    static class TransmissionControlBlock {
        // Send Sequence Variables
        long sndUna; // oldest unacknowledged sequence number
        long sndNxt; // next sequence number to be sent
        int sndWnd; // send window
        long iss; // initial send sequence number
        // Receive Sequence Variables
        long rcvNxt; // next sequence number expected on an incoming segments, and is the left or lower edge of the receive window
        int rcvWnd; // receive window
        long irs; // initial receive sequence number

        public TransmissionControlBlock(final long sndUna,
                                        final long sndNxt,
                                        final long rcvNxt,
                                        final int windowSize) {
            this.sndUna = sndUna;
            this.sndNxt = sndNxt;
            this.sndWnd = windowSize;
            this.rcvNxt = rcvNxt;
            this.rcvWnd = windowSize;
        }

        private boolean isAcceptableAck(final ConnectionHandshakeSegment seg) {
            return seg.isAck() && lessThan(sndUna, seg.ack(), SEQ_NO_SPACE) && lessThanOrEqualTo(seg.ack(), sndNxt, SEQ_NO_SPACE);
        }

        private int sequenceNumbersAllowedForNewDataTransmission() {
            return (int) (sndUna + sndWnd - sndNxt);
        }
    }

    /**
     * Holds data enqueued by the application to be written to the network. This FIFO queue also
     * updates the {@link io.netty.channel.Channel} writability for the bytes it holds.
     */
    static class SendBuffer {
        private final CoalescingBufferQueue bufferQueue;

        SendBuffer(final ChannelHandlerContext ctx) {
            bufferQueue = new CoalescingBufferQueue(ctx.channel());
        }

        /**
         * Add a buffer to the end of the queue and associate a promise with it that should be
         * completed when all the buffer's bytes have been consumed from the queue and written.
         *
         * @param buf     to add to the tail of the queue
         * @param promise to complete when all the bytes have been consumed and written, can be
         *                void.
         */
        public void add(final ByteBuf buf, final ChannelPromise promise) {
            bufferQueue.add(buf, promise);
        }

        /**
         * Are there pending buffers in the queue.
         */
        public boolean isEmpty() {
            return bufferQueue.isEmpty();
        }

        /**
         * Remove a {@link ByteBuf} from the queue with the specified number of bytes. Any added
         * buffer who's bytes are fully consumed during removal will have it's promise completed
         * when the passed aggregate {@link ChannelPromise} completes.
         *
         * @param bytes            the maximum number of readable bytes in the returned {@link
         *                         ByteBuf}, if {@code bytes} is greater than {@link #readableBytes}
         *                         then a buffer of length {@link #readableBytes} is returned.
         * @param aggregatePromise used to aggregate the promises and listeners for the constituent
         *                         buffers.
         * @return a {@link ByteBuf} composed of the enqueued buffers.
         */
        public ByteBuf remove(final int bytes, final ChannelPromise aggregatePromise) {
            return bufferQueue.remove(bytes, aggregatePromise);
        }

        /**
         * Release all buffers in the queue and complete all listeners and promises.
         */
        public void releaseAndFailAll(final Throwable cause) {
            bufferQueue.releaseAndFailAll(cause);
        }

        /**
         * The number of readable bytes.
         */
        public int readableBytes() {
            return bufferQueue.readableBytes();
        }
    }

    /**
     * Holds all segments that has been written to the network (called in-flight) but have not been
     * acknowledged yet. This FIFO queue also updates the {@link io.netty.channel.Channel}
     * writability for the bytes it holds.
     */
    static class RetransmissionQueue {
        private final PendingWriteQueue pendingSegments;
        private final Channel channel;

        RetransmissionQueue(final ChannelHandlerContext ctx) {
            channel = ctx.channel();
            pendingSegments = new PendingWriteQueue(channel);
        }

        public void add(final ConnectionHandshakeSegment seg, final ChannelPromise promise) {
            pendingSegments.add(seg, promise);
        }

        /**
         * Release all buffers in the queue and complete all listeners and promises.
         */
        public void releaseAndFailAll(final Throwable cause) {
            pendingSegments.removeAndFailAll(cause);
        }

        /**
         * Return the current message or {@code null} if empty.
         */
        public ConnectionHandshakeSegment current() {
            return (ConnectionHandshakeSegment) pendingSegments.current();
        }

        public void removeAndSucceedCurrent() {
            pendingSegments.remove().setSuccess();
        }

        /**
         * Returns the number of pending write operations.
         */
        public int size() {
            if (channel.eventLoop().inEventLoop()) {
                return pendingSegments.size();
            }
            else {
                final CompletableFuture<Integer> future = new CompletableFuture<>();
                channel.eventLoop().execute(() -> {
                    future.complete(pendingSegments.size());
                });
                try {
                    return future.get();
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static class ReceiveBuffer {
        private final CoalescingBufferQueue bufferQueue;
        private final ChannelHandlerContext ctx;

        ReceiveBuffer(final ChannelHandlerContext ctx) {
            bufferQueue = new CoalescingBufferQueue(ctx.channel(), 4, false);
            this.ctx = requireNonNull(ctx);
        }

        /**
         * Add a buffer to the end of the queue and associate a promise with it that should be
         * completed when all the buffer's bytes have been consumed from the queue and written.
         *
         * @param buf to add to the tail of the queue
         */
        public void add(final ByteBuf buf) {
            bufferQueue.add(buf);
        }

        /**
         * Are there pending buffers in the queue.
         */
        public boolean isEmpty() {
            return bufferQueue.isEmpty();
        }

        /**
         * Remove a {@link ByteBuf} from the queue with the specified number of bytes. Any added
         * buffer who's bytes are fully consumed during removal will have it's promise completed
         * when the passed aggregate {@link ChannelPromise} completes.
         *
         * @param bytes the maximum number of readable bytes in the returned {@link ByteBuf}, if
         *              {@code bytes} is greater than {@link #readableBytes} then a buffer of length
         *              {@link #readableBytes} is returned.
         * @return a {@link ByteBuf} composed of the enqueued buffers.
         */
        public ByteBuf remove(final int bytes) {
            return bufferQueue.remove(bytes, ctx.newPromise().setSuccess());
        }

        /**
         * Release all buffers in the queue and complete all listeners and promises.
         */
        public void releaseAndFailAll(final Throwable cause) {
            bufferQueue.releaseAndFailAll(cause);
        }

        /**
         * The number of readable bytes.
         */
        public int readableBytes() {
            return bufferQueue.readableBytes();
        }
    }
}
