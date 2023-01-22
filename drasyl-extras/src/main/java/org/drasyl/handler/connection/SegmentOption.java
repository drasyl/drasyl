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

import java.util.HashMap;
import java.util.Map;

import static org.drasyl.util.Preconditions.requireNonNegative;

enum SegmentOption {
    END_OF_OPTION_LIST((byte) 0),
    MAXIMUM_SEGMENT_SIZE((byte) 2),
    TIMESTAMPS((byte) 8); // TS Value (TSval): 8 bytes; TS Echo Reply (TSecr): 8 bytes
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
            default:
                throw new CodecException("Unable to read value of option " + this);
        }
    }

    static class TimestampsOption {
        // TS Value (TSval): 8 bytes
        final long tsVal;
        // TS Echo Reply (TSecr): 8 bytes
        final long tsEcr;

        TimestampsOption(long tsVal, long tsEcr) {
            this.tsVal = requireNonNegative(tsVal);
            this.tsEcr = requireNonNegative(tsEcr);
        }

        public TimestampsOption(long tsVal) {
            this(tsVal, 0);
        }

        @Override
        public String toString() {
            return "<TSval=" + tsVal + ",TSecr=" + tsEcr + ">";
        }
    }
}
