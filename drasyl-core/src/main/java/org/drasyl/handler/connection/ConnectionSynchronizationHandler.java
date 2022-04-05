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
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.ConnectionSynchronizationHandler.State.CLOSED;
import static org.drasyl.handler.connection.ConnectionSynchronizationHandler.State.ESTABLISHED;
import static org.drasyl.handler.connection.ConnectionSynchronizationHandler.State.SYN_RECEIVED;
import static org.drasyl.handler.connection.ConnectionSynchronizationHandler.State.SYN_SENT;
import static org.drasyl.handler.connection.ConnectionSynchronizationHandler.UserCall.OPEN;
import static org.drasyl.util.RandomUtil.randomInt;

/**
 * This handler performs a synchronization with the remote peer. The synchronization has been
 * heavily inspired by the three-way handshake of TCP (<a href="https://datatracker.ietf.org/doc/html/rfc793#section-3.4">RFC
 * 793</a>).
 */
public class ConnectionSynchronizationHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionSynchronizationHandler.class);
    private ChannelPromise openPromise;

    static enum State {
        // connection does not exist
        CLOSED,
        // connection non-synchronized
        SYN_SENT,
        SYN_RECEIVED,
        // connection synchronized
        ESTABLISHED,
        FIN_WAIT_1,
        FIN_WAIT_2,
        CLOSE_WAIT,
        CLOSING,
        LAST_ACK,
        TIME_WAIT
    }

    public static enum UserCall {
        // initiate synchronization process
        OPEN,
        CLOSE
    }

    private final Supplier<Integer> issProvider;
    private final boolean autoOpen;
    State state;
    // Send Sequence Variables
    int snd_una; // oldest unacknowledged sequence number
    int snd_nxt; // next sequence number to be sent
    int iss; // initial send sequence number
    // Receive Sequence Variables
    int rcv_nxt; // next sequence number expected on an incoming segments, and is the left or lower edge of the receive window
    int irs; // initial receive sequence number

    /**
     * @param issProvider Provider to generate the initial send sequence number
     * @param autoOpen    Initiate synchronization process automatically on {@link
     *                    #channelActive(ChannelHandlerContext)}
     * @param state       Current synchronization state
     * @param snd_una     Oldest unacknowledged sequence number
     * @param snd_nxt     Next sequence number to be sent
     * @param rcv_nxt     Next expected sequence number
     */
    ConnectionSynchronizationHandler(final Supplier<Integer> issProvider,
                                     final boolean autoOpen,
                                     final State state,
                                     final int snd_una,
                                     final int snd_nxt,
                                     final int rcv_nxt) {
        this.issProvider = requireNonNull(issProvider);
        this.state = requireNonNull(state);
        this.snd_una = snd_una;
        this.snd_nxt = snd_nxt;
        this.rcv_nxt = rcv_nxt;
        this.autoOpen = autoOpen;
    }

    public ConnectionSynchronizationHandler() {
        this(() -> randomInt(Integer.MAX_VALUE - 1), false, CLOSED, 0, 0, 0);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();

        if (autoOpen) {
            connectionOpen(ctx, ctx.newPromise());
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
        if (state == CLOSED) {
            state = SYN_SENT;
            openPromise = promise;

            // update send state
            iss = issProvider.get();
            snd_una = iss;
            snd_nxt = iss + 1;

            // send SYN
            final int seq = iss;
            final Segment seg = Segment.syn(seq);
            LOG.trace("1 Initiate handshake by sending `{}`", seg);
            ctx.write(seg);
        }
        else {
            openPromise.setFailure(new IllegalStateException("Connection is already open."));
        }
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

    private void channelReadSegment(final ChannelHandlerContext ctx, final Segment seg) {
        if (state == CLOSED) {
            // SYN
            if (seg.ctl() == 2) {
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
                final Segment response = Segment.synAck(seq, rcv_nxt);
                LOG.trace("Response to handshake initiation `{}` with `{}`", seg, response);
                ctx.writeAndFlush(response);
            }
            // ACK
            else if (seg.ctl() == 16) {
                // we dont expect ACKs here
                final int seq = seg.ack();
                final Segment response = Segment.rst(seq);
                LOG.trace("2 Got unexpected `{}`. Reply with `{}`.", seg, response);
                ctx.writeAndFlush(response);
            }
            // ACK/SYN
            else if (seg.ctl() == 18) {
                // as we are in a closed state, ignore the ACK-part of this segment
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
                final Segment response = Segment.synAck(seq, rcv_nxt);
                LOG.trace("Response to handshake initiation `{}` with `{}`", seg, response);
                ctx.writeAndFlush(response);
            }
            else {
                unexpectedSegment(seg);
            }
        }
        else if (state == SYN_SENT) {
            // SYN/ACK
            if (seg.ctl() == 18) {
                // verify ACK
                if (seg.ack() != snd_nxt) {
                    throw new UnsupportedOperationException();
                }

                // our SYN has been ACKed
                snd_una = seg.ack();

                // verify SYN
                // synchronize receive state
                rcv_nxt = seg.seq() + 1;
                irs = seg.seq();

                state = ESTABLISHED;

                // ACK the SYN
                final int seq = snd_nxt;
                final int ack = rcv_nxt;
                final Segment response = Segment.ack(seq, ack);
                ctx.writeAndFlush(response);

                LOG.trace("Handshake done on our side after receiving `{}` and sending `{}`. Connection established.", seg, response);
            }
            // SYN
            else if (seg.ctl() == 2) {
                // synchronize receive state
                rcv_nxt = seg.seq() + 1;
                irs = seg.seq();

                state = SYN_RECEIVED;

                // ACK the SYN and "re-send" our SYN
                final int seq = iss;
                final int ack = rcv_nxt;
                final Segment response = Segment.synAck(seq, ack);
                ctx.writeAndFlush(response);

                LOG.trace("Other side synchronizes his state with us. Now synchronize our state with other side.");
            }
            // ACK
            else if (seg.ctl() == 16) {
                // we dont expect ACKs here
                final int seq = seg.ack();
                final Segment response = Segment.rst(seq);
                LOG.trace("1 Got unexpected `{}`. Reply with `{}`.", seg, response);
                ctx.writeAndFlush(response);

                // FIXME: remove when we have retransmission
                final int seq2 = iss;
                final Segment seg2 = Segment.syn(seq2);
                LOG.trace("2 Initiate handshake by sending `{}`", seg2);
                ctx.writeAndFlush(seg2);
            }
            else {
                unexpectedSegment(seg);
            }
        }
        else if (state == SYN_RECEIVED) {
            // ACK
            if (seg.ctl() == 16) {
                // verify ACK
                if (seg.ack() != snd_nxt) {
                    throw new UnsupportedOperationException();
                }

                // our SYN has been ACKed
                snd_una = seg.ack();

                state = ESTABLISHED;

                LOG.trace("Handshake done on our side after receiving `{}`. Connection established.", seg);
            }
            // SYN/ACK
            else if (seg.ctl() == 18) {
                // verify ACK
                if (seg.ack() != snd_nxt) {
                    // unexpected ACK
                    final int seq = snd_nxt;
                    final int ack = rcv_nxt;
                    final Segment response = Segment.ack(seq, ack);
                    LOG.trace("5 Got unexpected `{}`. Reply with expected SEG `{}`.", seg, response);
                    ctx.writeAndFlush(response);
                    return;
                }

                // our SYN has been ACKed
                snd_una = seg.ack();

                state = ESTABLISHED;

                LOG.trace("Handshake done on our side after receiving `{}`. Connection established.", seg);
            }
            // RST
            else if (seg.ctl() == 4) {
                // handshake has been refused by the other site
                LOG.trace("Close channel as we got `{}`", seg);
                state = CLOSED;
                ctx.close();
            }
            // SYN
            else if (seg.ctl() == 2) {
                // we have already been synced. nevermind...sync again
                // synchronize receive state
                rcv_nxt = seg.seq() + 1;
                irs = seg.seq();

                state = SYN_RECEIVED;

                // ACK the SYN
                final int seq = iss;
                final int ack = rcv_nxt;
                final Segment response = Segment.ack(seq, ack);
                ctx.writeAndFlush(response);
            }
            else {
                unexpectedSegment(seg);
            }
        }
        else if (state == ESTABLISHED) {
            // SYN
            if (seg.ctl() == 2) {
                // we dont expect SYNs here
                final int seq = snd_nxt;
                final int ack = rcv_nxt;
                final Segment response = Segment.ack(seq, ack);
                LOG.trace("3 Got unexpected `{}`. Reply with expected SEG `{}`.", seg, response);
                ctx.writeAndFlush(response);
            }
            // RST
            else if (seg.ctl() == 4) {
                LOG.trace("Close channel as we got `{}`", seg);
                state = CLOSED;
                ctx.close();
            }
            // SYN/ACK
            else if (seg.ctl() == 18) {
                // we dont expect SYNs here
                final int seq = snd_nxt;
                final int ack = rcv_nxt;
                final Segment response = Segment.ack(seq, ack);
                LOG.trace("4 Got unexpected `{}`. Reply with `{}`.", seg, response);
                ctx.writeAndFlush(response);
            }
            // ACK
            else if (seg.ctl() == 16) {
                // verify ACK
                if (seg.ack() != snd_nxt) {
                    // unexpected ACK
                    final int seq = snd_nxt;
                    final int ack = rcv_nxt;
                    final Segment response = Segment.ack(seq, ack);
                    LOG.trace("6 Got unexpected `{}`. Reply with expected SEG `{}`.", seg, response);
                    ctx.writeAndFlush(response);
                    return;
                }
            }
            else {
                unexpectedSegment(seg);
            }
        }
        else {
            unexpectedSegment(seg);
        }
    }

    private UnsupportedOperationException unexpectedSegment(final Segment seg) {
        throw new UnsupportedOperationException("Got unexpected segment `" + seg + "` in state `" + state + "`");
    }
}
