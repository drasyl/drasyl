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
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.TransmissionControlBlock.MAX_PORT;
import static org.drasyl.handler.connection.TransmissionControlBlock.MIN_PORT;
import static org.drasyl.util.Preconditions.requireInRange;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.RandomUtil.randomLong;

/**
 * Message used by {@link ConnectionHandler} to provide reliable and ordered delivery of bytes
 * between hosts.
 */
@SuppressWarnings({ "java:S1845", "java:S3052" })
public class Segment extends DefaultByteBufHolder {
    public static final long MIN_SEQ_NO = 0L;
    public static final long MAX_SEQ_NO = (1L << 32) - 1L;
    public static final long HALF_MAX_SEQ_NO = 1L << 31;
    // Source Port: 2 bytes
    // Destination Port: 2 bytes
    // SEQ: 4 bytes
    // ACK: 4 bytes
    // Checksum: 2 bytes
    // CTL: 1 byte
    // Window: 4 bytes
    // Options: 1 bytes
    //   END_OF_OPTION_LIST: 1 byte
    // data: arbitrary number of bytes
    public static final int SEG_HDR_SIZE = 20;
    static final int SEQ_NO_SPACE = 32;
    static final byte ACK = 1 << 4;
    static final byte PSH = 1 << 3;
    static final byte RST = 1 << 2;
    static final byte SYN = 1 << 1;
    @SuppressWarnings("PointlessBitwiseExpression")
    static final byte FIN = 1 << 0;
    private final int srcPort;
    private final int dstPort;
    private final long seq;
    private final long ack;
    private final byte ctl;
    private final long wnd;
    private final int cks;
    private final Map<SegmentOption, Object> options;

    @SuppressWarnings("java:S107")
    Segment(final int srcPort,
            final int dstPort,
            final long seq,
            final long ack,
            final byte ctl,
            final long wnd,
            final int cks,
            final Map<SegmentOption, Object> options,
            final ByteBuf data) {
        super(data);
        this.srcPort = requireInRange(srcPort, MIN_PORT, MAX_PORT);
        this.dstPort = requireInRange(dstPort, MIN_PORT, MAX_PORT);
        this.seq = requireInRange(seq, MIN_SEQ_NO, MAX_SEQ_NO);
        this.ack = requireInRange(ack, MIN_SEQ_NO, MAX_SEQ_NO);
        this.ctl = ctl;
        this.wnd = requireNonNegative(wnd);
        this.cks = cks;
        this.options = requireNonNull(options);
    }

    @SuppressWarnings("java:S107")
    Segment(final int srcPort,
            final int dstPort,
            final long seq,
            final long ack,
            final byte ctl,
            final long wnd,
            final Map<SegmentOption, Object> options,
            final ByteBuf data) {
        this(srcPort, dstPort, seq, ack, ctl, wnd, (short) 0, options, data);
    }

    Segment(final int srcPort,
            final int dstPort,
            final long seq,
            final long ack,
            final byte ctl,
            final Map<SegmentOption, Object> options,
            final ByteBuf data) {
        this(srcPort, dstPort, seq, ack, ctl, 0, options, data);
    }

    Segment(final int srcPort,
            final int dstPort,
            final long seq,
            final long ack,
            final byte ctl,
            final long wnd,
            final ByteBuf data) {
        this(srcPort, dstPort, seq, ack, ctl, wnd, new EnumMap<>(SegmentOption.class), data);
    }

    Segment(final int srcPort,
            final int dstPort,
            final long seq,
            final long ack,
            final byte ctl,
            final ByteBuf data) {
        this(srcPort, dstPort, seq, ack, ctl, 0, data);
    }

    Segment(final int srcPort,
            final int dstPort,
            final long seq,
            final byte ctl) {
        this(srcPort, dstPort, seq, 0, ctl, 0, new EnumMap<>(SegmentOption.class), Unpooled.EMPTY_BUFFER);
    }

    public Segment(final int srcPort,
                   final int dstPort,
                   final long seq,
                   final byte ctl,
                   final long wnd) {
        this(srcPort, dstPort, seq, 0, ctl, wnd, new EnumMap<>(SegmentOption.class), Unpooled.EMPTY_BUFFER);
    }

    public Segment(final int srcPort,
                   final int dstPort,
                   final long seq,
                   final long ack,
                   final byte ctl,
                   final long wnd) {
        this(srcPort, dstPort, seq, ack, ctl, wnd, new EnumMap<>(SegmentOption.class), Unpooled.EMPTY_BUFFER);
    }

