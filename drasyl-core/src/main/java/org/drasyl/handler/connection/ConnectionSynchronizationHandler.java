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

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.ConnectionSynchronizationHandler.State.CLOSED;
import static org.drasyl.handler.connection.ConnectionSynchronizationHandler.State.ESTABLISHED;
import static org.drasyl.handler.connection.ConnectionSynchronizationHandler.State.LISTEN;
import static org.drasyl.handler.connection.ConnectionSynchronizationHandler.State.SYN_RECEIVED;
import static org.drasyl.handler.connection.ConnectionSynchronizationHandler.State.SYN_SENT;
import static org.drasyl.handler.connection.ConnectionSynchronizationHandler.UserCall.OPEN;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.RandomUtil.randomInt;

/**
 * This handler performs a synchronization with the remote peer. The synchronization has been
 * heavily inspired by the three-way handshake of TCP (<a href="https://datatracker.ietf.org/doc/html/rfc793#section-3.4">RFC
 * 793</a>). Depending on configuration, the synchronization will be automaticall initiated on
 * {@link #channelActive(ChannelHandlerContext)} or manually after writing {@link UserCall#OPEN} to
 * the channel. Once the synchronization is done, a {@link ConnectionSynchronized} event will be
 * passed to channel.
 */
public class ConnectionSynchronizationHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionSynchronizationHandler.class);
    private ChannelPromise openPromise;
    private final int synchronizationTimeout;
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
    Segment retransmissionSegment;
    private ScheduledFuture<?> synchronizationTimeoutFuture;
    private ScheduledFuture<?> retransmissionTimeoutFuture;

    public ConnectionSynchronizationHandler(final boolean activeOpen) {
        this(20000, () -> randomInt(Integer.MAX_VALUE - 1), activeOpen, CLOSED, 0, 0, 0);
    }

    /**
     * @param synchronizationTimeout
     * @param issProvider            Provider to generate the initial send sequence number
     * @param activeOpen             Initiate synchronization process automatically on {@link
     *                               #channelActive(ChannelHandlerContext)}
     * @param state                  Current synchronization state
     * @param snd_una                Oldest unacknowledged sequence number
     * @param snd_nxt                Next sequence number to be sent
     * @param rcv_nxt                Next expected sequence number
     */
    ConnectionSynchronizationHandler(final int synchronizationTimeout,
                                     final Supplier<Integer> issProvider,
                                     final boolean activeOpen,
                                     final State state,
                                     final int snd_una,
                                     final int snd_nxt,
                                     final int rcv_nxt) {
        this.synchronizationTimeout = requireNonNegative(synchronizationTimeout);
        this.issProvider = requireNonNull(issProvider);
        this.state = requireNonNull(state);
        this.snd_una = snd_una;
        this.snd_nxt = snd_nxt;
        this.rcv_nxt = rcv_nxt;
        this.activeOpen = activeOpen;
    }

    public ConnectionSynchronizationHandler() {
        this(false);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();

        if (activeOpen) {
            // active OPEN
            connectionOpen(ctx, ctx.newPromise());
        }
        else {
            // passive OPEN
            state = LISTEN;
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg == OPEN) {
            connectionOpen(ctx, promise);
        }
        else {
            ctx.write(msg, promise);
        }
    }

    private void connectionOpen(final ChannelHandlerContext ctx,
                                final ChannelPromise promise) {
        if (state == LISTEN) {
            openPromise = promise;
            state = SYN_SENT;

            // update send state
            iss = issProvider.get();
            snd_una = iss;
            snd_nxt = iss + 1;

            // send SYN
            final int seq = iss;
            final Segment seg = Segment.syn(seq);
            LOG.trace("Initiate handshake by sending `{}`", seg);
            writeSegment(ctx, seg);

//            if (synchronizationTimeout > 0) {
//                synchronizationTimeoutFuture = ctx.executor().schedule(() -> {
//                    if (retransmissionTimeoutFuture != null) {
//                        retransmissionTimeoutFuture.cancel(false);
//                        retransmissionTimeoutFuture = null;
//                    }
//                    ctx.fireExceptionCaught(new ConnectionSynchronizationException("handshake timed out"));
//                }, synchronizationTimeout, MILLISECONDS);
//            }
        }
        else {
            promise.setFailure(new IllegalStateException("Connection is already open."));
        }
    }

    private void writeSegment(final ChannelHandlerContext ctx, final Segment seg) {
        LOG.trace("Write {}", seg);
        ctx.writeAndFlush(seg);

//        if (seg.ctl() != 16) {
//            LOG.trace("Schedule timeout");
//            if (retransmissionTimeoutFuture != null) {
//                retransmissionTimeoutFuture.cancel(false);
//            }
//            retransmissionTimeoutFuture = ctx.executor().schedule(() -> retransmissionTimeout(ctx, seg), 1000L, MILLISECONDS);
//        }
    }

    private void channelReadSegment(final ChannelHandlerContext ctx, final Segment seg) {
        LOG.trace("Read {}", seg);

        switch (state) {
            case CLOSED:
                if (seg.isRst()) {
                    // as we are already/still in CLOSED state, we can ignore the RST
                    ReferenceCountUtil.release(seg);
                    return;
                }

                // at this point we're not (longer) willing to synchronize.
                // reset the peer
                final Segment response;
                if (seg.isAck()) {
                    // sent normal RST as we don't want to ACK an ACK
                    response = Segment.rst(seg.ack());
                }
                else {
                    response = Segment.rstAck(0, seg.seq());
                }
                ctx.writeAndFlush(response);
                ReferenceCountUtil.release(seg);

                break;

            case LISTEN:
                if (seg.isRst()) {
                    // as we are already/still in CLOSED state, we can ignore the RST
                    ReferenceCountUtil.release(seg);
                    return;
                }

                if (seg.isAck()) {
                    // we are on a state were we have never sent anything that must be ACKed
                    final Segment response2 = Segment.rst(seg.ack());
                    ctx.writeAndFlush(response2);
                    ReferenceCountUtil.release(seg);
                    return;
                }

                if (seg.isSyn()) {
                    // yay, peer SYNced with us
                    state = SYN_RECEIVED;

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
                    final Segment response3 = Segment.synAck(seq, ack);
                    ctx.writeAndFlush(response3);
                    ReferenceCountUtil.release(seg);
                    return;
                }

                // we should not reach this point. However, if it does happen, just drop the segment
                unexpectedSegment(seg);

            case SYN_SENT:
                if (seg.isAck()) {
                    if (seg.ack() <= iss || seg.ack() > snd_nxt) {
                        // segment ACKed something we never sent
                        if (!seg.isRst()) {
                            final Segment response2 = Segment.rst(seg.ack());
                            ctx.writeAndFlush(response2);
                        }
                        ReferenceCountUtil.release(seg);
                        return;
                    }
                }

                if (seg.isRst()) {
                    final boolean acceptableAck = seg.isAck() && seg.ack() == snd_nxt;
                    if (acceptableAck) {
                        ctx.fireUserEventTriggered(new ConnectionSynchronizationException("error: connection reset"));
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

                    boolean ourSynHasBeenAcked = snd_una > snd_nxt;
                    if (ourSynHasBeenAcked) {
                        state = ESTABLISHED;
                        // ACK
                    }
                    else {
                        state = SYN_RECEIVED;
                        // SYN/ACK
                    }
                }

                System.out.println();
                break;
        }
//
//        if (state == CLOSED) {
//            // ACK
//            if (seg.ctl() == 16) {
//                // we dont expect ACKs here
//                final int seq = seg.ack();
//                final Segment response = Segment.rst(seq);
//                LOG.trace("Got unexpected `{}`. Reset remote peer by replying with `{}`.", seg, response);
//                writeSegment(ctx, response);
//            }
//            // RST
//            else if (seg.ctl() == 4) {
//                // remote instructions us to reset connection. as we are still in the CLOSED state, we can just ignore the segment
//                ReferenceCountUtil.release(seg);
//            }
//            // SYN
//            else if (seg.ctl() == 2) {
//                state = SYN_RECEIVED;
//
//                // synchronize receive state
//                rcv_nxt = seg.seq() + 1;
//                irs = seg.seq();
//
//                // update send state
//                iss = issProvider.get();
//                snd_una = iss;
//                snd_nxt = iss + 1;
//
//                // send SYN/ACK
//                final int seq = iss;
//                final int ack = rcv_nxt;
//                final Segment response = Segment.synAck(seq, ack);
//                LOG.trace("Remote peer sent us his state (`{}`). Now acknowledge the receival and sent our state (`{}`).", seg, response);
//                writeSegment(ctx, response);
//            }
//            // ACK/SYN
//            else if (seg.ctl() == 18) {
//                // as we are in a CLOSED state, ignore the ACK-part of this segment and just respect
//                // the SYN-part
//                state = SYN_RECEIVED;
//
//                // synchronize receive state
//                rcv_nxt = seg.seq() + 1;
//                irs = seg.seq();
//
//                // update send state
//                iss = issProvider.get();
//                snd_una = iss;
//                snd_nxt = iss + 1;
//
//                // send SYN/ACK
//                final int seq = iss;
//                final int ack = rcv_nxt;
//                final Segment response = Segment.synAck(seq, ack);
//                LOG.trace("Remote peer sent us his state (`{}`). Now acknowledge the receival and sent our state (`{}`).", seg, response);
//                writeSegment(ctx, response);
//            }
//        }
//        else if (state == SYN_SENT) {
//            // SYN/ACK
//            if (seg.ctl() == 18) {
//                // verify ACK
//                if (seg.ack() != snd_nxt) {
//                    throw new UnsupportedOperationException("Expected SEG " + snd_nxt + " got " + seg.ack());
//                }
//                segmentAcknowledged();
//
//                // our SYN has been ACKed
//                snd_una = seg.ack();
//
//                // verify SYN
//                // synchronize receive state
//                rcv_nxt = seg.seq() + 1;
//                irs = seg.seq();
//
//                state = ESTABLISHED;
//
//                // ACK the SYN
//                final int seq = snd_nxt;
//                final int ack = rcv_nxt;
//                final Segment response = Segment.ack(seq, ack);
//                writeSegment(ctx, response);
//
//                LOG.trace("Connection synchronized! Remote peer sent us his state (`{}`). All states has been synchronized. Now acknowledge the receival by replying with `{}`.", seg, response);
//
//                final ConnectionSynchronized evt = new ConnectionSynchronized(snd_nxt, rcv_nxt);
//                ctx.fireUserEventTriggered(evt);
//
//                if (synchronizationTimeoutFuture != null) {
//                    synchronizationTimeoutFuture.cancel(false);
//                }
//            }
//            // SYN
//            else if (seg.ctl() == 2) {
//                // synchronize receive state
//                rcv_nxt = seg.seq() + 1;
//                irs = seg.seq();
//
//                state = SYN_RECEIVED;
//
//                // ACK the SYN and "re-send" our SYN
//                final int seq = iss;
//                final int ack = rcv_nxt;
//                final Segment response = Segment.synAck(seq, ack);
//                LOG.trace("Remote peer sent us his state (`{}`). Now acknowledge the receival and sent our state (`{}`).", seg, response);
//                writeSegment(ctx, response);
//            }
//            // ACK
//            else if (seg.ctl() == 16) {
//                // we dont expect ACKs here
//                final int seq = seg.ack();
//                final Segment response = Segment.rst(seq);
//                LOG.trace("1 Got unexpected `{}`. Reply with `{}`.", seg, response);
//                writeSegment(ctx, response);
//
//                // FIXME: remove when we have retransmission
////                final int seq2 = iss;
////                final Segment seg2 = Segment.syn(seq2);
////                LOG.trace("2 Initiate handshake by sending `{}`", seg2);
////                writeSegment(ctx, seg2);
//            }
//            else {
//                unexpectedSegment(seg);
//            }
//        }
//        else if (state == SYN_RECEIVED) {
//            // ACK
//            if (seg.ctl() == 16) {
//                // verify ACK
//                if (seg.ack() != snd_nxt) {
//                    throw new UnsupportedOperationException("Expected SEG " + snd_nxt + " got " + seg.ack());
//                }
//                segmentAcknowledged();
//
//                // our SYN has been ACKed
//                snd_una = seg.ack();
//
//                state = ESTABLISHED;
//
//                LOG.trace("Connection synchronized! Remote peer has acknowledged the receival of our state replying with `{}`.", seg);
//
//                final ConnectionSynchronized evt = new ConnectionSynchronized(snd_nxt, rcv_nxt);
//                ctx.fireUserEventTriggered(evt);
//
//                if (synchronizationTimeoutFuture != null) {
//                    synchronizationTimeoutFuture.cancel(false);
//                }
//            }
//            // SYN/ACK
//            else if (seg.ctl() == 18) {
//                // verify ACK
//                if (seg.ack() != snd_nxt) {
//                    // unexpected ACK
//                    final int seq = snd_nxt;
//                    final int ack = rcv_nxt;
//                    final Segment response = Segment.ack(seq, ack);
//                    LOG.trace("5 Got unexpected `{}`. Reply with expected SEG `{}`.", seg, response);
//                    writeSegment(ctx, response);
//                    return;
//                }
//                segmentAcknowledged();
//
//                // our SYN has been ACKed
//                snd_una = seg.ack();
//
//                // verify SYN
//                // synchronize receive state
//                rcv_nxt = seg.seq() + 1;
//                irs = seg.seq();
//
//                state = ESTABLISHED;
//
//                LOG.trace("Connection synchronized! Remote peer sent us his state (`{}`) and acknowledged our state.", seg);
//
//                final ConnectionSynchronized evt = new ConnectionSynchronized(snd_nxt, rcv_nxt);
//                ctx.fireUserEventTriggered(evt);
//
//                if (synchronizationTimeoutFuture != null) {
//                    synchronizationTimeoutFuture.cancel(false);
//                }
//            }
//            // RST
//            else if (seg.ctl() == 4) {
//                // handshake has been refused by the other site
//                LOG.trace("Close channel as we got `{}`", seg);
//                state = CLOSED;
//                ctx.close();
//            }
//            // SYN
//            else if (seg.ctl() == 2) {
//                // we have already been synced. nevermind...sync again
//                // synchronize receive state
//                rcv_nxt = seg.seq() + 1;
//                irs = seg.seq();
//
//                state = SYN_RECEIVED;
//
//                // ACK the SYN
//                final int seq = iss;
//                final int ack = rcv_nxt;
//                final Segment response = Segment.synAck(seq, ack);
//                writeSegment(ctx, response);
//            }
//            else {
//                unexpectedSegment(seg);
//            }
//        }
//        else if (state == ESTABLISHED) {
//            // SYN
//            if (seg.ctl() == 2) {
//                // we dont expect SYNs here
//                final int seq = snd_nxt;
//                final int ack = rcv_nxt;
//                final Segment response = Segment.ack(seq, ack);
//                LOG.trace("3 Got unexpected `{}`. Reply with expected SEG `{}`.", seg, response);
//                writeSegment(ctx, response);
//            }
//            // RST
//            else if (seg.ctl() == 4) {
//                LOG.trace("Close channel as we got `{}`", seg);
//                state = CLOSED;
//                ctx.close();
//            }
//            // SYN/ACK
//            else if (seg.ctl() == 18) {
//                // we dont expect SYNs here
//                final int seq = snd_nxt;
//                final int ack = rcv_nxt;
//                final Segment response = Segment.ack(seq, ack);
//                LOG.trace("4 Got unexpected `{}`. Reply with `{}`.", seg, response);
//                writeSegment(ctx, response);
//            }
//            // ACK
//            else if (seg.ctl() == 16) {
//                // verify ACK
//                if (seg.ack() != snd_nxt) {
//                    // unexpected ACK
//                    final int seq = snd_nxt;
//                    final int ack = rcv_nxt;
//                    final Segment response = Segment.ack(seq, ack);
//                    LOG.trace("6 Got unexpected `{}`. Reply with expected SEG `{}`.", seg, response);
//                    writeSegment(ctx, response);
//                    return;
//                }
//                segmentAcknowledged();
//            }
//            else {
//                unexpectedSegment(seg);
//            }
//        }
//        else {
//            unexpectedSegment(seg);
//        }
    }

    private void unexpectedSegment(final Segment seg) {
        ReferenceCountUtil.release(seg);
        throw new UnsupportedOperationException("Got unexpected segment `" + seg + "` in state `" + state + "`");
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof Segment) {
            channelReadSegment(ctx, (Segment) msg);
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    private void segmentAcknowledged() {
        if (retransmissionTimeoutFuture != null) {
            retransmissionTimeoutFuture.cancel(false);
            retransmissionTimeoutFuture = null;
        }
    }

    private void retransmissionTimeout(final ChannelHandlerContext ctx, final Segment seg) {
        if (retransmissionTimeoutFuture != null) {
            LOG.trace("Timeout! No ACK received for {} within x seconds. Retransmit!", seg);
            writeSegment(ctx, seg);
        }
    }

    /**
     * States of the synchronization progress
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
     * Signals to control the synchronization progress.
     */
    public enum UserCall {
        // initiate synchronization process
        OPEN
    }

    /**
     * Signals that the connection has been synchronized
     */
    public static class ConnectionSynchronized {
        private final int snd_nxt;
        private final int rcv_nxt;

        public ConnectionSynchronized(final int snd_nxt, final int rcv_nxt) {
            this.snd_nxt = snd_nxt;
            this.rcv_nxt = rcv_nxt;
        }

        /**
         * Returns the state that has been shared with the remote peer.
         *
         * @return state that has been shared with the remote peer
         */
        public int snd_nxt() {
            return snd_nxt;
        }

        /**
         * Returns the state that has been received from the remote peer.
         *
         * @return state that has been received from the remote peer
         */
        public int rcv_nxt() {
            return rcv_nxt;
        }

        @Override
        public String toString() {
            return "ConnectionSynchronized{" +
                    "snd_nxt=" + snd_nxt +
                    ", rcv_nxt=" + rcv_nxt +
                    '}';
        }
    }

    public static class ConnectionSynchronizationException extends Exception {
        public ConnectionSynchronizationException(final String message) {
            super(message);
        }
    }
}
