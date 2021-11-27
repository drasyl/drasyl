/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.tunnel.handler;

import com.google.common.primitives.Ints;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.cli.tunnel.message.Write;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class TunnelWriteCodec extends MessageToMessageCodec<ByteBuf, Write> {
    /**
     * Is used to identity probe messages. probe messages are used for actual performance
     * measurements.
     */
    static final int MAGIC_NUMBER = Ints.fromByteArray(new byte[]{
            -125, -19, 31, 5
    });
    static final int MIN_LENGTH = Integer.BYTES + Integer.BYTES;

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final Write msg, final List<Object> out) throws Exception {
        final ByteBuf buf = ctx.alloc().buffer();
        buf.writeInt(MAGIC_NUMBER);
        buf.writeInt(msg.getChannelId().length());
        buf.writeCharSequence(msg.getChannelId(), UTF_8);
        buf.writeBytes(msg.getMsg());
        out.add(buf);
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in,
                          final List<Object> out) throws Exception {
        if (in.readableBytes() >= MIN_LENGTH) {
            in.markReaderIndex();
            if (in.readInt() == MAGIC_NUMBER) {
                final int channelIdLength = in.readInt();
                final String channelId = in.readCharSequence(channelIdLength, UTF_8).toString();
                final ByteBuf msg = in.readBytes(in.readableBytes());
                out.add(new Write(channelId, msg));
            }
            else {
                // wrong magic number -> pass through message
                in.resetReaderIndex();
                out.add(in.retain());
            }
        }
        else {
            // too short -> pass through message
            out.add(in.retain());
        }
    }
}