    public Segment(final int srcPort,
                   final int dstPort,
                   final long seq,
                   final long ack,
                   final byte ctl) {
        this(srcPort, dstPort, seq, ack, ctl, 0);
    }

    /**
     * Returns the source port of this segment.
     *
     * @return the source port of this segment
     */
    public int srcPort() {
        return srcPort;
    }

    /**
     * Returns the destination port of this segment.
     *
     * @return the destination port of this segment
     */
    public int dstPort() {
        return dstPort;
    }

    /**
     * Returns the number of this segment.
     *
     * @return the sequence number of this segment
     */
    public long seq() {
        return seq;
    }

    /**
     * Returns the acknowledgement number (which is set to the next expected sequence number from
     * the other party).
     *
     * @return the acknowledgement number (which is set to the next expected sequence number from
     * the other party)
     */
    public long ack() {
        return ack;
    }

    /**
     * Returns the control byte which defines which flags (such as SYN, ACK, FIN, RST, and PSH) for
     * this segment are set.
     *
     * @return the control byte which defines which flags (such as SYN, ACK, FIN, RST, and PSH) for
     * this segment are set
     */
    public byte ctl() {
        return ctl;
    }

    /**
     * Returns the window size which indicates the amount of data, in bytes, that the sender of the
     * segment is willing to accept from the other party, effectively controlling the flow of data
     * and preventing buffer overflow.
     *
     * @return the window size which indicates the amount of data, in bytes, that the sender of the
     * segment is willing to accept from the other party, effectively controlling the flow of data
     * and preventing buffer overflow
     */
    public long wnd() {
        return wnd;
    }

    /**
     * Returns the checksum which is used for error-checking of the header and data, helping to
     * ensure that the information has not been altered in transit due to network corruption.
     *
     * @return the checksum which is used for error-checking of the header and data, helping to
     * ensure that the information has not been altered in transit due to network corruption
     */
    public int cks() {
        return cks;
    }

    /**
     * Returns {@code true}, if the ACK flag is set for this segment.
     *
     * @return {@code true}, if the ACK flag is set for this segment
     */
    public boolean isAck() {
        return (ctl & ACK) != 0;
    }

    /**
     * Returns {@code true}, if only ACK flag (and no other flag) is set for this segment.
     *
     * @return {@code true}, if only ACK flag (and no other flag) is set for this segment
     */
    public boolean isOnlyAck() {
        return ctl == ACK;
    }

    /**
     * Returns {@code true}, if the PSH flag is set for this segment.
     *
     * @return {@code true}, if the PSH flag is set for this segment
     */
    public boolean isPsh() {
        return (ctl & PSH) != 0;
    }

    /**
     * Returns {@code true}, if the RST flag is set for this segment.
     *
     * @return {@code true}, if the RST flag is set for this segment
     */
    public boolean isRst() {
        return (ctl & RST) != 0;
    }

    /**
     * Returns {@code true}, if the SYN flag is set for this segment.
     *
     * @return {@code true}, if the SYN flag is set for this segment
     */
    public boolean isSyn() {
        return (ctl & SYN) != 0;
    }

    /**
     * Returns {@code true}, if only SYN flag (and no other flag) is set for this segment.
     *
     * @return {@code true}, if only SYN flag (and no other flag) is set for this segment
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isOnlySyn() {
        return ctl == SYN;
    }

    /**
     * Returns {@code true}, if the FIN flag is set for this segment.
     *
     * @return {@code true}, if the FIN flag is set for this segment
     */
    public boolean isFin() {
        return (ctl & FIN) != 0;
    }

    public Map<SegmentOption, Object> options() {
        return options;
    }

    /**
     * Returns the length (in segments) of this segment.
     *
     * @return the length (in segments) of this segment
     */
    public int len() {
        return content().readableBytes();
    }

