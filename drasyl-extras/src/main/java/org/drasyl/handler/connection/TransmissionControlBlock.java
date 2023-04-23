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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.connection.Segment.MAX_SEQ_NO;
import static org.drasyl.handler.connection.Segment.MIN_SEQ_NO;
import static org.drasyl.handler.connection.Segment.SEG_HDR_SIZE;
import static org.drasyl.handler.connection.Segment.add;
import static org.drasyl.handler.connection.Segment.advanceSeq;
import static org.drasyl.handler.connection.Segment.sub;
import static org.drasyl.util.NumberUtil.max;
import static org.drasyl.util.NumberUtil.min;
import static org.drasyl.util.Preconditions.requireInRange;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.Preconditions.requirePositive;

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
@SuppressWarnings({ "java:S125", "java:S3776", "java:S6541" })
public class TransmissionControlBlock {
    static final int DRASYL_HDR_SIZE = 20 + 8 + 176;
    // RFC 9293: SendMSS is the MSS value received from the remote host, or the default 536 for IPv4
    // RFC 9293: or 1220 for IPv6, if no MSS Option is received.
    //
    // 536 is the result of an assumed MTU of 576 - IPv4 header (20) - TCP header (20) = 536
    // 1220 is the result of an assumed MTU of 1280 - IPv6 header (40) - TCP header (20) = 1220
    //
    // we instead, assume a MTU of 1460 for both IPv4 and IPv6. This is the smallest known MTU
    // on the Internet (applied by Google Cloud). We then have to remove the drasyl header
    // (DRASYL_HDR_SIZE) and our TCP header (31)
    public static final int DEFAULT_SEND_MSS = 1460 - DRASYL_HDR_SIZE - SEG_HDR_SIZE;
    private static final Logger LOG = LoggerFactory.getLogger(TransmissionControlBlock.class);
    private final RetransmissionQueue retransmissionQueue;
    private final SendBuffer sendBuffer;
    private final OutgoingSegmentQueue outgoingSegmentQueue;
    private final ReceiveBuffer receiveBuffer;
    private final int rcvBuff;
    private final ReliableConnectionConfig config;
    // RFC 9293: Send Sequence Variables
    // RFC 9293: SND.UNA = oldest unacknowledged sequence number
    private long sndUna;
    // RFC 9293: SND.NXT = next sequence number to be sent
    private long sndNxt;
    // RFC 9293: SND.WND = send window
    private long sndWnd;
    // RFC 9293: SND.WL1 = segment sequence number used for last window update
    private long sndWl1;
    // RFC 9293: SND.WL2 = segment acknowledgment number used for last window update
    private long sndWl2;
    // RFC 9293: initial send sequence number
    private long iss;

    // RFC 9293: Receive Sequence Variables
    // RFC 9293: RCV.NXT = next sequence number expected on an incoming segment, and is the left or
    // RFC 9293: lower edge of the receive window
    private long rcvNxt;
    // RFC 9293: receive window
    private long rcvWnd;
    // RFC 9293: IRS = initial receive sequence number
    private long irs;

    // RFC 9293: SendMSS is the MSS value received from the remote host, or the default 536 for IPv4
    // RFC 9293: or 1220 for IPv6, if no MSS Option is received.
    // The size does not include the TCP/IP headers and options.
    private long sendMss = DEFAULT_SEND_MSS;
    // RFC 9293: Silly Window Syndrome Avoidance
    // RFC 9293: the maximum send window it has seen so far on the connection, and to use this value
    // RFC 9293: as an estimate of RCV.BUFF
    private long maxSndWnd;
    // RFC 9293: To avoid a resulting deadlock, it is necessary to have a timeout to force
    // RFC 9293: transmission of data, overriding the SWS avoidance algorithm. In practice, this
    // RFC 9293: timeout should seldom occur.
    private ScheduledFuture<?> overrideTimer;

    // RFC 7323: Timestamps option
    // RFC 7323: TS.Recent = holds a timestamp to be echoed in TSecr whenever a segment is sent
    private long tsRecent;
    // RFC 7323: Last.ACK.sent = holds the ACK field from the last segment sent
    private long lastAckSent;
    // RFC 7323: Snd.TS.OK = remember successfull TSopt negotiation
    private boolean sndTsOk;

