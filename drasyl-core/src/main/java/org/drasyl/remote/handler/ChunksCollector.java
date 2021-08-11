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
package org.drasyl.remote.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import org.drasyl.remote.protocol.BodyChunkMessage;
import org.drasyl.remote.protocol.ChunkMessage;
import org.drasyl.remote.protocol.HeadChunkMessage;
import org.drasyl.remote.protocol.Nonce;
import org.drasyl.remote.protocol.PartialReadMessage;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * This class collects the individual chunks of a message and returns the message once all chunks
 * have been collected.
 */
class ChunksCollector {
    private static final Logger LOG = LoggerFactory.getLogger(ChunksCollector.class);
    private final int maxContentLength;
    private final Nonce nonce;
    private final Map<Integer, ByteBuf> chunks;
    private final ByteBufAllocator alloc;
    private int messageSize;
    private int totalChunks;

    public ChunksCollector(final int maxContentLength,
                           final Nonce nonce,
                           final ByteBufAllocator alloc) {
        this.maxContentLength = maxContentLength;
        this.nonce = requireNonNull(nonce);
        this.alloc = requireNonNull(alloc);
        this.chunks = new HashMap<>();
    }

    /**
     * This method collects chunks and returns a message if {@code chunk} was the last missing part
     * of the message. Otherwise {@code chunk} is temporarily cached, waits for the missing parts,
     * and returns {@code null}.
     * <p>
     * This method will release {@code chunk} even in case of an exception.
     *
     * @param chunk chunk to collect
     * @return the message if all chunks were collected, otherwise {@code null}
     * @throws IOException           if chunk could not be read
     * @throws IllegalStateException if an attempt is made to add a chunk from another message or to
     *                               an already composed message
     */
    public synchronized PartialReadMessage addChunk(final ChunkMessage chunk) throws IOException {
        // already composed?
        if (allChunksPresent()) {
            ReferenceCountUtil.safeRelease(chunk);
            throw new IllegalStateException("All chunks have already been collected and message has already been returned");
        }

        // belongs to our message?
        if (!chunk.getNonce().equals(nonce)) {
            ReferenceCountUtil.safeRelease(chunk);
            throw new IllegalStateException("This chunk belongs to another message!");
        }

        final int chunkSize = chunk.getBytes().readableBytes();
        final int chunkNo = chunk instanceof HeadChunkMessage ? 0 : ((BodyChunkMessage) chunk).getChunkNo().getValue();

        // add chunk
        if (messageSize + chunkSize > maxContentLength) {
            ReferenceCountUtil.safeRelease(chunk);
            LOG.debug("The chunked message with id `{}` has exhausted the max allowed size of {} bytes and was therefore dropped (tried to allocate additional {} bytes).", nonce, maxContentLength, chunkSize);
            throw new IllegalStateException("The chunked message with id `" + nonce + "` has exhausted the max allowed size of " + maxContentLength + " bytes and was therefore dropped (tried to allocate additional " + chunkSize + " bytes).");
        }
        messageSize += chunkSize;
        // does also release any previous chunk with same chunkNo
        ReferenceCountUtil.safeRelease(chunks.putIfAbsent(chunkNo, chunk.getBytes()));

        // head chunk? set totalChunks
        if (totalChunks == 0 && chunk instanceof HeadChunkMessage) {
            totalChunks = ((HeadChunkMessage) chunk).getTotalChunks().getValue();
        }

        //noinspection unchecked
        LOG.trace("[{}] {} of {} chunks collected ({} total bytes; last received chunk: {})", () -> nonce, chunks::size, () -> totalChunks, () -> messageSize, () -> chunkNo + 1);

        // message complete?
        if (allChunksPresent()) {
            // message complete, use zero-copy to compose it!
            final CompositeByteBuf messageByteBuf = alloc.compositeBuffer(totalChunks);
            final ByteBuf[] components = chunks.values().toArray(new ByteBuf[0]);
            chunks.clear();
            messageByteBuf.addComponents(true, components);
            return PartialReadMessage.of(messageByteBuf);
        }
        else {
            // message not complete, return null!
            return null;
        }
    }

    private boolean allChunksPresent() {
        return totalChunks > 0 && chunks.size() == totalChunks;
    }

    /**
     * Should release any resources allocated or managed by this object.
     */
    public void release() {
        chunks.values().forEach(ReferenceCountUtil::safeRelease);
        chunks.clear();
    }

    public boolean hasChunks() {
        return !chunks.isEmpty();
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public int getPresentChunks() {
        return chunks.size();
    }
}
