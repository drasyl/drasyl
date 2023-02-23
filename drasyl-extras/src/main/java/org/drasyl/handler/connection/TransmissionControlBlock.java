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
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.connection.Segment.ACK;
import static org.drasyl.handler.connection.Segment.MAX_SEQ_NO;
import static org.drasyl.handler.connection.Segment.MIN_SEQ_NO;
import static org.drasyl.handler.connection.Segment.PSH;
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
public class TransmissionControlBlock {
    private static final Logger LOG = LoggerFactory.getLogger(TransmissionControlBlock.class);
    static final int DRASYL_HDR_SIZE = 197; // FIXME: ergibt 1432: wie setzt sich der overhead zusammen?
    private final SendBuffer sendBuffer;
    final RetransmissionQueue retransmissionQueue;
    private final OutgoingSegmentQueue outgoingSegmentQueue;
    private final ReceiveBuffer receiveBuffer;
    private final int rcvBuff;
    public ReliableTransportConfig config;
    protected long ssthresh; // slow start threshold
    // congestion control
    long cwnd; // congestion window
    long lastAdvertisedWindow;
    // Send Sequence Variables
    // RFC 9293: SND.UNA = oldest unacknowledged sequence number
    private long sndUna;
    // RFC 9293: SND.NXT = next sequence number to be sent
    private long sndNxt;
    // RFC 7323: TS.Recent = holds a timestamp to be echoed in TSecr whenever a segment is sent
    private long tsRecent;
    // RFC 7323: Last.ACK.sent = holds the ACK field from the last segment sent
    private long lastAckSent;
    // RFC 7323: Snd.TS.OK = remember successfull TSopt negotiation
    private boolean sndTsOk;
    // RFC 6298: RTTVAR = round-trip time variation
    private double rttVar;
    // RFC 6298: SRTT = smoothed round-trip time
    private double sRtt;
    // RFC 6298: RTO = retransmission timeout
    //  Until a round-trip time (RTT) measurement has been made for a segment sent between the sender and receiver, the sender SHOULD set RTO <- 1 second
    private long rto;
    // RFC 9293: initial send sequence number
    private long iss;
    // Receive Sequence Variables
    // RFC 9293: RCV.NXT = next sequence number expected on an incoming segment, and is the left or
    // RFC 9293: lower edge of the receive window
    private long rcvNxt;
    // RFC 9293: receive window
    private int rcvWnd;
    // RFC 9293: SND.WND = send window
    private long sndWnd;
    // RFC 9293: SND.WL1 = segment sequence number used for last window update
    private long sndWl1;
    // RFC 9293: SND.WL2 = segment acknowledgment number used for last window update
    private long sndWl2;
    // RFC 9293: IRS = initial receive sequence number
    private long irs;
    // RFC 9293: SendMSS is the MSS value received from the remote host, or the default 536 for IPv4 or 1220 for IPv6, if no MSS Option is received.
    private int sendMss = 1432 - DRASYL_HDR_SIZE;
    // sender's silly window syndrome avoidance algorithm (Nagle algorithm)
    // RFC 5961: MAX.SND.WND = A new state variable MAX.SND.WND is defined as the largest window that the
    // local sender has ever received from its peer.
    private long maxSndWnd;
    int duplicateAcks;
    private ScheduledFuture<?> overrideTimer;
    long recover;

