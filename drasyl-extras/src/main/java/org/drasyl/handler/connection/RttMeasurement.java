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

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.handler.connection.ConnectionHandshakeSegment.Option.TIMESTAMPS;
import static org.drasyl.handler.connection.ConnectionHandshakeSegment.SEQ_NO_SPACE;
import static org.drasyl.handler.connection.RetransmissionTimeoutApplier.ALPHA;
import static org.drasyl.handler.connection.RetransmissionTimeoutApplier.BETA;
import static org.drasyl.util.SerialNumberArithmetic.lessThanOrEqualTo;

/**
 * Round-trip-time measurement as described in <a
 * href="https://www.rfc-editor.org/rfc/rfc7323.html#section-3">RFC 7323, Sections 3 and 4</a>.
 */
public class RttMeasurement {
    public static final int K = 4;
    public static final double G = 1.0 / 1_000; // clock granularity in seconds
    private static final Logger LOG = LoggerFactory.getLogger(RttMeasurement.class);
    public static final int LOWER_BOUND = 1_000;
    public static final int UPPER_BOUND = 60_000;
    long tsRecent; // holds a timestamp to be echoed in TSecr whenever a segment is sent
    long lastAckSent; // holds the ACK field from the last segment sent
    boolean addTimestamps;
    private double RTTVAR;
    private double SRTT = -1; // default value
    private double RTO = 1000; //  Until a round-trip time (RTT) measurement has been made for a segment sent between the sender and receiver, the sender SHOULD set RTO <- 1 second

    public void segmentArrives(final ChannelHandlerContext ctx,
                               final ConnectionHandshakeSegment seg,
                               final int smss,
                               final int flightSize) {
        final Object timestampsOption = seg.options().get(TIMESTAMPS);
        if (timestampsOption != null) {
            final long[] timestamps = (long[]) timestampsOption;
            final long tsVal = timestamps[0];
            final long tsEcr = timestamps[1];
            if (lessThanOrEqualTo(tsRecent, tsVal, SEQ_NO_SPACE) && lessThanOrEqualTo(seg.seq(), lastAckSent, SEQ_NO_SPACE)) {
                tsRecent = tsVal;

                // calculate RTO
                calculateRto(ctx, tsEcr, smss, flightSize);
            }
        }
    }

    public void write(final ConnectionHandshakeSegment seg) {
        if (!addTimestamps && seg.isSyn()) {
            // we start adding timestamps only with the first SYN
            // otherwise, RST messages caused by unexpected segments before the handshake will contain timestamps
            addTimestamps = true;
        }

        if (addTimestamps) {
            // TSval contains current value of the sender's timestamp clock
            final long tsVal = System.nanoTime() / 1_000_000;

            // tsEcr is only valid if ACK bit is set
            final long tsEcr;
            if (seg.isAck()) {
                // when timestamps are sent, the TSecr field is set to the current TS.Recent value
                tsEcr = tsRecent;
            }
            else {
                // when ACK bit is not set, set TSecr field to zero
                tsEcr = 0;
            }

            // add timestamps to segment
            seg.options().put(TIMESTAMPS, new long[]{ tsVal, tsEcr });

            // record ACK field from the last segment sent
            lastAckSent = seg.ack();
        }
    }

    private void calculateRto(final ChannelHandlerContext ctx,
                              final long tsEcr,
                              final int smss,
                              final int flightSize) {
        if (SRTT == -1) {
            // first measurement
            int r = (int) (System.nanoTime() / 1_000_000 - tsEcr);
            SRTT = r;
            RTTVAR = r / 2.0;
            RTO = SRTT + Math.max(G, K * RTTVAR);
        }
        else {
            // subsequent measurement
            int rDash = (int) (System.nanoTime() / 1_000_000 - tsEcr);

            int ExpectedSamples = (int) Math.ceil(flightSize / (smss * 2));
            double alphaDash = ALPHA / ExpectedSamples;
            double betaDash = BETA / ExpectedSamples;
            RTTVAR = (1 - betaDash) * RTTVAR + betaDash * Math.abs(SRTT - rDash);
            SRTT = (1 - alphaDash) * SRTT + alphaDash * rDash;
            RTO = SRTT + Math.max(G, K * RTTVAR);
        }

        if (RTO < LOWER_BOUND) {
            // Whenever RTO is computed, if it is less than 1 second, then the
            //         RTO SHOULD be rounded up to 1 second.
            RTO = LOWER_BOUND;
        }
        else if (RTO > UPPER_BOUND) {
            // A maximum value MAY be placed on RTO provided it is at least 60
            //         seconds.
            RTO = UPPER_BOUND;
        }

        LOG.error("{} RTO set to {}ms.", ctx.channel(), RTO);
    }
}
