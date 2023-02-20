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
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import org.drasyl.util.SerialNumberArithmetic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireInRange;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.RandomUtil.randomInt;

/**
 * Message used by {@link ReliableTransportHandler} to provide reliable and ordered delivery of
 * bytes between hosts.
 */
@SuppressWarnings({ "java:S1845", "java:S3052" })
public class Segment extends DefaultByteBufHolder {
    public static final long MIN_SEQ_NO = 0L;
    public static final long MAX_SEQ_NO = 4_294_967_295L;
    // SEQ: 4 bytes
    // ACK: 4 bytes
    // CTL: 1 byte
    // Window: 4 bytes
    // Options: 18 bytes
    //   MAXIMUM_SEGMENT_SIZE: ignored, as only used for SYN
    //   SACK: ignored, as only used for empty(?) ACKs
    //   TIMESTAMPS: 17 bytes
    //   END_OF_OPTION_LIST: 1 byte
    // data: arbitrary number of bytes
    public static final int SEG_HDR_SIZE = 31;
    static final int SEQ_NO_SPACE = 32;
    static final byte ACK = 1 << 4;
    static final byte PSH = 1 << 3;
    static final byte RST = 1 << 2;
    static final byte SYN = 1 << 1;
    @SuppressWarnings("PointlessBitwiseExpression")
    static final byte FIN = 1 << 0;
    private final long seq;
    private final long ack;
    private final byte ctl;
    private final long window;
    private final Map<SegmentOption, Object> options;

    public Segment(final long seq,
                   final long ack,
                   final byte ctl,
                   final long window,
                   final Map<SegmentOption, Object> options,
                   final ByteBuf data) {
        super(data);
        this.seq = requireInRange(seq, MIN_SEQ_NO, MAX_SEQ_NO);
        this.ack = requireInRange(ack, MIN_SEQ_NO, MAX_SEQ_NO);
        this.ctl = ctl;
        this.window = requireNonNegative(window);
        this.options = requireNonNull(options);
    }

    public Segment(final long seq,
                   final byte ctl,
                   final Map<SegmentOption, Object> options) {
        this(seq, 0, ctl, 0, options, Unpooled.EMPTY_BUFFER);
    }

    public Segment(final long seq,
                   final long ack,
                   final byte ctl,
                   final Map<SegmentOption, Object> options,
                   final ByteBuf data) {
        this(seq, ack, ctl, 0, options, data);
    }

    public Segment(final long seq,
                   final long ack,
                   final byte ctl,
                   final Map<SegmentOption, Object> options) {
        this(seq, ack, ctl, 0, options, Unpooled.EMPTY_BUFFER);
    }

    public Segment(final long seq,
                   final long ack,
                   final byte ctl,
                   final long window,
                   final ByteBuf data) {
        this(seq, ack, ctl, window, new EnumMap<>(SegmentOption.class), data);
    }

    public Segment(final long seq,
                   final long ack,
                   final byte ctl,
                   final ByteBuf data) {
        this(seq, ack, ctl, 0, data);
    }

    public Segment(final long seq,
                   final byte ctl) {
        this(seq, 0, ctl, 0, new EnumMap<>(SegmentOption.class), Unpooled.EMPTY_BUFFER);
    }

    public Segment(final long seq,
                   final byte ctl,
                   final long window) {
        this(seq, 0, ctl, window, new EnumMap<>(SegmentOption.class), Unpooled.EMPTY_BUFFER);
    }

    public Segment(final long seq,
                   final long ack,
                   final byte ctl,
                   final long window) {
        this(seq, ack, ctl, window, new EnumMap<>(SegmentOption.class), Unpooled.EMPTY_BUFFER);
    }

    public Segment(final long seq,
                   final long ack,
                   final byte ctl) {
        this(seq, ack, ctl, 0);
    }

    public static long advanceSeq(final long seq, final long advancement) {
        return add(seq, advancement);
    }

    public static long randomSeq() {
        // generate random number between [0,4294967296]
        return (long) randomInt(Integer.MAX_VALUE - 1) + randomInt(Integer.MAX_VALUE - 1) + randomInt(3);
    }

