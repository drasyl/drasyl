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
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireInRange;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.RandomUtil.randomInt;
import static org.drasyl.util.SerialNumberArithmetic.add;

/**
 * Message used by {@link ReliableDeliveryHandler} to provide reliable and ordered delivery of
 * bytes between hosts.
 */
@SuppressWarnings({"java:S1845", "java:S3052"})
public class Segment extends DefaultByteBufHolder {
    public static final long MIN_SEQ_NO = 0L;
    public static final long MAX_SEQ_NO = 4_294_967_295L;
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

    public static Segment ack(final long seq,
                              final long ack,
                              final long window,
                              final ByteBuf data) {
        return new Segment(seq, ack, ACK, window, new EnumMap<>(SegmentOption.class), data);
    }

    public static Segment ack(final long seq,
                              final long ack,
                              final Map<SegmentOption, Object> options,
                              final ByteBuf data) {
        return new Segment(seq, ack, ACK, 0, options, data);
    }

    public static Segment ack(final long seq,
                              final long ack,
                              final Map<SegmentOption, Object> options) {
        return new Segment(seq, ack, ACK, 0, options, Unpooled.EMPTY_BUFFER);
    }

    public static Segment ack(final long seq,
                              final long ack,
                              final long window) {
        return new Segment(seq, ack, ACK, window, new EnumMap<>(SegmentOption.class), Unpooled.EMPTY_BUFFER);
    }

    public static Segment ack(final long seq, final long ack) {
        return ack(seq, ack, 0);
    }

    public static Segment ack(final long seq,
                              final long ack,
                              final ByteBuf data) {
        return new Segment(seq, ack, ACK, 0, new EnumMap<>(SegmentOption.class), data);
    }

    public static Segment rst(final long seq) {
        return new Segment(seq, 0, RST, 0, new EnumMap<>(SegmentOption.class), Unpooled.EMPTY_BUFFER);
    }

    public static Segment syn(final long seq,
                              final Map<SegmentOption, Object> options) {
        return new Segment(seq, 0, SYN, 0, options, Unpooled.EMPTY_BUFFER);
    }

    public static Segment syn(final long seq, final long window) {
        return new Segment(seq, 0, SYN, window, new EnumMap<>(SegmentOption.class), Unpooled.EMPTY_BUFFER);
    }

    public static Segment syn(final long seq) {
        return syn(seq, 0);
    }

    public static Segment fin(final long seq) {
        return new Segment(seq, 0, FIN, 0, new EnumMap<>(SegmentOption.class), Unpooled.EMPTY_BUFFER);
    }

    public static Segment pshAck(final long seq,
                                 final long ack,
                                 final long window,
                                 final ByteBuf data) {
        return new Segment(seq, ack, (byte) (PSH | ACK), window, new EnumMap<>(SegmentOption.class), data);
    }

    public static Segment pshAck(final long seq,
                                 final long ack,
                                 final ByteBuf data) {
        return pshAck(seq, ack, 0, data);
    }

    public static Segment rstAck(final long seq, final long ack) {
        return new Segment(seq, ack, (byte) (RST | ACK), 0, new EnumMap<>(SegmentOption.class), Unpooled.EMPTY_BUFFER);
    }

    public static Segment synAck(final long seq,
                                 final long ack,
                                 final long window) {
        return new Segment(seq, ack, (byte) (SYN | ACK), window, new EnumMap<>(SegmentOption.class), Unpooled.EMPTY_BUFFER);
    }

    public static Segment synAck(final long seq,
                                 final long ack,
                                 final Map<SegmentOption, Object> options) {
        return new Segment(seq, ack, (byte) (SYN | ACK), 0, options, Unpooled.EMPTY_BUFFER);
    }

    public static Segment synAck(final long seq, final long ack) {
        return synAck(seq, ack, 0);
    }

    public static Segment finAck(final long seq, final long ack) {
        return new Segment(seq, ack, (byte) (FIN | ACK), 0, new EnumMap<>(SegmentOption.class), Unpooled.EMPTY_BUFFER);
    }

    public static long advanceSeq(final long seq, final long advancement) {
        return SerialNumberArithmetic.add(seq, advancement, SEQ_NO_SPACE);
    }

    public static long randomSeq() {
        // generate random number between [0,4294967296]
        return (long) randomInt(Integer.MAX_VALUE - 1) + randomInt(Integer.MAX_VALUE - 1) + randomInt(3);
    }

    public long seq() {
        return seq;
    }

    public long ack() {
        return ack;
    }

    public int ctl() {
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
        } else {
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
        for (SegmentOption option : options.keySet()) {
            optionsLabel.add(option.toString());
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
        return add(seq(), len() - 1L, SEQ_NO_SPACE);
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
        } finally {
            other.release();
        }
    }

    @Override
    public Segment retain() {
        return (Segment) super.retain();
    }
}
