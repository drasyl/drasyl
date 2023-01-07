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
import org.drasyl.handler.connection.ConnectionHandshakeSegment.Option;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.drasyl.handler.connection.ConnectionHandshakeSegment.Option.END_OF_OPTION_LIST;

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
    // Options: 2..bytes
    // data: arbitrary number of bytes
    public static final int MIN_MESSAGE_LENGTH = 15;

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final ConnectionHandshakeSegment seg,
                          final List<Object> out) throws Exception {
        final ByteBuf buf = ctx.alloc().buffer(MIN_MESSAGE_LENGTH + seg.content().readableBytes());
        buf.writeInt(MAGIC_NUMBER);
        buf.writeInt((int) seg.seq());
        buf.writeInt((int) seg.ack());
        buf.writeByte(seg.ctl());
        buf.writeInt((int) seg.window());

        // options
        for (final Entry<Option, Object> entry : seg.options().entrySet()) {
            final Option option = entry.getKey();
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
        if (in.readableBytes() >= MIN_MESSAGE_LENGTH) {
            in.markReaderIndex();
            if (MAGIC_NUMBER == in.readInt()) {
                final long seq = in.readUnsignedInt();
                final long ack = in.readUnsignedInt();
                final byte ctl = in.readByte();
                final long window = in.readUnsignedInt();

                // options
                final Map<Option, Object> options = new EnumMap<>(Option.class);
                byte kind;
                while ((kind = in.readByte()) != END_OF_OPTION_LIST.kind()) {
                    final Option option = Option.ofKind(kind);
                    final Object value = option.readValueFrom(in);

                    options.put(option, value);
                }

                final ConnectionHandshakeSegment seg = new ConnectionHandshakeSegment(seq, ack, ctl, window, options, in.retain());
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
