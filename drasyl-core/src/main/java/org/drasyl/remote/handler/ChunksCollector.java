/*
 * Copyright (c) 2020-2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.remote.handler;

import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import org.drasyl.remote.protocol.MessageId;
import org.drasyl.remote.protocol.RemoteEnvelope;
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
    private final MessageId messageId;
    private final Map<Integer, ByteBuf> chunks;
    private int messageSize;
    private int totalChunks;

    public ChunksCollector(final int maxContentLength, final MessageId messageId) {
        this.maxContentLength = maxContentLength;
        this.messageId = requireNonNull(messageId);
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
    public synchronized <T extends MessageLite> RemoteEnvelope<T> addChunk(final RemoteEnvelope<? extends MessageLite> chunk) throws IOException {
        // already composed?
        if (allChunksPresent()) {
            ReferenceCountUtil.safeRelease(chunk);
            throw new IllegalStateException("All chunks have already been collected and message has already been returned");
        }

        // is chunk?
        if (!chunk.isChunk()) {
            ReferenceCountUtil.safeRelease(chunk);
            throw new IllegalStateException("This is not a chunk!");
        }

        // belongs to our message?
        if (!chunk.getId().equals(messageId)) {
            ReferenceCountUtil.safeRelease(chunk);
            throw new IllegalStateException("This chunk belongs to another message!");
        }

        final int chunkSize = chunk.getInternalByteBuf().readableBytes();
        final int chunkNo = chunk.getChunkNo().getValue();

        // add chunk
        if (messageSize + chunkSize > maxContentLength) {
            ReferenceCountUtil.safeRelease(chunk);
            LOG.debug("The chunked message with id `{}` has exhausted the max allowed size of {} bytes and was therefore dropped (tried to allocate additional {} bytes).", messageId, maxContentLength, chunkSize);
            throw new IllegalStateException("The chunked message with id `" + messageId + "` has exhausted the max allowed size of " + maxContentLength + " bytes and was therefore dropped (tried to allocate additional " + chunkSize + " bytes).");
        }
        messageSize += chunkSize;
        // does also release any previous chunk with same chunkNo
        ReferenceCountUtil.safeRelease(chunks.putIfAbsent(chunkNo, chunk.getInternalByteBuf()));

        // head chunk? set totalChunks
        if (totalChunks == 0 && chunk.getTotalChunks().getValue() > 0) {
            totalChunks = chunk.getTotalChunks().getValue();
        }

        //noinspection unchecked
        LOG.trace("[{}] {} of {} chunks collected ({} total bytes; last received chunk: {})", () -> messageId, chunks::size, () -> totalChunks, () -> messageSize, () -> chunkNo + 1);

        // message complete?
        if (allChunksPresent()) {
            // message complete, use zero-copy to compose it!
            final CompositeByteBuf messageByteBuf = Unpooled.compositeBuffer(totalChunks);
            for (int i = 0; i < totalChunks; i++) {
                final ByteBuf chunkByteBuf = chunks.remove(i);
                messageByteBuf.addComponent(true, chunkByteBuf);
            }
            return RemoteEnvelope.of(messageByteBuf);
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
