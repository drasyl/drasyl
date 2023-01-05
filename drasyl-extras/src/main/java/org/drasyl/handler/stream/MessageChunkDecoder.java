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
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

import static org.drasyl.handler.stream.MessageChunkEncoder.MAGIC_NUMBER_CONTENT;
import static org.drasyl.handler.stream.MessageChunkEncoder.MAGIC_NUMBER_LAST;

/**
 * Decodes {@link ByteBuf}s with correct magic number to {@link MessageChunk}s.
 *
 * @see ChunkedMessageInput
 */
@Sharable
public class MessageChunkDecoder extends MessageToMessageDecoder<ByteBuf> {
    private final int chunkNoFieldLength;

    /**
     * @param chunkNoFieldLength the length of the chunkNo field
     * @throws IllegalArgumentException if {@code lengthFieldLength} is not 1, 2, or 3
     */
    public MessageChunkDecoder(final int chunkNoFieldLength) {
        if (chunkNoFieldLength != 1 && chunkNoFieldLength != 2 &&
                chunkNoFieldLength != 3) {
            throw new IllegalArgumentException("chunkNoFieldLength must be either 1, 2, or 3: " + chunkNoFieldLength);
        }
        this.chunkNoFieldLength = chunkNoFieldLength;
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in,
                          final List<Object> out) throws Exception {
        if (in.readableBytes() >= MessageChunkEncoder.MIN_MESSAGE_LENGTH) {
            in.markReaderIndex();
            final int magicNumber = in.readInt();
            final byte id = in.readByte();
            if (MAGIC_NUMBER_CONTENT == magicNumber) {
                final int chunkNo = getChunkNo(in);
                out.add(new MessageChunk(id, chunkNo, in.retain()));
            }
            else if (MAGIC_NUMBER_LAST == magicNumber) {
                final int totalChunks = getChunkNo(in);
                out.add(new LastMessageChunk(id, totalChunks, in.retain()));
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

    private int getChunkNo(final ByteBuf buf) {
        final int chunkNo;
        switch (chunkNoFieldLength) {
            case 1:
                chunkNo = buf.readUnsignedByte();
                break;
            case 2:
                chunkNo = buf.readUnsignedShort();
                break;
            case 3:
                chunkNo = buf.readUnsignedMedium();
                break;
            default:
                throw new DecoderException("unsupported lengthFieldLength: " + chunkNoFieldLength + " (expected: 1, 2, or 3)");
        }
        return chunkNo;
    }
}
