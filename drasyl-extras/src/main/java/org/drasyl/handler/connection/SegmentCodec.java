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
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.ReferenceCountUtil;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.drasyl.handler.connection.SegmentOption.END_OF_OPTION_LIST;

/**
 * Encodes {@link ByteBuf}s to {@link Segment}s and vice versa.
 */
@Sharable
public class SegmentCodec extends MessageToMessageCodec<ByteBuf, Segment> {
    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final Segment seg,
                          final List<Object> out) throws Exception {
        ReferenceCountUtil.touch(seg, "ConnectionHandshakeCodec encode " + seg.toString());
        final ByteBuf buf = ctx.alloc().buffer(Segment.SEG_HDR_SIZE + seg.content().readableBytes());
        ReferenceCountUtil.touch(buf, "encode");
        buf.writeInt((int) seg.seq());
        buf.writeInt((int) seg.ack());
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

        buf.writeBytes(seg.content());
        out.add(buf);
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in,
                          final List<Object> out) {
        try {
            if (in.readableBytes() >= Segment.SEG_HDR_SIZE) {
                final long seq = in.readUnsignedInt();
                final long ack = in.readUnsignedInt();
                final byte ctl = in.readByte();
                final long window = in.readUnsignedInt();

                // options
                final Map<SegmentOption, Object> options = new EnumMap<>(SegmentOption.class);
                byte kind;
                while ((kind = in.readByte()) != END_OF_OPTION_LIST.kind()) {
                    final SegmentOption option = SegmentOption.ofKind(kind);
                    final Object value = option.readValueFrom(in);

                    options.put(option, value);
                }

                final Segment seg = new Segment(seq, ack, ctl, window, options, in.retain());
                out.add(seg);
            }
            else {
                // wrong length -> pass through message
                out.add(in.retain());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
