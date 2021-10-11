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

/**
 * Represents the very last chunk of a message that is too large to be transmitted as a whole.
 *
 * @see MessageChunk
 * @see ChunkedMessageInput
 */
public class LastMessageChunk extends MessageChunk {
    /**
     * @param msgId   id of the message to which this chunk belongs
     * @param chunkNo number of this chunk (starting with {@code 0})
     * @param content message's content portion of this chunk
     */
    public LastMessageChunk(final byte msgId, final byte chunkNo, final ByteBuf content) {
        super(msgId, chunkNo, content);
    }

    @Override
    public String toString() {
        return "LastMessageChunk{" +
                "msgId=" + msgId() +
                ", chunkNo=" + chunkNo() +
                ", content=" + content() +
                '}';
    }

    @Override
    public LastMessageChunk replace(final ByteBuf content) {
        return new LastMessageChunk(msgId(), chunkNo(), content);
    }
}
