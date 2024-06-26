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

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Buffers until all {@link MessageChunk}s belonging to the same message have been collected, then
 * passes them in the correct order.
 *
 * @see ChunkedMessageInput
 */
@Deprecated
public class MessageChunksBuffer extends MessageToMessageDecoder<MessageChunk> {
    private static final Logger LOG = LoggerFactory.getLogger(MessageChunksBuffer.class);
    private final int maxContentLength;
    private final int allChunksTimeout;
    private Byte id;
    private int contentLength;
    private final List<MessageChunk> chunks;
    private LastMessageChunk lastChunk;
    private ScheduledFuture<?> timeoutGuard;

    MessageChunksBuffer(final int maxContentLength,
                        final int allChunksTimeout,
                        final List<MessageChunk> chunks,
                        final Byte id,
                        final int contentLength,
                        final LastMessageChunk lastChunk,
                        final ScheduledFuture<?> timeoutGuard) {
        this.maxContentLength = requirePositive(maxContentLength);
        this.allChunksTimeout = requireNonNegative(allChunksTimeout);
        this.chunks = requireNonNull(chunks);
        this.id = id;
        this.contentLength = requireNonNegative(contentLength);
        this.lastChunk = lastChunk;
        this.timeoutGuard = timeoutGuard;
    }

    /**
     * Creates a new instance.
     *
     * @param maxContentLength the maximum cumulative length of the aggregated message. If the
     *                         length of the buffered content exceeds this value, a {@link
     *                         TooLongFrameException)} will be thrown.
     * @param allChunksTimeout time in milliseconds after receiving the first chunk to wait for
     *                         remaining chunks. Upon timeout, received chunks will be discarded. A
     *                         value of {@code 0} deactivates the timeout function.
     * @param maxChunks        maximum number of chunks to collect. If the more chunks are required
     *                         to aggregate the message, all chunks will be discarded.
     */
    public MessageChunksBuffer(final int maxContentLength,
                               final int allChunksTimeout,
                               final int maxChunks) {
        this(maxContentLength, allChunksTimeout, new MessageChunksBufferInputList(maxChunks), null, 0, null, null);
    }

    @SuppressWarnings("java:S3776")
    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final MessageChunk msg, final List<Object> out) throws Exception {
        // first chunk?
        if (id == null) {
            id = msg.msgId();
            if (allChunksTimeout > 0) {
                timeoutGuard = ctx.executor().schedule(() -> {
                    LOG.trace("Not all chunks have been received within {}ms. Discard {} chunks.", () -> allChunksTimeout, chunks::size);
                    discard();
                }, allChunksTimeout, MILLISECONDS);
            }
        }

        // does the chunk belong to same ByteBuf?
        if (id == msg.msgId()) {
            contentLength += msg.content().readableBytes();

            if (contentLength > maxContentLength) {
                discard();
                throw new TooLongFrameException("The chunked ByteBuf has exhausted the max allowed size of " + maxContentLength + " bytes (tried to allocate additional " + (contentLength - maxContentLength) + " bytes).");
            }

            if (msg instanceof LastMessageChunk) {
                if (lastChunk == null) {
                    if (msg.chunkNo() < chunks.size()) {
                        discard();
                        throw new TooLongFrameException("More chunks received (" + chunks.size() + ") then specified in chunk header (" + msg.chunkNo() + ").");
                    }

                    lastChunk = (LastMessageChunk) msg.retain();
                }
            }
            else {
                chunks.set(msg.chunkNo(), (MessageChunk) msg.retain());
            }

            checkCompleteness(out);
        }
    }

    private void checkCompleteness(final List<Object> out) {
        if (lastChunk != null && lastChunk.chunkNo() == chunks.size()) {
            if (timeoutGuard != null) {
                timeoutGuard.cancel(false);
            }

            out.addAll(chunks);
            out.add(lastChunk);

            reset();
        }
    }

    private void reset() {
        id = null;
        contentLength = 0;
        chunks.clear();
        lastChunk = null;
        timeoutGuard = null;
    }

    private void discard() {
        chunks.forEach(MessageChunk::release);
        reset();
    }
}
