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
package org.drasyl.channel.chunk;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.MessageAggregator;

/**
 * Aggregates {@link MessageChunk}s to a {@link ReassembledMessage}.
 *
 * @see ChunkedMessageInput
 */
public class ChunkedMessageAggregator extends MessageAggregator<MessageChunk, MessageChunk, MessageChunk, ReassembledMessage> {
    /**
     * Creates a new instance.
     *
     * @param maxContentLength the maximum length of the aggregated message. If the length of the
     *                         aggregated content exceeds this value, {@link #handleOversizedMessage(ChannelHandlerContext,
     *                         MessageChunk)} will be called.
     */
    public ChunkedMessageAggregator(final int maxContentLength) {
        super(maxContentLength);
    }

    @Override
    protected boolean isStartMessage(final MessageChunk msg) {
        return msg.chunkNo() == 0;
    }

    @Override
    protected boolean isContentMessage(final MessageChunk msg) {
        return true;
    }

    @Override
    protected boolean isLastContentMessage(final MessageChunk msg) {
        return msg instanceof LastMessageChunk;
    }

    @Override
    protected boolean isAggregated(final MessageChunk msg) {
        return false;
    }

    @Override
    protected boolean isContentLengthInvalid(final MessageChunk start,
                                             final int maxContentLength) {
        return false;
    }

    @Override
    protected Object newContinueResponse(final MessageChunk start,
                                         final int maxContentLength,
                                         final ChannelPipeline pipeline) {
        return null;
    }

    @Override
    protected boolean closeAfterContinueResponse(final Object msg) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean ignoreContentAfterContinueResponse(final Object msg) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ReassembledMessage beginAggregation(final MessageChunk start,
                                                  final ByteBuf content) {
        return new ReassembledMessage(content);
    }
}
