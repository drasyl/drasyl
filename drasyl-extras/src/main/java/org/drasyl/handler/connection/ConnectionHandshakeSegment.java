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
import io.netty.handler.codec.CodecException;
import org.drasyl.util.SerialNumberArithmetic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireInRange;
import static org.drasyl.util.SerialNumberArithmetic.add;

/**
 * A message used by {@link ConnectionHandshakeHandler} to perform a handshake.
 * <p>
 * The synchronization process has been heavily inspired by the three-way handshake of TCP (<a
 * href="https://datatracker.ietf.org/doc/html/rfc793#section-3.4">RFC 793</a>).
 */
@SuppressWarnings({ "java:S1845", "java:S3052" })
public class ConnectionHandshakeSegment extends DefaultByteBufHolder {
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
    private final Map<Option, Object> options;

    public ConnectionHandshakeSegment(final long seq,
                                      final long ack,
                                      final byte ctl,
                                      final Map<Option, Object> options, final ByteBuf data) {
        super(data);
        this.seq = requireInRange(seq, MIN_SEQ_NO, MAX_SEQ_NO);
        this.ack = requireInRange(ack, MIN_SEQ_NO, MAX_SEQ_NO);
        this.ctl = ctl;
        this.options = requireNonNull(options);
    }

    public static ConnectionHandshakeSegment ack(final long seq,
                                                 final long ack,
                                                 final Map<Option, Object> options,
                                                 final ByteBuf data) {
        return new ConnectionHandshakeSegment(seq, ack, ACK, options, data);
    }

    public static ConnectionHandshakeSegment ack(final long seq,
                                                 final long ack,
                                                 final Map<Option, Object> options) {
        return new ConnectionHandshakeSegment(seq, ack, ACK, options, Unpooled.EMPTY_BUFFER);
    }

    public static ConnectionHandshakeSegment ack(final long seq, final long ack) {
        return new ConnectionHandshakeSegment(seq, ack, ACK, new EnumMap<>(Option.class), Unpooled.EMPTY_BUFFER);
    }

    public static ConnectionHandshakeSegment ack(final long seq,
                                                 final long ack,
                                                 final ByteBuf data) {
        return new ConnectionHandshakeSegment(seq, ack, ACK, new EnumMap<>(Option.class), data);
    }

    public static ConnectionHandshakeSegment rst(final long seq) {
        return new ConnectionHandshakeSegment(seq, 0, RST, new EnumMap<>(Option.class), Unpooled.EMPTY_BUFFER);
    }

    public static ConnectionHandshakeSegment syn(final long seq,
                                                 final Map<Option, Object> options) {
        return new ConnectionHandshakeSegment(seq, 0, SYN, new EnumMap<>(options), Unpooled.EMPTY_BUFFER);
    }

    public static ConnectionHandshakeSegment syn(final long seq) {
        return syn(seq, new EnumMap<>(Option.class));
    }

    public static ConnectionHandshakeSegment fin(final long seq) {
        return new ConnectionHandshakeSegment(seq, 0, FIN, new EnumMap<>(Option.class), Unpooled.EMPTY_BUFFER);
    }

    public static ConnectionHandshakeSegment pshAck(final long seq,
                                                    final long ack,
                                                    final ByteBuf data) {
        return new ConnectionHandshakeSegment(seq, ack, (byte) (PSH | ACK), new EnumMap<>(Option.class), data);
    }

    public static ConnectionHandshakeSegment rstAck(final long seq, final long ack) {
        return new ConnectionHandshakeSegment(seq, ack, (byte) (RST | ACK), new EnumMap<>(Option.class), Unpooled.EMPTY_BUFFER);
    }

    public static ConnectionHandshakeSegment synAck(final long seq, final long ack,
                                                    final Map<Option, Object> options) {
        return new ConnectionHandshakeSegment(seq, ack, (byte) (SYN | ACK), new EnumMap<>(options), Unpooled.EMPTY_BUFFER);
    }

    public static ConnectionHandshakeSegment synAck(final long seq, final long ack) {
        return synAck(seq, ack, new EnumMap<>(Option.class));
    }

    public static ConnectionHandshakeSegment finAck(final long seq, final long ack) {
        return new ConnectionHandshakeSegment(seq, ack, (byte) (FIN | ACK), new EnumMap<>(Option.class), Unpooled.EMPTY_BUFFER);
    }

    public static long advanceSeq(final long seq, final long advancement) {
        return SerialNumberArithmetic.add(seq, advancement, SEQ_NO_SPACE);
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

    public Map<Option, Object> options() {
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
        final ConnectionHandshakeSegment segment = (ConnectionHandshakeSegment) o;
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
        for (Option option : options.keySet()) {
            optionsLabel.add(option.toString());
        }

        return "<SEQ=" + seq + "><ACK=" + ack + "><CTL=" + String.join(",", controlBitLabels) + "><LEN=" + len() + "><OPTS=" + String.join(",", optionsLabel) + ">";
    }

    @Override
    public ConnectionHandshakeSegment copy() {
        return new ConnectionHandshakeSegment(seq, ack, ctl, new EnumMap<>(options), content().copy());
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

    public boolean canPiggybackAck(final ConnectionHandshakeSegment other) {
        return (other.isOnlyAck() || other.isOnlyFin()) && seq() == other.seq();
    }

    public ConnectionHandshakeSegment piggybackAck(final ConnectionHandshakeSegment other) {
        if (!canPiggybackAck(other)) {
            return null;
        }

        try {
            if (isAck() && other.isOnlyAck()) {
                // fully replace other ACK
                return this;
            }

            // attach ACK
            return new ConnectionHandshakeSegment(seq, other.ack(), (byte) (ctl | other.ctl()), options, content());
        }
        finally {
            other.release();
        }
    }

    @Override
    public ConnectionHandshakeSegment retain() {
        return (ConnectionHandshakeSegment) super.retain();
    }

    enum Option {
        END_OF_OPTION_LIST((byte) 0),
        MAXIMUM_SEGMENT_SIZE((byte) 2),
        TIMESTAMPS((byte) 8); // TS Value (TSval): 8 bytes; TS Echo Reply (TSecr): 8 bytes
        private static final Map<Byte, Option> OPTIONS;

        static {
            OPTIONS = new HashMap<>();
            for (final Option option : values()) {
                OPTIONS.put(option.kind(), option);
            }
        }

        private final byte kind;

        Option(final byte kind) {
            this.kind = kind;
        }

        public static Option ofKind(final byte kind) {
            return OPTIONS.get(kind);
        }

        public byte kind() {
            return kind;
        }

        public void writeValueTo(final ByteBuf out, final Object value) {
            switch (this) {
                case MAXIMUM_SEGMENT_SIZE:
                    out.writeShort((Integer) value);
                    return;
                case TIMESTAMPS:
                    final long[] timestamps = (long[]) value;
                    out.writeLong(timestamps[0]);
                    out.writeLong(timestamps[0]);
                    return;
                default:
                    throw new CodecException("Unable to write value of option " + this);
            }
        }

        public Object readValueFrom(final ByteBuf in) {
            switch (this) {
                case MAXIMUM_SEGMENT_SIZE:
                    return in.readUnsignedShort();
                case TIMESTAMPS:
                    return new long[]{
                            in.readLong(),
                            in.readLong()
                    };
                default:
                    throw new CodecException("Unable to read value of option " + this);
            }
        }
    }
}
