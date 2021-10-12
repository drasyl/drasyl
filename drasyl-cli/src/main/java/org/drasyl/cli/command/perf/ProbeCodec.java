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
package org.drasyl.cli.command.perf;

import com.google.common.primitives.Longs;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.cli.command.perf.message.Probe;

import java.util.List;

/**
 * Encodes {@link Probe} messages to {@link ByteBuf}s and vice versa.
 */
@Sharable
public class ProbeCodec extends MessageToMessageCodec<ByteBuf, Probe> {
    /**
     * Is used to identity probe messages. probe messages are used for actual performance
     * measurements.
     */
    static final long MAGIC_NUMBER_PROBE = Longs.fromByteArray(new byte[]{
            20,
            21,
            1,
            23,
            0,
            1,
            38,
            16
    });

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final Probe msg,
                          final List<Object> out) {
        final ByteBuf buf = ctx.alloc().buffer();
        buf.writeLong(MAGIC_NUMBER_PROBE);
        buf.writeLong(msg.getMessageNo());
        buf.writeBytes(msg.getPayload());
        out.add(buf);
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in,
                          final List<Object> out) {
        if (in.readableBytes() >= Long.BYTES) {
            in.markReaderIndex();
            if (in.readLong() == MAGIC_NUMBER_PROBE) {
                final long messageNo = in.readLong();
                // ignore payload
                out.add(new Probe(new byte[0], messageNo));
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
