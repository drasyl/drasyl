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
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Encodes {@link MessageChunk}s to {@link ByteBuf}s.
 *
 * @see ChunkedMessageInput
 */
@Sharable
public class MessageChunkEncoder extends MessageToByteEncoder<MessageChunk> {
    public static final int MAGIC_NUMBER_CONTENT = -143_591_473;
    public static final int MAGIC_NUMBER_LAST = -143_591_472;
    // magic number: 4 bytes
    // id: 1 byte
    // chunk number (content) / total chunks (last content): 1, 2, or 3 bytes
    // content: n bytes
    public static final int MIN_MESSAGE_LENGTH = 6;
    private final int chunkNoFieldLength;

    /**
     * @param chunkNoFieldLength the length of the chunkNo field
     * @throws IllegalArgumentException if {@code lengthFieldLength} is not 1, 2, or 3
     */
    public MessageChunkEncoder(final int chunkNoFieldLength) {
        if (chunkNoFieldLength != 1 && chunkNoFieldLength != 2 &&
                chunkNoFieldLength != 3) {
            throw new IllegalArgumentException("chunkNoFieldLength must be either 1, 2, or 3: " + chunkNoFieldLength);
        }
        this.chunkNoFieldLength = chunkNoFieldLength;
    }

    public MessageChunkEncoder() {
        this(2);
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final MessageChunk msg,
                          final ByteBuf out) {
        if (msg instanceof LastMessageChunk) {
            out.writeInt(MAGIC_NUMBER_LAST);
        }
        else {
            out.writeInt(MAGIC_NUMBER_CONTENT);
        }
        out.writeByte(msg.msgId());

        switch (chunkNoFieldLength) {
            case 1:
                if (msg.chunkNo() >= 256) {
                    throw new IllegalArgumentException(
                            "length does not fit into a byte: " + msg.chunkNo());
                }
                out.writeByte((byte) msg.chunkNo());
                break;
            case 2:
                if (msg.chunkNo() >= 65536) {
                    throw new IllegalArgumentException(
                            "length does not fit into a short integer: " + msg.chunkNo());
                }
                out.writeShort((short) msg.chunkNo());
                break;
            case 3:
                if (msg.chunkNo() >= 16777216) {
                    throw new IllegalArgumentException(
                            "length does not fit into a medium integer: " + msg.chunkNo());
                }
                out.writeMedium(msg.chunkNo());
                break;
            default:
                throw new Error("should not reach here");
        }

        out.writeBytes(msg.content());
    }
}
