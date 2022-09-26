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
package org.drasyl.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

import static org.drasyl.util.Preconditions.requirePositive;

/**
 * A decoder that splits received {@link ByteBuf}s into frames not larger then {@link
 * #maxFrameLength}. For example, if you received the following four fragmented packets:
 * <pre>
 * +---+----+-----+----+
 * | A | BC | DEF | GH |
 * +---+----+-----+----+
 * </pre>
 * A {@link MaxLengthFrameDecoder}{@code (2)} will decode them into the following three packets with
 * the fixed length:
 * <pre>
 * +---+----+----+---+----+
 * | A | BC | DE | F | GH |
 * +---+----+----+---+----+
 * </pre>
 */
public class MaxLengthFrameDecoder extends ByteToMessageDecoder {
    private final int maxFrameLength;

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength the maximum length of the frame
     */
    public MaxLengthFrameDecoder(final int maxFrameLength) {
        this.maxFrameLength = requirePositive(maxFrameLength);
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in,
                          final List<Object> out) throws Exception {
        out.add(in.readRetainedSlice(Math.min(in.readableBytes(), maxFrameLength)));
    }
}
