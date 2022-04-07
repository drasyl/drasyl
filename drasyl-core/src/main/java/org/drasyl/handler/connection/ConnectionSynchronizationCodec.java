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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.List;

public class ConnectionSynchronizationCodec extends MessageToMessageCodec<ByteBuf, Segment> {
    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final Segment seg,
                          final List<Object> out) throws Exception {
        final ByteBuf buf = ctx.alloc().buffer();
        buf.writeInt(seg.seq());
        buf.writeInt(seg.ack());
        buf.writeByte(seg.ctl());
        buf.writeBytes(seg.content());
        seg.release();
        out.add(buf);
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in,
                          final List<Object> out) {
        if (in.readableBytes() == 9) {
            final int seq = in.readInt();
            final int ack = in.readInt();
            final byte ctl = in.readByte();
            final Segment seg = new Segment(seq, ack, ctl, in.discardSomeReadBytes().retain());
            out.add(seg);
        }
        else {
            throw new CodecException("Message must be 9 bytes long (was " + in.readableBytes() + " bytes long).");
        }
    }
}
