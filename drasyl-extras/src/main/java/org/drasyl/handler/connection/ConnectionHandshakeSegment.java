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
import java.util.List;
import java.util.Objects;

import static org.drasyl.util.Preconditions.requireInRange;
import static org.drasyl.util.SerialNumberArithmetic.add;
import static org.drasyl.util.SerialNumberArithmetic.lessThan;
import static org.drasyl.util.SerialNumberArithmetic.lessThanOrEqualTo;

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
    private static final byte ACK = 1 << 4;
    private static final byte PSH = 1 << 3;
    private static final byte RST = 1 << 2;
    private static final byte SYN = 1 << 1;
    @SuppressWarnings("PointlessBitwiseExpression")
    private static final byte FIN = 1 << 0;
    private final long seq;
    private final long ack;
    private final byte ctl;
    private long tsVal;
    private long tsEcr;

    public ConnectionHandshakeSegment(final long seq,
                                      final long ack,
                                      final byte ctl,
                                      final long tsVal,
                                      final long tsEcr,
                                      final ByteBuf data) {
        super(data);
        this.seq = requireInRange(seq, MIN_SEQ_NO, MAX_SEQ_NO);
        this.ack = requireInRange(ack, MIN_SEQ_NO, MAX_SEQ_NO);
        this.ctl = ctl;
        this.tsVal = tsVal;
        this.tsEcr = tsEcr;
    }

    public ConnectionHandshakeSegment(final long seq,
                                      final long ack,
                                      final byte ctl,
                                      final ByteBuf data) {
        this(seq, ack, ctl, 0, 0, data);
    }

    public static ConnectionHandshakeSegment ack(final long seq, final long ack) {
        return new ConnectionHandshakeSegment(seq, ack, ACK, Unpooled.EMPTY_BUFFER);
    }

    public static ConnectionHandshakeSegment ack(final long seq,
                                                 final long ack,
                                                 final ByteBuf data) {
        return new ConnectionHandshakeSegment(seq, ack, ACK, data);
    }

    public static ConnectionHandshakeSegment rst(final long seq) {
        return new ConnectionHandshakeSegment(seq, 0, RST, Unpooled.EMPTY_BUFFER);
    }

    public static ConnectionHandshakeSegment syn(final long seq) {
        return new ConnectionHandshakeSegment(seq, 0, SYN, Unpooled.EMPTY_BUFFER);
    }

    public static ConnectionHandshakeSegment fin(final long seq) {
        return new ConnectionHandshakeSegment(seq, 0, FIN, Unpooled.EMPTY_BUFFER);
    }

    public static ConnectionHandshakeSegment pshAck(final long seq,
                                                    final long ack,
                                                    final ByteBuf data) {
        return new ConnectionHandshakeSegment(seq, ack, (byte) (PSH | ACK), data);
    }

    public static ConnectionHandshakeSegment rstAck(final long seq, final long ack) {
        return new ConnectionHandshakeSegment(seq, ack, (byte) (RST | ACK), Unpooled.EMPTY_BUFFER);
    }

    public static ConnectionHandshakeSegment synAck(final long seq, final long ack) {
        return new ConnectionHandshakeSegment(seq, ack, (byte) (SYN | ACK), Unpooled.EMPTY_BUFFER);
    }

    public static ConnectionHandshakeSegment finAck(final long seq, final long ack) {
        return new ConnectionHandshakeSegment(seq, ack, (byte) (FIN | ACK), Unpooled.EMPTY_BUFFER);
    }

    public static long advanceSeq(final long seq, final long advancement) {
        return SerialNumberArithmetic.add(seq, advancement, SEQ_NO_SPACE);
    }

    public static long advanceSeq(final long seq) {
        return advanceSeq(seq, 1);
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

    public long tsVal() {
        return tsVal;
    }

    public void setTsVal(final long tsVal) {
        this.tsVal = tsVal;
    }

    public long tsEcr() {
        return tsEcr;
    }

    public void setTsEcr(long tsEcr) {
        this.tsEcr = tsEcr;
    }

    public int len() {
        return content().readableBytes();
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

        return "<SEQ=" + seq + "><ACK=" + ack + "><CTL=" + String.join(",", controlBitLabels) + "><LEN=" + len() + "><TSval=" + tsVal + ",TSecr=" + tsEcr + ">";
    }

    @Override
    public ConnectionHandshakeSegment copy() {
        return new ConnectionHandshakeSegment(seq, ack, ctl, content().copy());
    }

    public long lastSeq() {
        return add(seq(), len() - 1L, SEQ_NO_SPACE);
    }

    public boolean isAcceptableAck(final long sndUna, final long sndNxt) {
        return isAck() && lessThan(sndUna, ack, SEQ_NO_SPACE) && lessThanOrEqualTo(ack, sndNxt, SEQ_NO_SPACE);
    }

    public boolean canPiggybackAck(final ConnectionHandshakeSegment other) {
        return (isOnlyAck() || isOnlyFin()) && seq() == other.seq();
    }

    public ConnectionHandshakeSegment piggybackAck(ConnectionHandshakeSegment other) {
        if (!canPiggybackAck(other)) {
            return null;
        }

        try {
            if (isAck() && other.isOnlyAck()) {
                // fully replace other ACK
                return this;
            }

            // attach ACK
            return new ConnectionHandshakeSegment(seq, other.ack(), (byte) (ctl | ACK), tsVal, tsEcr, content());
        }
        finally {
            other.release();
        }
    }
}