    public long nxtSeq() {
        if (isSyn() || isFin()) {
            // the SYN and FIN flags each count as one segment (SYN and FIN are never set at the
            // same time)
            return add(seq(), 1 + len());
        }
        else {
            return add(seq(), len());
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final Segment segment = (Segment) o;
        return srcPort == segment.srcPort && dstPort == segment.dstPort && seq == segment.seq && ack == segment.ack && ctl == segment.ctl;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), srcPort, dstPort, seq, ack, ctl);
    }

    @Override
    public String toString() {
        final List<String> controlBitLabels = new ArrayList<>();
        if (isPsh()) {
            controlBitLabels.add("PSH");
        }
        if (isRst()) {
            controlBitLabels.add("RST");
        }
        if (isFin()) {
            controlBitLabels.add("FIN");
        }
        if (isSyn()) {
            controlBitLabels.add("SYN");
        }
        if (isAck()) {
            controlBitLabels.add("ACK");
        }
        final List<String> optionsLabel = new ArrayList<>();
        for (final Entry<SegmentOption, Object> entry : options.entrySet()) {
            final SegmentOption option = entry.getKey();
            final Object value = entry.getValue();
            optionsLabel.add(option.toString() + "=" + value);
        }

        return "<S=" + srcPort + "/D=" + dstPort + "><SEQ=" + seq + "><ACK=" + ack + "><CTL=" + String.join(",", controlBitLabels) + "><WIN=" + wnd + "><CKS=" + cks + "><LEN=" + len() + "><OPTS=" + String.join(",", optionsLabel) + ">";
    }

    @Override
    public Segment copy() {
        return new Segment(srcPort, dstPort, seq, ack, ctl, wnd, cks, new EnumMap<>(options), content().copy());
    }

    /**
     * Returns the sequence number of the last byte in this segment.
     *
     * @return the sequence number of the last byte in this segment
     */
    public long lastSeq() {
        if (len() == 0) {
            return seq();
        }
        return add(seq(), len() - 1L);
    }

    /**
     * Returns {@code true}, if this segment must be accepted by the receiver.
     *
     * @return {@code true}, if this segment must be accepted by the receiver
     */
    public boolean mustBeAcked() {
        return (!isOnlyAck() && !isRst()) || len() != 0;
    }

    @Override
    public Segment retain() {
        return (Segment) super.retain();
    }

    /**
     * Advances {@code seq} by {@code advancement}. The addition operation is applying the rules
     * specified in
     * <a href="https://www.rfc-editor.org/rfc/rfc1982">RFC 1982: Serial Number Arithmetic</a>.
     *
     * @param seq
     * @param advancement
     * @return advanced sequence number
     */
    public static long advanceSeq(final long seq, final long advancement) {
        return add(seq, advancement);
    }

    /**
     * Returns a random sequence number between [0,4294967295].
     *
     * @return random sequence number between [0,4294967295]
     */
    public static long randomSeq() {
        return randomLong(0, MAX_SEQ_NO);
    }

    /**
     * @param s sequence number we want increment. Must be non-negative.
     * @param n number to add. Must be within range {@code [0, (2^(serialBits - 1) - 1)]}
     * @return resulting sequence number of the addition
     */
    public static long add(final long s, final long n) {
        return (s + n) % (MAX_SEQ_NO + 1);
    }

    public static long sub(final long s, final long n) {
        long result = (s - n) % (MAX_SEQ_NO + 1);
        if (result < 0) {
            result += (MAX_SEQ_NO + 1);
        }
        return result;
    }

    /**
     * @param s1 first non-negative number
     * @param s2 second non-negative number
     * @return {@code true} if {@code s1} is less than {@code s2}. Otherwise {@code false}
     */
    public static boolean lessThan(final long s1, final long s2) {
        return (s1 < s2 && s2 - s1 < HALF_MAX_SEQ_NO) || (s1 > s2 && s1 - s2 > HALF_MAX_SEQ_NO);
    }

    /**
     * @param s1 first non-negative number
     * @param s2 second non-negative number
     * @return {@code true} if {@code s1} is less than or equal to {@code s2}. Otherwise
     * {@code false}
     */
    public static boolean lessThanOrEqualTo(final long s1, final long s2) {
        return s1 == s2 || lessThan(s1, s2);
    }

    /**
     * @param s1 first non-negative number
     * @param s2 second non-negative number
     * @return {@code true} if {@code s1} is greater than {@code s2}. Otherwise {@code false}
     */
    public static boolean greaterThan(final long s1, final long s2) {
        return (s1 < s2 && s2 - s1 > HALF_MAX_SEQ_NO) || (s1 > s2 && s1 - s2 < HALF_MAX_SEQ_NO);
    }

    /**
     * @param s1 first non-negative number
     * @param s2 second non-negative number
     * @return {@code true} if {@code s1} is greater than or equal to {@code s2}. Otherwise
     * {@code false}
     */
    public static boolean greaterThanOrEqualTo(final long s1, final long s2) {
        return s1 == s2 ||greaterThan(s1, s2);
    }
}