    // RFC 6298: Retransmission Timer Computation
    // RFC 6298: RTTVAR = round-trip time variation
    private double rttVar;
    // RFC 6298: SRTT = smoothed round-trip time
    private double sRtt;
    // RFC 6298: RTO = retransmission timeout
    // RFC 6298: Until a round-trip time (RTT) measurement has been made for a segment sent between
    // RFC 6298: the sender and receiver, the sender SHOULD set RTO <- 1 second
    private long rto;

    // RFC 5681: Congestion Control Algorithms
    // RFC 5681: The congestion window (cwnd) is a sender-side limit on the amount of data the
    // RFC 5681: sender can transmit into the network before receiving an acknowledgment (ACK),
    // RFC 5681: while the receiver's advertised window (rwnd) is a receiver-side limit on the
    // RFC 5681: amount of outstanding data.  The minimum of cwnd and rwnd governs data
    // RFC 5681: transmission.
    private long cwnd;
    // RFC 5681: the slow start threshold (ssthresh), is used to determine whether the slow start or
    // RFC 5681: congestion avoidance algorithm is used to control data transmission
    private long ssthresh;
    // RFC 5681:
    private long lastAdvertisedWindow;
    // RFC 5681:
    private int duplicateAcks;

    // RFC 6582: Fast Recovery Algorithm Modification (NewReno)
    // RFC 6582: When in fast recovery, this variable records the send sequence number that must be
    // RFC 6582: acknowledged before the fast recovery procedure is declared to be over.
    private long recover;

    @SuppressWarnings("java:S107")
    TransmissionControlBlock(final ReliableConnectionConfig config,
                             final long sndUna,
                             final long sndNxt,
                             final int sndWnd,
                             final long iss,
                             final long rcvNxt,
                             final int rcvWnd,
                             final int rcvBuff,
                             final long irs,
                             final SendBuffer sendBuffer,
                             final OutgoingSegmentQueue outgoingSegmentQueue,
                             final RetransmissionQueue retransmissionQueue,
                             final ReceiveBuffer receiveBuffer,
                             final long cwnd,
                             final long ssthresh,
                             final long maxSndWnd,
                             final long recover,
                             final long tsRecent,
                             final long lastAckSent,
                             final boolean sndTsOk,
                             final double rttVar,
                             final double sRtt,
                             final long rto) {
        this.config = requireNonNull(config);
        this.sndUna = requireInRange(sndUna, MIN_SEQ_NO, MAX_SEQ_NO);
        this.sndNxt = requireInRange(sndNxt, MIN_SEQ_NO, MAX_SEQ_NO);
        this.sndWnd = requireNonNegative(sndWnd);
        this.iss = requireInRange(iss, MIN_SEQ_NO, MAX_SEQ_NO);
        this.rcvNxt = requireInRange(rcvNxt, MIN_SEQ_NO, MAX_SEQ_NO);
        this.rcvWnd = requireNonNegative(rcvWnd);
        this.rcvBuff = requirePositive(rcvBuff);
        this.irs = requireInRange(irs, MIN_SEQ_NO, MAX_SEQ_NO);
        this.sendBuffer = requireNonNull(sendBuffer);
        this.outgoingSegmentQueue = requireNonNull(outgoingSegmentQueue);
        this.retransmissionQueue = requireNonNull(retransmissionQueue);
        this.receiveBuffer = requireNonNull(receiveBuffer);
        this.cwnd = requireNonNegative(cwnd);
        this.ssthresh = requireNonNegative(ssthresh);
        this.maxSndWnd = requireNonNegative(maxSndWnd);
        this.recover = requireInRange(recover, MIN_SEQ_NO, MAX_SEQ_NO);
        this.tsRecent = requireNonNegative(tsRecent);
        this.lastAckSent = requireNonNegative(lastAckSent);
        this.sndTsOk = sndTsOk;
        this.rttVar = requireNonNegative(rttVar);
        this.sRtt = requireNonNegative(sRtt);
        this.rto = requirePositive(rto);
    }

    @SuppressWarnings("java:S107")
    TransmissionControlBlock(final ReliableConnectionConfig config,
                             final long sndUna,
                             final long sndNxt,
                             final int sndWnd,
                             final long iss,
                             final long rcvNxt,
                             final long irs,
                             final SendBuffer sendBuffer,
                             final RetransmissionQueue retransmissionQueue,
                             final ReceiveBuffer receiveBuffer,
                             final long tsRecent,
                             final long lastAckSent,
                             final boolean sndTsOk) {
        this(config, sndUna, sndNxt, sndWnd, iss, rcvNxt, config.rmem(), config.rmem(), irs, sendBuffer, new OutgoingSegmentQueue(), retransmissionQueue, receiveBuffer, config.mmsS() * 3L, config.rmem(), sndWnd, iss, tsRecent, lastAckSent, sndTsOk, 0, 0, config.rto().toMillis());
    }