    @SuppressWarnings("java:S107")
    TransmissionControlBlock(final ReliableTransportConfig config,
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
    TransmissionControlBlock(final ReliableTransportConfig config,
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

    TransmissionControlBlock(final ReliableTransportConfig config,
                             final Channel channel,
                             final long sndUna,
                             final long sndNxt,
                             final int sndWnd,
                             final long iss,
                             final long irs) {
        this(config, sndUna, sndNxt, sndWnd, iss, irs, irs, new SendBuffer(channel), new RetransmissionQueue(), new ReceiveBuffer(channel), 0, 0, false);
    }

    TransmissionControlBlock(final ReliableTransportConfig config,
                             final Channel channel,
                             final long sndUna,
                             final long sndNxt,
                             final long iss,
                             final long irs) {
        this(config, channel, sndUna, sndNxt, config.rmem(), iss, irs);
    }

    TransmissionControlBlock(final ReliableTransportConfig config,
                             final Channel channel,
                             final long irs) {
        this(config, 0, 0, config.rmem(), 0, irs, irs, new SendBuffer(channel), new RetransmissionQueue(), new ReceiveBuffer(channel), 0, 0, false);
    }

    // RFC 1122, Section 4.2.2.6
    // https://www.rfc-editor.org/rfc/rfc1122#section-4.2.2.6
    // Eff.snd.MSS = min(SendMSS+20, MMS_S) - TCPhdrsize - IPoptionsize
    static int effSndMss(final int sendMss, final int mmsS) {
        return min(sendMss + DRASYL_HDR_SIZE, mmsS) - Segment.SEG_HDR_SIZE;
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

    public int rcvWnd() {
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
        TransmissionControlBlock that = (TransmissionControlBlock) o;
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
    void send(final ChannelHandlerContext ctx, final Segment seg) {
        if (sndNxt == seg.seq() && seg.len() > 0) {
            sndNxt = add(seg.lastSeq(), 1);
        }
        outgoingSegmentQueue.place(ctx, seg);
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
                      final Segment seg) {
        send(ctx, seg);
        flush(ctx);
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
     * Writes data to the network thas has been queued for transmission.
     */
    void writeEnqueuedData(final ChannelHandlerContext ctx) {
        segmentizeData(ctx, false);
    }

    private void segmentizeData(final ChannelHandlerContext ctx,
                                final boolean overrideTimeoutOccurred) {
        try {
            long readableBytes = sendBuffer.readableBytes();

            while (readableBytes > 0) {
                if (!config().noDelay()) {
                    // apply Nagle algorithm, which aims to coalesce short segments (sender's SWS avoidance algorithm)
                    // https://www.rfc-editor.org/rfc/rfc9293.html#section-3.8.6.2.1
                    // The "usable window" is: U = SND.UNA + SND.WND - SND.NXT
                    final long u = sub(add(sndUna, min(sndWnd(), cwnd())), sndNxt);
                    // D is the amount of data queued in the sending TCP endpoint but not yet sent
                    final long d = readableBytes;

                    // Send data...
                    final double fs = 0.5; // Nagle algorithm: Fs is a fraction whose recommended value is 1/2
                    final boolean sendData;
                    if (min(d, u) >= (long) effSndMss()) {
                        // ..if a maximum-sized segment can be sent, i.e., if:
                        // min(D,U) >= Eff.snd.MSS;
                        sendData = true;
                    }
                    else if (sndNxt == sndUna && d <= u) {
                        // or if the data is pushed and all queued data can be sent now, i.e., if:
                        // SND.NXT = SND.UNA and D <= U
                        sendData = true;
                    }
                    else if (sndNxt == sndUna && min(d, u) >= fs * maxSndWnd()) {
                        // or if at least a fraction Fs of the maximum window can be sent, i.e., if:
                        // SND.NXT = SND.UNA and min(D,U) >= Fs * Max(SND.WND);
                        sendData = true;
                    }
                    else if (overrideTimeoutOccurred) {
                        // or if the override timeout occurs
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
                        LOG.trace("{}[{}] Sender's SWS avoidance: No send condition met. Delay {} bytes.", ctx.channel(), ((ReliableTransportHandler) ctx.handler()).state, readableBytes);
                        return;
                    }
                }

                // at least one byte is required for Zero-Window Probing
                final long usableWindow;
//                if (sndWnd() == 0 && flightSize() == 0) {
//                    // zero window probing
//                    usableWindow = 1;
//                }
//                else {
                final long window = min(sndWnd(), cwnd());
                usableWindow = max(0, window - flightSize());
//                }

                final long remainingBytes = min(effSndMss(), usableWindow, sendBuffer.readableBytes(), readableBytes);

                if (remainingBytes > 0) {
                    LOG.trace("{}[{}] {} bytes in-flight. SND.WND/CWND of {} bytes allows us to write {} new bytes to network. {} bytes wait to be written. Write {} bytes.", ctx.channel(), ((ReliableTransportHandler) ctx.handler()).state, flightSize(), min(sndWnd(), cwnd()), usableWindow, readableBytes, remainingBytes);
                    final ReliableTransportHandler handler = (ReliableTransportHandler) ctx.handler();

                    final AtomicBoolean doPush = new AtomicBoolean();
                    final ByteBuf data = sendBuffer.read((int) remainingBytes, doPush);
                    byte ack = ACK;
                    if (doPush.get()) {
                        ack |= PSH;
                    }
                    final Segment segment = handler.formSegment(ctx, sndNxt, rcvNxt, ack, data);
                    send(ctx, segment);

                    readableBytes -= remainingBytes;
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

    ReliableTransportConfig config() {
        return config;
    }

    private void createOverrideTimer(final ChannelHandlerContext ctx) {
        if (overrideTimer == null) {
            overrideTimer = ctx.executor().schedule(() -> {
                overrideTimer = null;
                LOG.trace("{}[{}] Sender's SWS avoidance: Override timeout occurred after {}ms.", ctx.channel(), ((ReliableTransportHandler) ctx.handler()).state, config.overrideTimeout().toMillis());
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

    void bla_sendMss(final int sendMss) {
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
        LOG.trace("{}[{}] Advance RCV.NXT to {}.", ctx.channel(), ((ReliableTransportHandler) ctx.handler()).state, rcvNxt);
    }

    public void decrementRcvWnd(final int decrement) {
        rcvWnd -= decrement;
        assert rcvWnd >= 0 : "RCV.WND must be non-negative";
    }

    public void incrementRcvWnd(final ChannelHandlerContext ctx) {
        // receiver's SWS avoidance algorithms
        // RFC 9293, Section 3.8.6.2.2
        // https://www.rfc-editor.org/rfc/rfc9293.html#section-3.8.6.2.2

        // total receive buffer space is RCV.BUFF
        // RCV.USER octets of this total may be tied up with data that has been received and acknowledged but that the user process has not yet consumed
        int rcvUser = receiveBuffer.readableBytes();
        final double fr = 0.5; // Fr is a fraction whose recommended value is 1/2

        if (rcvBuff() - rcvUser - rcvWnd >= min(fr * rcvBuff(), effSndMss())) {
            final int newRcvWind = rcvBuff() - rcvUser;
            LOG.trace("{}[{}] Receiver's SWS avoidance: Advance RCV.WND from {} to {} (+{}).", ctx.channel(), ((ReliableTransportHandler) ctx.handler()).state, rcvWnd, newRcvWind, newRcvWind - rcvWnd);
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

    public int effSndMss() {
        return effSndMss(sendMss, config.mmsS());
    }

    public int smss() {
        return effSndMss();
    }

    public void selectIss() {
        iss = config().issSupplier().getAsLong();
    }

    public void initSndUnaSndNxt() {
        sndUna = iss();
        sndNxt = add(iss(), 1);
    }

    public void rto(final long rto) {
        assert rto > 0;
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
        LOG.trace("{}[{}] Advance SND.UNA to {}.", ctx.channel(), ((ReliableTransportHandler) ctx.handler()).state, sndUna);
    }

    public void bla_tsRecent(final long tsRecent) {
        this.tsRecent = tsRecent;
    }

    public void turnOnSndTsOk() {
        sndTsOk = true;
    }

    public void bla_sRtt(final int sRtt) {
        this.sRtt = sRtt;
    }

    public void bla_rttVar(final double rttVar) {
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

    public void bla_rcvNxt(final long rcvNxt) {
        this.rcvNxt = rcvNxt;
    }

    public void bla_irs(final long irs) {
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

    public void bla_ssthresh(final long ssthresh) {
        this.ssthresh = ssthresh;
    }

    public void bla_cwnd(final long cwnd) {
        this.cwnd = cwnd;
    }

    public int sendMss() {
        return sendMss;
    }

    public void bla_sndWnd(final long sndWnd) {
        this.sndWnd = sndWnd;
        maxSndWnd = max(maxSndWnd, sndWnd);
    }

    public void bla_sndWl1(final long sndWl1) {
        this.sndWl1 = sndWl1;
    }

    public void bla_sndWl2(final long sndWl2) {
        this.sndWl2 = sndWl2;
    }

    public void bla_recover(final long recover) {
        this.recover = recover;
    }

    public int duplicateAcks() {
        return duplicateAcks;
    }
}
