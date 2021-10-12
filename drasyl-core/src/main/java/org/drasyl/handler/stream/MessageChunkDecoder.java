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
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

/**
 * Decodes {@link ByteBuf}s with correct magic number to {@link MessageChunk}s.
 *
 * @see ChunkedMessageInput
 */
@Sharable
public class MessageChunkDecoder extends MessageToMessageDecoder<ByteBuf> {
    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in,
                          final List<Object> out) throws Exception {
        if (in.readableBytes() >= MessageChunkEncoder.MIN_MESSAGE_LENGTH) {
            in.markReaderIndex();
            final int magicNumber = in.readInt();
            final byte id = in.readByte();
            if (MessageChunkEncoder.MAGIC_NUMBER_CONTENT == magicNumber) {
                final byte chunkNo = in.readByte();
                out.add(new MessageChunk(id, chunkNo, in.retainedSlice()));
            }
            else if (MessageChunkEncoder.MAGIC_NUMBER_LAST == magicNumber) {
                final byte totalChunks = in.readByte();
                out.add(new LastMessageChunk(id, totalChunks, in.retainedSlice()));
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