    TransmissionControlBlock(final ReliableConnectionConfig config,
                             final Channel channel,
                             final long sndUna,
                             final long sndNxt,
                             final int sndWnd,
                             final long iss,
                             final long irs) {
        this(config, sndUna, sndNxt, sndWnd, iss, irs, irs, new SendBuffer(channel), new RetransmissionQueue(), new ReceiveBuffer(channel), 0, 0, false);
    }

    TransmissionControlBlock(final ReliableConnectionConfig config,
                             final Channel channel,
                             final long sndUna,
                             final long sndNxt,
                             final long iss,
                             final long irs) {
        this(config, channel, sndUna, sndNxt, config.rmem(), iss, irs);
    }

    TransmissionControlBlock(final ReliableConnectionConfig config,
                             final Channel channel,
                             final long irs) {
        this(config, 0, 0, config.rmem(), 0, irs, irs, new SendBuffer(channel), new RetransmissionQueue(), new ReceiveBuffer(channel), 0, 0, false);
    }

    // RFC 1122, Section 4.2.2.6
    // https://www.rfc-editor.org/rfc/rfc1122#section-4.2.2.6
    // Eff.snd.MSS = min(SendMSS+20, MMS_S) - TCPhdrsize - IPoptionsize
    static long effSndMss(final long sendMss, final long mmsS) {
        return min(sendMss + SEG_HDR_SIZE, mmsS) - SEG_HDR_SIZE;
    }

    public long sndUna() {
        return sndUna;
    }

    public long sndNxt() {
        return sndNxt;
    }

    public long sndWnd() {
        return sndWnd;
    }

    public long iss() {
        return iss;
    }

    public long rcvNxt() {
        return rcvNxt;
    }

    public long rcvWnd() {
        return rcvWnd;
    }