    /**
     * @param s sequence number we want increment. Must be non-negative.
     * @param n number to add. Must be within range {@code [0, (2^(serialBits - 1) - 1)]}
     * @return resulting sequence number of the addition
     */
    public static long add(final long s, final long n) {
        return SerialNumberArithmetic.add(s, n, SEQ_NO_SPACE);
    }

    public static long sub(final long i1, final long i2) {
        return SerialNumberArithmetic.sub(i1, i2, SEQ_NO_SPACE);
    }

    /**
     * @param i1 first non-negative number
     * @param i2 second non-negative number
     * @return {@code true} if {@code i1} is less than {@code i2}. Otherwise {@code false}
     */
    public static boolean lessThan(final long i1, final long i2) {
        return SerialNumberArithmetic.lessThan(i1, i2, SEQ_NO_SPACE);
    }

    /**
     * @param i1 first non-negative number
     * @param i2 second non-negative number
     * @return {@code true} if {@code i1} is less than or equal to {@code i2}. Otherwise
     * {@code false}
     */
    public static boolean lessThanOrEqualTo(final long i1, final long i2) {
        return SerialNumberArithmetic.lessThanOrEqualTo(i1, i2, SEQ_NO_SPACE);
    }

    /**
     * @param i1 first non-negative number
     * @param i2 second non-negative number
     * @return {@code true} if {@code i1} is greater than {@code i2}. Otherwise {@code false}
     */
    public static boolean greaterThan(final long i1, final long i2) {
        return SerialNumberArithmetic.greaterThan(i1, i2, SEQ_NO_SPACE);
    }

    /**
     * @param i1 first non-negative number
     * @param i2 second non-negative number
     * @return {@code true} if {@code i1} is greater than or equal to {@code i2}. Otherwise
     * {@code false}
     */
    public static boolean greaterThanOrEqualTo(final long i1, final long i2) {
        return SerialNumberArithmetic.greaterThanOrEqualTo(i1, i2, SEQ_NO_SPACE);
    }

    public long seq() {
        return seq;
    }

    public long ack() {
        return ack;
    }

    public byte ctl() {
        return ctl;
    }

    public long window() {
        return window;
    }

    public boolean isAck() {
        return (ctl & ACK) != 0;
    }

    public boolean isOnlyAck() {
        return ctl == ACK;
    }

    public boolean isPsh() {
        return (ctl & PSH) != 0;
    }

    public boolean isRst() {
        return (ctl & RST) != 0;
    }

    public boolean isSyn() {
        return (ctl & SYN) != 0;
    }

    public boolean isOnlySyn() {
        return ctl == SYN;
    }

    public boolean isFin() {
        return (ctl & FIN) != 0;
    }

    public boolean isOnlyFin() {
        return ctl == FIN;
    }

    public Map<SegmentOption, Object> options() {
        return options;
    }

    public int len() {
        if (isSyn() || isFin()) {
            return 1 + content().readableBytes();
        }
        else {
            return content().readableBytes();
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
        return seq == segment.seq && ack == segment.ack && ctl == segment.ctl;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), seq, ack, ctl);
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

        return "<SEQ=" + seq + "><ACK=" + ack + "><CTL=" + String.join(",", controlBitLabels) + "><WIN=" + window + "><LEN=" + len() + "><OPTS=" + String.join(",", optionsLabel) + ">";
    }

    @Override
    public Segment copy() {
        return new Segment(seq, ack, ctl, window, new EnumMap<>(options), content().copy());
    }

    public long lastSeq() {
        if (len() == 0) {
            return seq();
        }
        return add(seq(), len() - 1L);
    }

    public boolean mustBeAcked() {
        return (!isOnlyAck() && !isRst()) || len() != 0;
    }

    public boolean canPiggybackAck(final Segment other) {
        return (other.isOnlyAck() || other.isOnlyFin()) && seq() == other.seq();
    }

    public Segment piggybackAck(final Segment other) {
        if (!canPiggybackAck(other)) {
            return null;
        }

        try {
            if (isAck() && other.isOnlyAck()) {
                // fully replace other ACK
                return this;
            }

            // attach ACK
            return new Segment(seq, other.ack(), (byte) (ctl | other.ctl()), window, options, content());
        }
        finally {
            other.release();
        }
    }

    @Override
    public Segment retain() {
        return (Segment) super.retain();
    }
}
