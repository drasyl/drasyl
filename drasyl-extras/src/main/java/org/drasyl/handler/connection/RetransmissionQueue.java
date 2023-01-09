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
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.handler.connection.ConnectionHandshakeSegment.Option;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.EnumMap;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.ACK;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.FIN;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.PSH;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.SEQ_NO_SPACE;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.SYN;
import static org.drasyl.util.SerialNumberArithmetic.lessThan;

/**
 * Holds all segments that has been written to the network (called in-flight) but have not been
 * acknowledged yet. This FIFO queue also updates the {@link io.netty.channel.Channel} writability
 * for the bytes it holds.
 */
public class RetransmissionQueue {
    private static final Logger LOG = LoggerFactory.getLogger(RetransmissionQueue.class);
    private final Channel channel;
    private ScheduledFuture<?> retransmissionTimer;
    private long synSeq = -1;
    private long pshSeq = -1;
    private long finSeq = -1;

    RetransmissionQueue(final Channel channel) {
        this.channel = requireNonNull(channel);
    }

    public void add(final ChannelHandlerContext ctx,
                    final ConnectionHandshakeSegment seg,
                    final TransmissionControlBlock tcb) {
        if (seg.isSyn()) {
            synSeq = seg.seq();
        }
        if (seg.isPsh()) {
            pshSeq = seg.seq();
        }
        if (seg.isFin()) {
            finSeq = seg.seq();
        }

        recreateRetransmissionTimer(ctx, tcb);
    }

    /**
     * Release all buffers in the queue and complete all listeners and promises.
     */
    public void releaseAndFailAll() {
        synSeq = -1;
        pshSeq = -1;
        finSeq = -1;
        // FIXME: fail stuff in SendBuffer?
    }

    @Override
    public String toString() {
        return "RTNS.Q(SYN=" + synSeq + ", PSH=" + pshSeq + ", FIN=" + finSeq + ")";
    }

    public void handleAcknowledgement(final ChannelHandlerContext ctx,
                                      final ConnectionHandshakeSegment seg,
                                      final TransmissionControlBlock tcb,
                                      final RttMeasurement rttMeasurement,
                                      final long ackedBytes) {
        boolean somethingWasAcked = ackedBytes > 0;
        if (synSeq != -1 && lessThan(synSeq, tcb.sndUna(), SEQ_NO_SPACE)) {
            // SYN has been ACKed
            synSeq = -1;
            somethingWasAcked = true;
        }
        if (pshSeq != -1 && lessThan(pshSeq, tcb.sndUna(), SEQ_NO_SPACE)) {
            // PSH has been ACKed
            pshSeq = -1;
            somethingWasAcked = true;
        }
        if (finSeq != -1 && lessThan(finSeq, tcb.sndUna(), SEQ_NO_SPACE)) {
            // FIN has been ACKed
            finSeq = -1;
            somethingWasAcked = true;
        }

        if (somethingWasAcked) {
            tcb.sendBuffer().acknowledge((int) ackedBytes);
        }

        boolean queueWasNotEmpty = ackedBytes != 0;
        Object current = null;
        if (queueWasNotEmpty) {
            if (tcb.sendBuffer().acknowledgeableBytes() == 0) {
                // everything was ACKed, cancel retransmission timer
                cancelRetransmissionTimer();
            } else if (somethingWasAcked) {
                // as something was ACKed, recreate retransmission timer
                recreateRetransmissionTimer(ctx, tcb);
            }
        }
    }

    private void recreateRetransmissionTimer(final ChannelHandlerContext ctx,
                                             final TransmissionControlBlock tcb) {
        // reset existing timer
        if (retransmissionTimer != null) {
            retransmissionTimer.cancel(false);
        }

        // create new timer
        long rto = (long) tcb.rttMeasurement().rto();
        retransmissionTimer = ctx.executor().schedule(() -> {
            // https://www.rfc-editor.org/rfc/rfc6298 kapitel 5

            // retransmit the earliest segment that has not been acknowledged
            final EnumMap<Option, Object> options = new EnumMap<>(Option.class);
            final ByteBuf data = tcb.sendBuffer().unacknowledged(tcb.mss());
            byte ctl = ACK;
            if (synSeq != -1) {
                ctl |= SYN;
            } else if (pshSeq != -1) {
                ctl |= PSH;
            } else if (finSeq != -1) {
                ctl |= FIN;
            }
            if (ctl == ACK && data.readableBytes() == 0) {
                System.out.printf("");
            }
            // FIXME: wann PSH hinzufügen?
            ConnectionHandshakeSegment retransmission = new ConnectionHandshakeSegment(tcb.sndUna(), tcb.rcvNxt(), ctl, tcb.rcvWnd(), options, data);
            LOG.error("{} Retransmission timeout after {}ms! Retransmit: {}", channel, rto, retransmission);

            ctx.writeAndFlush(retransmission);

            // The host MUST set RTO <- RTO * 2 ("back off the timer")
            tcb.rttMeasurement().timeoutOccured();

            // Start the retransmission timer, such that it expires after RTO seconds
            recreateRetransmissionTimer(ctx, tcb);
        }, rto, MILLISECONDS);
    }

    private void cancelRetransmissionTimer() {
        if (retransmissionTimer != null) {
            retransmissionTimer.cancel(false);
            retransmissionTimer = null;
        }
    }
}