    public long irs() {
        return irs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TransmissionControlBlock that = (TransmissionControlBlock) o;
        return sndUna == that.sndUna && sndNxt == that.sndNxt && sndWnd == that.sndWnd && iss == that.iss && rcvNxt == that.rcvNxt && rcvWnd == that.rcvWnd && irs == that.irs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sndUna, sndNxt, sndWnd, iss, rcvNxt, rcvWnd, irs);
    }

    @Override
    public String toString() {
        return "TransmissionControlBlock{" +
                "SND.UNA=" + sndUna +
                ", SND.NXT=" + sndNxt +
                ", SND.WND=" + sndWnd +
                ", ISS=" + iss +
                ", RCV.NXT=" + rcvNxt +
                ", RCV.WND=" + rcvWnd +
                ", IRS=" + irs +
                ", " + sendBuffer +
                ", OG.SEG.Q=" + outgoingSegmentQueue +
                ", " + retransmissionQueue +
                ", " + receiveBuffer +
                ", SendMSS=" + sendMss +
                ", CWND=" + cwnd +
                ", SSTHRESH=" + ssthresh +
                '}';
    }

    public void delete() {
        cancelOverrideTimer();
        sendBuffer.release();
        receiveBuffer.release();
    }

    public ReceiveBuffer receiveBuffer() {
        return receiveBuffer;
    }

    /**
     * Advances SND.NXT and places the {@code seg} on the outgoing segment queue.
     */
    void send(final ChannelHandlerContext ctx, final Segment seg, final ChannelPromise promise) {
        if (sndNxt == seg.seq() && seg.len() > 0) {
            sndNxt = add(seg.lastSeq(), 1);
        }
        outgoingSegmentQueue.place(ctx, seg, promise);
    }

    void send(final ChannelHandlerContext ctx, final Segment seg) {
        send(ctx, seg, ctx.newPromise());
    }

    /**
     * Writes outgoing segment queue to network.
     */
    void flush(final ChannelHandlerContext ctx) {
        outgoingSegmentQueue.flush(ctx, this);
    }

    /**
     * Advances SND.NXT, places the {@code seg} on the outgoing segment queue, and writes queue to
     * network.
     */
    void sendAndFlush(final ChannelHandlerContext ctx,
                      final Segment seg,
                      final ChannelPromise promise) {
        send(ctx, seg, promise);
        flush(ctx);
    }

    void sendAndFlush(final ChannelHandlerContext ctx,
                      final Segment seg) {
        sendAndFlush(ctx, seg, ctx.newPromise());
    }

    /**
     * Enqueues data for transmission once connection has been ESTABLISHED.
     */
    void enqueueData(final ChannelHandlerContext ctx,
                     final ByteBuf data,
                     final ChannelPromise promise) {
        sendBuffer.enqueue(data, promise);
    }

    /**
     * Writes data to the network that has been queued for transmission.
     */
    void writeEnqueuedData(final ChannelHandlerContext ctx) {
        segmentizeData(ctx, false);
    }

    private void segmentizeData(final ChannelHandlerContext ctx,
                                final boolean overrideTimeoutOccurred) {
        try {
            long readableBytes = sendBuffer.length();

            while (readableBytes > 0) {
                if (!config().noDelay()) {
                    // RFC 9293: A TCP implementation MUST include a SWS avoidance algorithm in the
                    // RFC 9293: sender (MUST-38).
                    // (Nagle algorithm)

                    // RFC 9293: The sender's SWS avoidance algorithm is more difficult than the
                    // RFC 9293: receiver's because the sender does not know (directly) the
                    // RFC 9293: receiver's total buffer space (RCV.BUFF). An approach that has been
                    // RFC 9293: found to work well is for the sender to calculate Max(SND.WND),
                    // RFC 9293: which is the maximum send window it has seen so far on the
                    // RFC 9293: connection, and to use this value as an estimate of RCV.BUFF.
                    // RFC 9293: Unfortunately, this can only be an estimate; the receiver may at
                    // RFC 9293: any time reduce the size of RCV.BUFF. To avoid a resulting
                    // RFC 9293: deadlock, it is necessary to have a timeout to force transmission
                    // RFC 9293: of data, overriding the SWS avoidance algorithm. In practice, this
                    // RFC 9293: timeout should seldom occur.

                    // RFC 9293: The "usable window" is:
                    // RFC 9293: U = SND.UNA + SND.WND - SND.NXT
                    // RFC 9293: i.e., the offered window less the amount of data sent but not
                    // RFC 9293: acknowledged.
                    final long u = sub(add(sndUna, min(sndWnd(), cwnd())), sndNxt);
                    // RFC 9293: If D is the amount of data queued in the sending TCP endpoint but
                    // RFC 9293: not yet sent,
                    final long d = readableBytes;
                    // RFC 9293: then the following set of rules is recommended.

                    // RFC 9293: Send data...
                    final boolean sendData;
                    if (min(d, u) >= effSndMss()) {
                        // RFC 9293: (1) if a maximum-sized segment can be sent, i.e., if:
                        // RFC 9293:     min(D,U) >= Eff.snd.MSS;
                        sendData = true;
                    }
                    else if (sndNxt == sndUna && d <= u) {
                        // RFC 9293: (2) or if the data is pushed and all queued data can be sent
                        // RFC 9293:     now, i.e., if:
                        // RFC 9293:     [SND.NXT = SND.UNA and] PUSHed and D <= U
                        // RFC 9293:     (the bracketed condition is imposed by the Nagle algorithm);
                        sendData = true;
                    }
                    else if (sndNxt == sndUna && min(d, u) >= config.fs() * maxSndWnd()) {
                        // RFC 9293: (3) or if at least a fraction Fs of the maximum window can be
                        // RFC 9293:     sent, i.e., if:
                        // RFC 9293:     [SND.NXT = SND.UNA and]
                        // RFC 9293:         min(D,U) >= Fs * Max(SND.WND);
                        sendData = true;
                    }
                    else if (overrideTimeoutOccurred) {
                        // (4) or if the override timeout occurs.
                        sendData = true;
                    }
                    else {
                        createOverrideTimer(ctx);
                        sendData = false;
                    }

                    if (sendData) {
                        cancelOverrideTimer();
                    }
                    else {
                        LOG.trace("{} Sender's SWS avoidance: No send condition met. Delay {} bytes.", ctx.channel(), readableBytes);
                        return;
                    }
                }

                final long usableMss = sendMss() - SEG_HDR_SIZE;
                final long window = min(sndWnd(), cwnd());
                final long usableWindow = max(0, window - flightSize());
                final long remainingBytes;
                if (readableBytes <= usableMss && readableBytes <= usableWindow) {
                    // we have less than a segment to send
                    remainingBytes = readableBytes;
                }
                else if (usableMss <= readableBytes && usableMss <= usableWindow) {
                    // we have at least one full segment to send
                    remainingBytes = usableMss;
                }
                else {
                    // we're path or receiver capped
                    remainingBytes = usableWindow;

                    if (sndWnd() > cwnd()) {
                        // path capped
                        LOG.trace("{} Path capped.", ctx.channel());
                    }
                    else {
                        // receiver capped
                        LOG.trace("{} Receiver capped.", ctx.channel());
                    }
                }

                if (remainingBytes > 0) {
                    LOG.trace("{} {} bytes in-flight. SND.WND/CWND of {} bytes allows us to write {} new bytes to network. {} bytes wait to be written. Write {} bytes.", ctx.channel(), flightSize(), min(sndWnd(), cwnd()), usableWindow, readableBytes, remainingBytes);
                    final ConnectionHandler handler = (ConnectionHandler) ctx.handler();

                    readableBytes -= handler.segmentizeAndSendData(ctx, (int) remainingBytes);
                }
                else {
                    return;
                }
            }
        }
        finally {
            outgoingSegmentQueue.flush(ctx, this);
        }
    }

    void pushAndSegmentizeData(final ChannelHandlerContext ctx) {
        sendBuffer.push();
        segmentizeData(ctx, false);
    }

    ReliableConnectionConfig config() {
        return config;
    }

    private void createOverrideTimer(final ChannelHandlerContext ctx) {
        if (overrideTimer == null) {
            overrideTimer = ctx.executor().schedule(() -> {
                overrideTimer = null;
                LOG.trace("{} Sender's SWS avoidance: Override timeout occurred after {}ms.", ctx.channel(), config.overrideTimeout().toMillis());
                segmentizeData(ctx, true);
            }, config.overrideTimeout().toMillis(), MILLISECONDS);
        }
    }

    private void cancelOverrideTimer() {
        if (overrideTimer != null) {
            overrideTimer.cancel(false);
            overrideTimer = null;
        }
    }

    long flightSize() {
        return sub(sndNxt, sndUna);
    }

    void sendMss(final int sendMss) {
        this.sendMss = sendMss;
    }

    public SendBuffer sendBuffer() {
        return sendBuffer;
    }

    public long sndWl1() {
        return sndWl1;
    }

    public long sndWl2() {
        return sndWl2;
    }

    boolean doSlowStart() {
        return cwnd < ssthresh;
    }

    public long cwnd() {
        return cwnd;
    }

    public OutgoingSegmentQueue outgoingSegmentQueue() {
        return outgoingSegmentQueue;
    }

    public RetransmissionQueue retransmissionQueue() {
        return retransmissionQueue;
    }

    public long ssthresh() {
        return ssthresh;
    }

    public void advanceRcvNxt(final ChannelHandlerContext ctx, final int advancement) {
        rcvNxt = advanceSeq(rcvNxt, advancement);
        LOG.trace("{} Advance RCV.NXT to {}.", ctx.channel(), rcvNxt);
    }

    public void decrementRcvWnd(final long decrement) {
        rcvWnd -= decrement;
        assert rcvWnd >= 0 : "RCV.WND must be non-negative";
    }

    public void incrementRcvWnd(final ChannelHandlerContext ctx) {
        // receiver's SWS avoidance algorithms
        // RFC 9293, Section 3.8.6.2.2
        // https://www.rfc-editor.org/rfc/rfc9293.html#section-3.8.6.2.2

        // total receive buffer space is RCV.BUFF
        // RCV.USER octets of this total may be tied up with data that has been received and acknowledged but that the user process has not yet consumed
        final int rcvUser = receiveBuffer.readableBytes();
        final double fr = 0.5; // Fr is a fraction whose recommended value is 1/2

        if (rcvBuff() - rcvUser - rcvWnd >= min(fr * rcvBuff(), effSndMss())) {
            final int newRcvWind = rcvBuff() - rcvUser;
            LOG.trace("{} Receiver's SWS avoidance: Advance RCV.WND from {} to {} (+{}).", ctx.channel(), rcvWnd, newRcvWind, newRcvWind - rcvWnd);
            rcvWnd = newRcvWind;
            assert rcvWnd >= 0 : "RCV.WND must be non-negative";
        }
    }

    int rcvBuff() {
        return rcvBuff;
    }

    long rcvUser() {
        return receiveBuffer.readableBytes();
    }

    public long maxSndWnd() {
        return maxSndWnd;
    }

    /**
     * The maximum size of a segment that TCP really sends, the "effective send MSS,"
     */
    public long effSndMss() {
        return effSndMss(sendMss, config.mmsS());
    }

    /**
     * RFC 5681: The SMSS is the size of the largest segment that the sender can transmit. This
     * RFC 5681: value can be based on the maximum transmission unit of the network, the path MTU
     * RFC 5681: discovery [RFC1191, RFC4821] algorithm, RMSS (see next item), or other factors. The
     * RFC 5681: size does not include the TCP/IP headers and options.
     */
    public long smss() {
        return sendMss;
    }

    public void selectIss() {
        iss = config().issSupplier().getAsLong();
    }

    public void initSndUnaSndNxt() {
        sndUna = iss();
        sndNxt = add(iss(), 1);
    }

    public void rto(final long rto) {
        assert rto >= 0;
        if (rto < config.lBound().toMillis()) {
            // RFC 6298: (2.4) Whenever RTO is computed, if it is less than 1 second, then the RTO
            // RFC 6298:       SHOULD be rounded up to 1 second.
            this.rto = config.lBound().toMillis();

            // RFC 6298:       Traditionally, TCP implementations use coarse grain clocks to measure
            // RFC 6298:       the RTT and trigger the RTO, which imposes a large minimum value on
            // RFC 6298:       the RTO. Research suggests that a large minimum RTO is needed to keep
            // RFC 6298:       TCP conservative and avoid spurious retransmissions [AP99].
            // RFC 6298:       Therefore, this specification requires a large minimum RTO as a
            // RFC 6298:       conservative approach, while at the same time acknowledging that at
            // RFC 6298:       some future point, research may show that a smaller minimum RTO is
            // RFC 6298:       acceptable or superior.
        }
        else if (rto > config.uBound().toMillis()) {
            // RFC 6298: (2.5) A maximum value MAY be placed on RTO provided it is at least 60
            // RFC 6298:       seconds.
            this.rto = config.uBound().toMillis();
        }
        else {
            this.rto = rto;
        }
    }

    public long tsRecent() {
        return tsRecent;
    }

    public void sndUna(final ChannelHandlerContext ctx, final long sndUna) {
        this.sndUna = sndUna;
        LOG.trace("{} Advance SND.UNA to {}.", ctx.channel(), sndUna);
    }

    public void tsRecent(final long tsRecent) {
        this.tsRecent = tsRecent;
    }

    public void turnOnSndTsOk() {
        sndTsOk = true;
    }

    public void sRtt(final double sRtt) {
        this.sRtt = sRtt;
    }

    public void rttVar(final double rttVar) {
        this.rttVar = rttVar;
    }

    public void lastAckSent(final long lastAckSent) {
        this.lastAckSent = lastAckSent;
    }

    public double sRtt() {
        return sRtt;
    }

    public double rttVar() {
        return rttVar;
    }

    public void rcvNxt(final long rcvNxt) {
        this.rcvNxt = rcvNxt;
    }

    public void irs(final long irs) {
        this.irs = irs;
    }

    public boolean sndTsOk() {
        return sndTsOk;
    }

    public long lastAckSent() {
        return lastAckSent;
    }

    public long rto() {
        return rto;
    }

    public void ssthresh(final long ssthresh) {
        this.ssthresh = ssthresh;
    }

    public void cwnd(final long cwnd) {
        this.cwnd = cwnd;
    }

    public long sendMss() {
        return sendMss;
    }

    public void sndWnd(final long sndWnd) {
        this.sndWnd = sndWnd;
        maxSndWnd = max(maxSndWnd, sndWnd);
    }

    public void sndWl1(final long sndWl1) {
        this.sndWl1 = sndWl1;
    }

    public void sndWl2(final long sndWl2) {
        this.sndWl2 = sndWl2;
    }

    public void recover(final long recover) {
        this.recover = recover;
    }

    public int duplicateAcks() {
        return duplicateAcks;
    }

    public long recover() {
        return recover;
    }

    public void lastAdvertisedWindow(long lastAdvertisedWindow) {
        this.lastAdvertisedWindow = lastAdvertisedWindow;
    }

    public void duplicateAcks(final int duplicateAcks) {
        this.duplicateAcks = duplicateAcks;
    }

    public long lastAdvertisedWindow() {
        return lastAdvertisedWindow;
    }
}
