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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.function.Supplier;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
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
 * connection-oriented communication with remote peer.
 * <p>
 * Depending on configuration, the synchronization will be automaticall initiated on {@link
 * #channelActive(ChannelHandlerContext)} or manually after writing {@link UserCall#OPEN} to the
 * channel. Once the synchronization is done, a {@link ConnectionHandshakeCompleted} event will be
 * passed to channel.
 * <p>
 * {@link ConnectionHandshakeIssued} event is passed to channel once a handshake is started. A
 * handshake will result in a {@link ConnectionHandshakeCompleted} or {@link
 * ConnectionHandshakeTimeout} event, or {@link ConnectionHandshakeException} error.
 * <p>
 * The synchronization process has been heavily inspired by the three-way handshake of TCP (<a
 * href="https://datatracker.ietf.org/doc/html/rfc793#section-3.4">RFC 793</a>).
 */
public class ConnectionHandshakeHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionHandshakeHandler.class);
    private static final ConnectionHandshakeIssued HANDSHAKE_ISSUED_EVENT = new ConnectionHandshakeIssued();
    private static final ConnectionHandshakeReset HANDSHAKE_RESET_EVENT = new ConnectionHandshakeReset();
    private final long retransmissionTimeout;
    private final long handshakeTimeout;
    private final Supplier<Integer> issProvider;
    private final boolean activeOpen;
    State state;
    // Send Sequence Variables
    int snd_una; // oldest unacknowledged sequence number
    int snd_nxt; // next sequence number to be sent
    int iss; // initial send sequence number
    // Receive Sequence Variables
    int rcv_nxt; // next sequence number expected on an incoming segments, and is the left or lower edge of the receive window
    int irs; // initial receive sequence number
    private ChannelPromise openPromise;
    private ScheduledFuture<?> handshakeTimeoutFuture;
    private ScheduledFuture<?> retransmissionTimeoutFuture;

    public ConnectionHandshakeHandler(final long handshakeTimeout,
                                      final boolean activeOpen) {
        this(handshakeTimeout, 100L, () -> randomInt(Integer.MAX_VALUE - 1), activeOpen, CLOSED, 0, 0, 0);
    }

    /**
     * @param handshakeTimeout      Time in ms after issuing a handshake until it is considered as
     *                              timed out (on timeout a {@link ConnectionHandshakeTimeout} event
     *                              will passed to channel)
     * @param retransmissionTimeout
     * @param issProvider           Provider to generate the initial send sequence number
     * @param activeOpen            Initiate active OPEN handshake process automatically on {@link
     *                              #channelActive(ChannelHandlerContext)}
     * @param state                 Current synchronization state
     * @param snd_una               Oldest unacknowledged sequence number
     * @param snd_nxt               Next sequence number to be sent
     * @param rcv_nxt               Next expected sequence number
     */
    ConnectionHandshakeHandler(final long handshakeTimeout,
                               final long retransmissionTimeout,
                               final Supplier<Integer> issProvider,
                               final boolean activeOpen,
                               final State state,
                               final int snd_una,
                               final int snd_nxt,
                               final int rcv_nxt) {
        this.handshakeTimeout = requireNonNegative(handshakeTimeout);
        this.retransmissionTimeout = requireNonNegative(retransmissionTimeout);
        this.issProvider = requireNonNull(issProvider);
        this.activeOpen = activeOpen;
        this.state = requireNonNull(state);
        this.snd_una = snd_una;
        this.snd_nxt = snd_nxt;
        this.rcv_nxt = rcv_nxt;
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
            connectionOpen(ctx, ctx.newPromise());
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
            channelReadSegment(ctx, (ConnectionHandshakeSegment) msg);
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
            connectionOpen(ctx, promise);
        }
        else if (msg == ABORT) {
            connectionAbort(ctx, promise);
        }
        else {
            if (state != ESTABLISHED) {
                promise.setFailure(new IllegalStateException("Attempting to write to channel with handshake in progress"));
                return;
            }

            ctx.write(msg, promise);
        }
    }

    /*
     * Handshake
     */

    private void connectionOpen(final ChannelHandlerContext ctx,
                                final ChannelPromise promise) {
        switch (state) {
            case CLOSED:
            case LISTEN:
                // channel was closed. Perform active OPEN handshake
                openPromise = promise;
                ctx.fireUserEventTriggered(HANDSHAKE_ISSUED_EVENT);

                state = SYN_SENT;

                // update send state
                iss = issProvider.get();
                snd_una = iss;
                snd_nxt = iss + 1;

                // send SYN
                final int seq = iss;
                final ConnectionHandshakeSegment seg = ConnectionHandshakeSegment.syn(seq);
                LOG.trace("[{}] Initiate active OPEN handshake by sending `{}`.", state, seg);
                writeSegment(ctx, seg);

                // start handshake timeout guard
                if (handshakeTimeout > 0) {
                    handshakeTimeoutFuture = ctx.executor().schedule(() -> {
                        cancelTimeoutGuards();
                        LOG.trace("[{}] Handshake timed out after {}ms!", state, handshakeTimeout);
                        final ConnectionHandshakeException e = new ConnectionHandshakeException("Error: Handshake timeout");
                        promise.setFailure(e);
                        ctx.fireExceptionCaught(e);
                        state = CLOSED;
                    }, handshakeTimeout, MILLISECONDS);
                }

                // start retransmission timeout guard
                retransmissionTimeoutFuture = ctx.executor().scheduleWithFixedDelay(() -> {
                    LOG.trace("[{}] Retransmission timeout after {}ms for `{}`.", state, retransmissionTimeout, seg);
                    writeSegment(ctx, seg);
                }, retransmissionTimeout, retransmissionTimeout, MILLISECONDS);
                break;

            default:
                promise.setFailure(new Exception("Connection is already open."));
        }
    }

    private void connectionAbort(final ChannelHandlerContext ctx, final ChannelPromise promise) {
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
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.rst(snd_nxt);
                writeSegment(ctx, response);
                state = CLOSED;
                promise.setSuccess();
                break;
        }
    }

    private void cancelTimeoutGuards() {
        if (handshakeTimeoutFuture != null) {
            handshakeTimeoutFuture.cancel(false);
        }
        if (retransmissionTimeoutFuture != null) {
            retransmissionTimeoutFuture.cancel(false);
        }
    }

    private void writeSegment(final ChannelHandlerContext ctx,
                              final ConnectionHandshakeSegment seg) {
        LOG.trace("[{}] Write `{}`.", state, seg);
        ctx.writeAndFlush(seg).addListener(FIRE_EXCEPTION_ON_FAILURE);
    }

    private void channelReadSegment(final ChannelHandlerContext ctx,
                                    final ConnectionHandshakeSegment seg) {
        LOG.trace("[{}] Read `{}`.", state, seg);

        switch (state) {
            case CLOSED:
                channelReadSegmentClosedState(ctx, seg);
                break;

            case LISTEN:
                channelReadSegmentListenState(ctx, seg);
                break;

            case SYN_SENT:
                channelReadSegmentSynSentState(ctx, seg);
                break;

            case SYN_RECEIVED:
                channelReadSegmentSynReceivedState(ctx, seg);
                break;

            case ESTABLISHED:
                channelReadSegmentEstablishedState(ctx, seg);
                break;
        }
    }

    private void channelReadSegmentClosedState(final ChannelHandlerContext ctx,
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
        writeSegment(ctx, response);
        ReferenceCountUtil.release(seg);
    }

    private void channelReadSegmentListenState(final ChannelHandlerContext ctx,
                                               final ConnectionHandshakeSegment seg) {
        if (seg.isRst()) {
            // as we are already/still in CLOSED state, we can ignore the RST
            ReferenceCountUtil.release(seg);
            return;
        }

        if (seg.isAck()) {
            // we are on a state were we have never sent anything that must be ACKed
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.rst(seg.ack());
            writeSegment(ctx, response);
            ReferenceCountUtil.release(seg);
            return;
        }

        if (seg.isSyn()) {
            // yay, peer SYNced with us
            state = SYN_RECEIVED;

            ctx.fireUserEventTriggered(HANDSHAKE_ISSUED_EVENT);

            // synchronize receive state
            rcv_nxt = seg.seq() + 1;
            irs = seg.seq();

            // update send state
            iss = issProvider.get();
            snd_una = iss;
            snd_nxt = iss + 1;

            // send SYN/ACK
            final int seq = iss;
            final int ack = rcv_nxt;
            final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.synAck(seq, ack);
            writeSegment(ctx, response);
            ReferenceCountUtil.release(seg);
            return;
        }

        // we should not reach this point. However, if it does happen, just drop the segment
        unexpectedSegment(seg);
    }

    private void channelReadSegmentSynSentState(final ChannelHandlerContext ctx,
                                                final ConnectionHandshakeSegment seg) {
        if (seg.isAck()) {
            if (seg.ack() <= iss || seg.ack() > snd_nxt) {
                // segment ACKed something we never sent
                if (!seg.isRst()) {
                    final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.rst(seg.ack());
                    writeSegment(ctx, response);
                }
                ReferenceCountUtil.release(seg);
                return;
            }
        }

        if (seg.isRst()) {
            final boolean acceptableAck = seg.isAck() && seg.ack() == snd_nxt;
            if (acceptableAck) {
                ctx.fireUserEventTriggered(HANDSHAKE_RESET_EVENT);
                ReferenceCountUtil.release(seg);
                state = CLOSED;
                return;
            }
            else {
                ReferenceCountUtil.release(seg);
                return;
            }
        }

        if (seg.isSyn()) {
            rcv_nxt = seg.seq() + 1;
            irs = seg.seq();
            if (seg.isAck()) {
                snd_una = seg.ack();
            }

            final boolean ourSynHasBeenAcked = snd_una > iss;
            if (ourSynHasBeenAcked) {
                cancelTimeoutGuards();
                state = ESTABLISHED;
                // ACK
                final int seq = snd_nxt;
                final int ack = rcv_nxt;
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(seq, ack);
                writeSegment(ctx, response);
                ReferenceCountUtil.release(seg);

                ctx.fireUserEventTriggered(new ConnectionHandshakeCompleted(snd_nxt, rcv_nxt));

                openPromise.setSuccess();
            }
            else {
                state = SYN_RECEIVED;
                // SYN/ACK
                final int seq = iss;
                final int ack = rcv_nxt;
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.synAck(seq, ack);
                writeSegment(ctx, response);
                ReferenceCountUtil.release(seg);
            }
        }
    }

    private void channelReadSegmentSynReceivedState(final ChannelHandlerContext ctx,
                                                    final ConnectionHandshakeSegment seg) {
        // check SEQ
        if (seg.seq() != rcv_nxt && !seg.isAck()) {
            // not expected seq
            if (!seg.isRst()) {
                final int seq = snd_nxt;
                final int ack = rcv_nxt;
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(seq, ack);
                writeSegment(ctx, response);
            }
            ReferenceCountUtil.release(seg);
            return;
        }

        // check RST
        if (seg.isRst()) {
            if (activeOpen) {
                // connection has been refused by remote
                ctx.fireExceptionCaught(new ConnectionHandshakeException("Error: Connection refused"));
                state = CLOSED;
                ReferenceCountUtil.release(seg);
                return;
            }
            else {
                // peer is not longer interested in a connection. Go back to previous state
                state = LISTEN;
                ReferenceCountUtil.release(seg);
                return;
            }
        }

        if (seg.isAck()) {
            if (snd_una <= seg.ack() && seg.ack() <= snd_nxt) {
                cancelTimeoutGuards();
                state = ESTABLISHED;

                if (snd_una < seg.ack() && seg.ack() <= snd_nxt) {
                    snd_una = seg.ack();
                }
                else if (seg.ack() < snd_una) {
                    // ACK is duplicate, ignore
                    ReferenceCountUtil.release(seg);
                }
                else if (seg.ack() > snd_nxt) {
                    // ACK acks something not yet sent
                    System.out.println();
                }

                ReferenceCountUtil.release(seg);
                ctx.fireUserEventTriggered(new ConnectionHandshakeCompleted(snd_nxt, rcv_nxt));

                if (openPromise != null) {
                    openPromise.setSuccess();
                }
            }
            else {
                ReferenceCountUtil.release(seg);
                final int seq = seg.ack();
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.rst(seq);
                writeSegment(ctx, response);
                return;
            }
        }
    }

    private void channelReadSegmentEstablishedState(final ChannelHandlerContext ctx,
                                                    final ConnectionHandshakeSegment seg) {
        // check SEQ
        if (seg.seq() != rcv_nxt && !seg.isAck()) {
            // not expected seq
            if (!seg.isRst()) {
                final int seq = snd_nxt;
                final int ack = rcv_nxt;
                final ConnectionHandshakeSegment response = ConnectionHandshakeSegment.ack(seq, ack);
                writeSegment(ctx, response);
            }
            ReferenceCountUtil.release(seg);
            return;
        }

        // check RST
        if (seg.isRst()) {
            ctx.fireExceptionCaught(new ConnectionHandshakeException("Error: Connection reset"));
            state = CLOSED;
            ReferenceCountUtil.release(seg);
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
}
