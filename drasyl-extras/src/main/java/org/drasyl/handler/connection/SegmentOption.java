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
import io.netty.handler.codec.CodecException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireNonNegative;

enum SegmentOption {
    END_OF_OPTION_LIST((byte) 0), // 1 byte
    MAXIMUM_SEGMENT_SIZE((byte) 2), // 3 bytes
    SACK((byte) 5), // at least 2 bytes
    TIMESTAMPS((byte) 8); // 17 bytes // FIXME: sind das bei TCP auch 8 bytes pro timestamp?
    private static final Map<Byte, SegmentOption> OPTIONS;

    static {
        OPTIONS = new HashMap<>();
        for (final SegmentOption option : values()) {
            OPTIONS.put(option.kind(), option);
        }
    }

    private final byte kind;

    SegmentOption(final byte kind) {
        this.kind = kind;
    }

    public static SegmentOption ofKind(final byte kind) {
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
                out.writeLong(((TimestampsOption) value).tsVal);
                out.writeLong(((TimestampsOption) value).tsEcr);
                return;
            case SACK:
                out.writeByte(((SackOption)value).edges.size());
                for (final long edge : ((SackOption)value).edges) {
                   out.writeInt((int) edge);
                }
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
                return new TimestampsOption(in.readLong(), in.readLong());
            case SACK:
                final int size = in.readUnsignedByte();
                final List<Long> edges = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    edges.add(in.readUnsignedInt());
                }
                return new SackOption(edges);
            default:
                throw new CodecException("Unable to read value of option " + this);
        }
    }

    static class TimestampsOption {
        // TS Value (TSval): 8 bytes
        final long tsVal;
        // TS Echo Reply (TSecr): 8 bytes
        final long tsEcr;

        TimestampsOption(final long tsVal, final long tsEcr) {
            this.tsVal = requireNonNegative(tsVal);
            this.tsEcr = requireNonNegative(tsEcr);
        }

        public TimestampsOption(final long tsVal) {
            this(tsVal, 0);
        }

        @Override
        public String toString() {
            return "<TSval=" + tsVal + ",TSecr=" + tsEcr + ">";
        }
    }

    static class SackOption {
        final List<Long> edges;

        public SackOption(final List<Long> edges) {
            this.edges = requireNonNull(edges);
        }

        public SackOption() {
            this(new ArrayList<>());
        }

        @Override
        public String toString() {
            final StringBuilder blocks = new StringBuilder();
            for (int i = 0; i < edges.size(); i += 2) {
                if (blocks.length() != 0) {
                    blocks.append(",");
                }

                blocks.append(edges.get(i) + "-" + edges.get(i + 1));
            }

            return "<SACK=" + blocks + ">";
        }
    }
}
