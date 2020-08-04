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

@SuppressWarnings({ "java:S107" })
public class ChunkedMessageInput implements ChunkedInput<ChunkedMessage> {
    private final CompressedPublicKey sender;
    private final CompressedPublicKey recipient;
    private final int contentLength;
    private final String checksum;
    private final Queue<ByteBuf> chunks;
    private final ByteBuf sourcePayload;
    private final Class<?> clazz;
    private final MessageId msgID;
    private long progress;
    private boolean sentLastChuck;

    ChunkedMessageInput(CompressedPublicKey sender,
                        CompressedPublicKey recipient,
                        int contentLength,
                        String checksum,
                        Queue<ByteBuf> chunks,
                        ByteBuf sourcePayload,
                        Class<?> clazz,
                        MessageId msgID,
                        long progress,
                        boolean sentLastChuck) {
        this.sender = sender;
        this.recipient = recipient;
        this.contentLength = contentLength;
        this.checksum = checksum;
        this.chunks = chunks;
        this.sourcePayload = sourcePayload;
        this.clazz = clazz;
        this.msgID = msgID;
        this.progress = progress;
        this.sentLastChuck = sentLastChuck;
    }

    /**
     * Generates a {@link ChunkedInput} for the {@link ChunkedWriteHandler} from the given {@link
     * ApplicationMessage}.
     *
     * @param msg the message that should be sent in chunks
     */
    public ChunkedMessageInput(ApplicationMessage msg, int chunkSize) {
        this(msg.getSender(), msg.getRecipient(), msg.payloadAsByteBuf().readableBytes(),
                Hashing.murmur3x64Hex(msg.getPayload()), new LinkedList<>(),
                msg.payloadAsByteBuf(), msg.getPayloadClazz(),
                msg.getId(), 0, false);
        chunkedArray(this.chunks, sourcePayload, chunkSize);
    }

    @Override
    public boolean isEndOfInput() {
        return sentLastChuck;
    }

    @Override
    public void close() {
        chunks.clear();
        sourcePayload.release();
    }

    @Override
    public ChunkedMessage readChunk(ChannelHandlerContext ctx) {
        return readChunk(ctx.alloc());
    }

    @Override
    public ChunkedMessage readChunk(ByteBufAllocator allocator) {
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
                ByteBuf byteBuf = chunks.poll();
                ChunkedMessage chunkedMessage;
                int readableBytes = byteBuf.readableBytes();

                if (progress == 0) {
                    // Send first chunk for this input
                    chunkedMessage = ChunkedMessage.createFirstChunk(sender, recipient, msgID, new byte[readableBytes], clazz, contentLength, checksum);
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

    @Override
    public long length() {
        return contentLength;
    }

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
    void chunkedArray(Queue<ByteBuf> buffer, ByteBuf source, int chunkSize) {
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