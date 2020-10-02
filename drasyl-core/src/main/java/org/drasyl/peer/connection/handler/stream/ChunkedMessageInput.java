/*
 * Copyright (c) 2020.
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
package org.drasyl.peer.connection.handler.stream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.stream.ChunkedInput;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.crypto.Hashing;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ChunkedMessage;
import org.drasyl.peer.connection.message.MessageId;

import java.util.LinkedList;
import java.util.Queue;

/**
 * This class is responsible for chunking a {@link ApplicationMessage} into multiple {@link
 * ChunkedMessage}s.
 */
@SuppressWarnings({ "java:S107" })
public class ChunkedMessageInput implements ChunkedInput<ChunkedMessage> {
    private final CompressedPublicKey sender;
    private final CompressedPublicKey recipient;
    private final int contentLength;
    private final String checksum;
    private final Queue<ByteBuf> chunks;
    private final ByteBuf sourcePayload;
    private final MessageId msgID;
    private long progress;
    private boolean sentLastChuck;

    ChunkedMessageInput(final CompressedPublicKey sender,
                        final CompressedPublicKey recipient,
                        final int contentLength,
                        final String checksum,
                        final Queue<ByteBuf> chunks,
                        final ByteBuf sourcePayload,
                        final MessageId msgID,
                        final long progress,
                        final boolean sentLastChuck) {
        this.sender = sender;
        this.recipient = recipient;
        this.contentLength = contentLength;
        this.checksum = checksum;
        this.chunks = chunks;
        this.sourcePayload = sourcePayload;
        this.msgID = msgID;
        this.progress = progress;
        this.sentLastChuck = sentLastChuck;
    }

    /**
     * Generates a {@link ChunkedInput} for the {@link ChunkedWriteHandler} from the given {@link
     * ApplicationMessage}.
     *
     * @param msg       the message that should be sent in chunks
     * @param chunkSize the size of each chunk
     */
    public ChunkedMessageInput(final ApplicationMessage msg, final int chunkSize) {
        this(msg.getSender(), msg.getRecipient(), msg.payloadAsByteBuf().readableBytes(),
                Hashing.murmur3x64Hex(msg.getPayload()), new LinkedList<>(),
                msg.payloadAsByteBuf(),
                msg.getId(), 0, false);
        chunkedArray(this.chunks, sourcePayload, chunkSize);
    }

    /**
     * Return {@code true} if and only if there is no data left in the stream and the stream has
     * reached at its end.
     */
    @Override
    public boolean isEndOfInput() {
        return sentLastChuck;
    }

    /**
     * Releases the resources associated with the input.
     */
    @Override
    public void close() {
        chunks.clear();
        sourcePayload.release();
    }

    /**
     * @param ctx The context which provides a {@link ByteBufAllocator} if buffer allocation is
     *            necessary.
     * @return the fetched chunk. {@code null} if there is no data left in the stream. Please note
     * that {@code null} does not necessarily mean that the stream has reached at its end.  In a
     * slow stream, the next chunk might be unavailable just momentarily.
     * @deprecated Use {@link #readChunk(ByteBufAllocator)}.
     *
     * <p>Fetches a chunked data from the stream. Once this method returns the last chunk
     * and thus the stream has reached at its end, any subsequent {@link #isEndOfInput()} call must
     * return {@code true}.
     */
    @Override
    public ChunkedMessage readChunk(final ChannelHandlerContext ctx) {
        return readChunk(ctx.alloc());
    }

    /**
     * Fetches a chunked data from the stream. Once this method returns the last chunk and thus the
     * stream has reached at its end, any subsequent {@link #isEndOfInput()} call must return {@code
     * true}.
     *
     * @param allocator {@link ByteBufAllocator} if buffer allocation is necessary.
     * @return the fetched chunk. {@code null} if there is no data left in the stream. Please note
     * that {@code null} does not necessarily mean that the stream has reached at its end.  In a
     * slow stream, the next chunk might be unavailable just momentarily.
     */
    @Override
    public ChunkedMessage readChunk(final ByteBufAllocator allocator) {
        if (chunks.isEmpty()) {
            if (sentLastChuck) {
                return null;
            }
            else {
                // Send last chunk for this input
                sentLastChuck = true;
                return ChunkedMessage.createLastChunk(sender, recipient, msgID);
            }
        }
        else {
            boolean release = true;
            try {
                final ByteBuf byteBuf = chunks.poll();
                final ChunkedMessage chunkedMessage;
                final int readableBytes = byteBuf.readableBytes();

                if (progress == 0) {
                    // Send first chunk for this input
                    chunkedMessage = ChunkedMessage.createFirstChunk(sender, recipient, msgID, new byte[readableBytes], contentLength, checksum);
                }
                else {
                    // Send follow chunk for this input
                    chunkedMessage = ChunkedMessage.createFollowChunk(sender, recipient, msgID, new byte[readableBytes]);
                }

                byteBuf.readBytes(chunkedMessage.getPayload());
                release = false;
                progress += readableBytes;

                return chunkedMessage;
            }
            finally {
                if (release) {
                    ReferenceCountUtil.release(sourcePayload);
                }
            }
        }
    }

    /**
     * Returns the length of the input.
     *
     * @return the length of the input if the length of the input is known. a negative value if the
     * length of the input is unknown.
     */
    @Override
    public long length() {
        return contentLength;
    }

    /**
     * Returns current transfer progress.
     */
    @Override
    public long progress() {
        return progress;
    }

    /**
     * Creates a list of {@link ByteBuf}s from the given <code>source</code>. The source {@link
     * ByteBuf} is not modified during this process.
     *
     * @param buffer    the dest buffer queue
     * @param source    the source {@link ByteBuf}
     * @param chunkSize the chunk size
     */
    void chunkedArray(final Queue<ByteBuf> buffer, final ByteBuf source, final int chunkSize) {
        int offset = 0;
        while (offset < source.readableBytes()) {
            boolean release = true;
            ByteBuf byteBuffer = null;

            try {
                if (offset + chunkSize > source.readableBytes()) {
                    byteBuffer = source.slice(offset, source.readableBytes() - offset);
                    offset += source.readableBytes() - offset;
                }
                else {
                    byteBuffer = source.slice(offset, chunkSize);
                    offset += chunkSize;
                }

                release = false;
                buffer.add(byteBuffer);
            }
            finally {
                if (release && byteBuffer != null) {
                    byteBuffer.release();
                }
            }
        }
    }
}