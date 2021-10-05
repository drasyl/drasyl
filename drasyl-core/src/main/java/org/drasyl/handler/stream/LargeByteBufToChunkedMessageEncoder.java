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
package org.drasyl.handler.stream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.stream.ChunkedStream;

import java.util.List;

import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Encodes {@link ByteBuf}s exceeding {@link #maxChunkLength} to {@link ChunkedMessageInput}s.
 * Rejects {@link ByteBuf}s exceeding {@link #maxContentLength}.
 * <p>
 * This handler should be used together with {@link io.netty.handler.stream.ChunkedWriteHandler}.
 */
public class LargeByteBufToChunkedMessageEncoder extends MessageToMessageEncoder<ByteBuf> {
    private final int maxChunkLength;
    private final int maxContentLength;

    public LargeByteBufToChunkedMessageEncoder(final int maxChunkLength,
                                               final int maxContentLength) {
        this.maxChunkLength = requirePositive(maxChunkLength);
        this.maxContentLength = requirePositive(maxContentLength);
    }

    @Override
    public boolean acceptOutboundMessage(final Object msg) {
        return msg instanceof ByteBuf && ((ByteBuf) msg).readableBytes() > maxChunkLength;
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final ByteBuf msg,
                          final List<Object> out) throws Exception {
        final int contentLength = msg.readableBytes();
        if (contentLength > maxContentLength) {
            throw new EncoderException("ByteBuf has a size of " + contentLength + " bytes and is too large. The max. allowed size is " + maxContentLength + " bytes. ByteBuf dropped.");
        }
        else {
            out.add(new ChunkedMessageInput(new ChunkedStream(new ByteBufInputStream(msg.retain(), true), maxChunkLength)));
        }
    }
}
