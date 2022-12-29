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
package org.drasyl.handler.oldconnection;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.List;

/**
 * Encodes {@link ByteBuf}s to {@link ConnectionHandshakeSegment}s and vice versa.
 */
@Sharable
public class ConnectionHandshakeCodec extends MessageToMessageCodec<ByteBuf, ConnectionHandshakeSegment> {
    public static final int MAGIC_NUMBER = 852_550_535;
    // Magic Number: 4 bytes
    // SEQ: 4 bytes
    // ACK: 4 bytes
    // CTL: 1 byte
    // data: arbitrary number of bytes
    public static final int MIN_MESSAGE_LENGTH = 13;

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final ConnectionHandshakeSegment seg,
                          final List<Object> out) throws Exception {
        final ByteBuf buf = ctx.alloc().buffer(13);
        buf.writeInt(MAGIC_NUMBER);
        buf.writeInt((int) seg.seq());
        buf.writeInt((int) seg.ack());
        buf.writeByte(seg.ctl());
        buf.writeBytes(seg.content());
        out.add(buf);
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in,
                          final List<Object> out) {
        if (in.readableBytes() >= MIN_MESSAGE_LENGTH) {
            in.markReaderIndex();
            if (MAGIC_NUMBER == in.readInt()) {
                final long seq = in.readUnsignedInt();
                final long ack = in.readUnsignedInt();
                final byte ctl = in.readByte();
                final ConnectionHandshakeSegment seg = new ConnectionHandshakeSegment(seq, ack, ctl, in.discardSomeReadBytes().retain());
                out.add(seg);
            }
            else {
                // wrong magic number -> pass through message
                in.resetReaderIndex();
                out.add(in.retain());
            }
        }
        else {
            // wrong length -> pass through message
            out.add(in.retain());
        }
    }
}
