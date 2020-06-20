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

import java.util.LinkedList;
import java.util.Queue;

public class ChunkedMessageInput implements ChunkedInput<ChunkedMessage> {
    private final CompressedPublicKey sender;
    private final CompressedPublicKey recipient;
    private final int contentLength;
    private final int chunkSize;
    private final String checksum;
    private final Queue<ByteBuf> chunks;
    private final ByteBuf sourcePayload;
    private final String msgID;
    private int sequenceNumber;
    private long progress;
    private boolean sentLastChuck;

    /**
     * Generates a {@link ChunkedInput} for the {@link ChunkedWriteHandler} from the given {@link
     * ApplicationMessage}.
     *
     * @param msg the message that should be sent in chunks
     */
    public ChunkedMessageInput(ApplicationMessage msg, int chunkSize) {
        this.sender = msg.getSender();
        this.recipient = msg.getRecipient();
        this.checksum = Hashing.murmur3_128Hex(msg.getPayload());
        this.contentLength = msg.payloadAsByteBuf().readableBytes();
        this.msgID = msg.getId();
        this.sequenceNumber = 0;
        this.chunkSize = chunkSize;
        this.sourcePayload = msg.payloadAsByteBuf();
        this.chunks = chunkedArray(sourcePayload, this.chunkSize);
        this.sentLastChuck = false;
    }

    @Override
    public boolean isEndOfInput() throws Exception {
        return sentLastChuck;
    }

    @Override
    public void close() throws Exception {
        chunks.clear();
        sourcePayload.release();
    }

    @Override
    public ChunkedMessage readChunk(ChannelHandlerContext ctx) throws Exception {
        return readChunk(ctx.alloc());
    }

    @Override
    public ChunkedMessage readChunk(ByteBufAllocator allocator) throws Exception {
        if (chunks.isEmpty()) {
            if (sentLastChuck) {
                return null;
            }
            else {
                // Send last chunk for this input
                sentLastChuck = true;
                return ChunkedMessage.createLastChunk(sender, recipient, msgID, sequenceNumber);
            }
        }
        else {
            boolean release = true;
            try {
                ByteBuf byteBuf = chunks.poll();
                ChunkedMessage chunkedMessage;
                int readableBytes = byteBuf.readableBytes();

                if (sequenceNumber == 0) {
                    // Send first chunk for this input
                    chunkedMessage = ChunkedMessage.createFirstChunk(sender, recipient, msgID, new byte[readableBytes], contentLength, checksum);
                }
                else {
                    // Send follow chunk for this input
                    chunkedMessage = ChunkedMessage.createFollowChunk(sender, recipient, msgID, new byte[readableBytes], sequenceNumber);
                }

                byteBuf.readBytes(chunkedMessage.getPayload());
                release = false;
                progress += readableBytes;
                sequenceNumber++;

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
     * @param source    the source {@link ByteBuf}
     * @param chunksize the chunk size
     * @return a list of {@link ByteBuf} chunks/slices of the source
     */
    private Queue<ByteBuf> chunkedArray(ByteBuf source, int chunksize) {
        LinkedList<ByteBuf> buffer = new LinkedList<>();

        int offset = 0;
        while (offset < source.readableBytes()) {
            boolean release = true;
            ByteBuf byteBuffer = null;

            try {
                if (offset + chunksize > source.readableBytes()) {
                    byteBuffer = source.slice(offset, source.readableBytes() - offset);
                    offset += source.readableBytes() - offset;
                }
                else {
                    byteBuffer = source.slice(offset, chunksize);
                    offset += chunksize;
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

        return buffer;
    }
}
