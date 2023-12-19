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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.drasyl.handler.connection.Segment.SEG_HDR_SIZE;
import static org.drasyl.handler.connection.SegmentOption.END_OF_OPTION_LIST;

/**
 * Encodes {@link ByteBuf}s to {@link Segment}s and vice versa.
 */
public class SegmentCodec extends MessageToMessageCodec<ByteBuf, Segment> {
    private static final Logger LOG = LoggerFactory.getLogger(SegmentCodec.class);
    public static final int CKS_INDEX = 12;
    public static final int MAGIC_NUMBER = 1_232_217_832;
    private final boolean checksumEnabled;

    public SegmentCodec(final boolean checksumEnabled) {
        this.checksumEnabled = checksumEnabled;
    }

    public SegmentCodec() {
        this(true);
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final Segment seg,
                          final List<Object> out) throws Exception {
        final ByteBuf buf = ctx.alloc().buffer(Integer.BYTES + SEG_HDR_SIZE + seg.content().readableBytes());
        buf.writeInt(MAGIC_NUMBER);
        buf.writeInt((int) seg.seq());
        buf.writeInt((int) seg.ack());
        buf.writeShort(0); // checksum placeholder
        buf.writeByte(seg.ctl());
        buf.writeInt((int) seg.wnd());

        // options
        for (final Entry<SegmentOption, Object> entry : seg.options().entrySet()) {
            final SegmentOption option = entry.getKey();
            final Object value = entry.getValue();

            buf.writeByte(option.kind());
            option.writeValueTo(buf, value);
        }
        // end of list option
        buf.writeByte(END_OF_OPTION_LIST.kind());

        // content
        buf.writeBytes(seg.content());

        if (checksumEnabled) {
            // calculate checksum
            final int cks = calculateChecksum(buf, 4);
            buf.setShort(CKS_INDEX, (short) cks);
        }

        out.add(buf);
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in,
                          final List<Object> out) {
        if (in.readableBytes() >= Integer.BYTES + SEG_HDR_SIZE) {
            in.markReaderIndex();
            if (in.readInt() != MAGIC_NUMBER) {
                in.resetReaderIndex();
                ctx.fireChannelRead(in.retain());
                return;
            }

            final int readerIndex = in.readerIndex();

            final long seq = in.readUnsignedInt();
            final long ack = in.readUnsignedInt();
            final int cks = in.readUnsignedShort();
            final byte ctl = in.readByte();
            final long wnd = in.readUnsignedInt();

            // options
            final Map<SegmentOption, Object> options = new EnumMap<>(SegmentOption.class);
            byte kind;
            while ((kind = in.readByte()) != END_OF_OPTION_LIST.kind()) {
                final SegmentOption option = SegmentOption.ofKind(kind);
                final Object value = option.readValueFrom(in);

                options.put(option, value);
            }

            final Segment seg = new Segment(seq, ack, ctl, wnd, cks, options, in);

            if (checksumEnabled) {
                // verify checksum
                if (calculateChecksum(in, readerIndex) != 0) {
                    // wrong checksum, drop segment
                    LOG.debug("{} Drop SEG `{}` because of wrong checksum.", ctx.channel(), seg);
                    return;
                }
            }

            out.add(seg.retain());
        }
        else {
            // wrong length -> pass through message
            out.add(in.retain());
        }
    }

    private int calculateChecksum(final ByteBuf buf, final int index) {
        try {
            buf.markReaderIndex();

            buf.readerIndex(index);
            int sum = 0;
            while (buf.readableBytes() > 1) {
                sum += buf.readUnsignedShort();
            }
            if (buf.readableBytes() > 0) {
                // add padding
                sum += buf.readUnsignedByte();
            }

            return (~((sum & 0xffff) + (sum >> 16))) & 0xffff;
        }
        finally {
            buf.resetReaderIndex();
        }
    }
}
