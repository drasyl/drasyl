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
package org.drasyl.peer.connection.handler;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.drasyl.DrasylException;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ChunkedMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Allows sending bigger messages as chunks.
 */
public class ChunkedMessageHandler extends SimpleChannelDuplexHandler<ChunkedMessage, ApplicationMessage> {
    public static final String CHUNK_HANDLER = "chunkHandler";
    public static final int CHUNK_SIZE = 32768; // 2^15 bytes for payload and 2^15 bytes for meta-data
    private static final Logger LOG = LoggerFactory.getLogger(ChunkedMessageHandler.class);
    private final int maxContentLength;
    // TODO: Delete chunks if after n seconds not all chunks have arrived
    private final Multimap<String, ChunkedMessage> chunks;

    public ChunkedMessageHandler(int maxContentLength) {
        super(true, false, false);
        this.maxContentLength = maxContentLength;
        chunks = HashMultimap.create();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx,
                                ChunkedMessage msg) throws Exception {
        if (msg.getContentLength() > maxContentLength) {
            ctx.writeAndFlush(new StatusMessage(StatusMessage.Code.STATUS_PAYLOAD_TOO_LARGE, msg.getId()));

            if (LOG.isDebugEnabled()) {
                LOG.debug("[{}]: Dropped chunked message `{}` because content length of `{}` > `{}`", ctx.channel().id().asShortText(), msg, msg.getContentLength(), maxContentLength);
            }
        }
        else if (msg.getSequenceNumber() > 0 && !chunks.containsKey(msg.getId())) {
            ctx.writeAndFlush(new StatusMessage(StatusMessage.Code.STATUS_BAD_REQUEST, msg.getId()));

            if (LOG.isDebugEnabled()) {
                LOG.debug("[{}]: Dropped chunked message `{}` because start chunk was not sent", ctx.channel().id().asShortText(), msg);
            }
        }

        if (msg.getPayload().length != 0) {
            chunks.put(msg.getId(), msg);
        }
        else {
            joinOnLastChunk(ctx, msg);
        }
    }

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx,
                                 ApplicationMessage msg, ChannelPromise promise) throws Exception {
        if (msg.getPayload().length > maxContentLength) {
            throw new DrasylException("Payload is bigger than max content length.");
        }

        if (msg.getPayload().length > CHUNK_SIZE) {
            // Generate checksum
            String checksum = DigestUtils.sha256Hex(msg.getPayload());

            List<ByteBuffer> chunkedArray = chunkedArray(msg.getPayload(), CHUNK_SIZE);

            AtomicReference<String> id = new AtomicReference<>();

            AtomicInteger sequenceNumber = new AtomicInteger();
            chunkedArray.forEach(byteBuf -> {
                ChunkedMessage chunkedMessage;
                if (id.get() == null) {
                    chunkedMessage = new ChunkedMessage(msg.getRecipient(), msg.getSender(), byteBuf.array(), msg.getPayload().length, checksum);
                    id.set(chunkedMessage.getId());
                }
                else {
                    chunkedMessage = new ChunkedMessage(msg.getRecipient(), msg.getSender(), id.get(), byteBuf.array(), sequenceNumber.get());
                }

                sequenceNumber.getAndIncrement();
                ctx.writeAndFlush(chunkedMessage);
            });

            // Send last chunk
            ctx.writeAndFlush(new ChunkedMessage(msg.getRecipient(), msg.getSender(), id.get(), sequenceNumber.get()), promise);

            // Release not chunked message
            ReferenceCountUtil.release(msg);
        }
        else {
            // Skipp
            ctx.write(msg, promise);
        }
    }

    /**
     * Joins the chunks if the last chunk has arrived.
     *
     * @param ctx channel context
     * @param msg last chunk
     */
    private void joinOnLastChunk(ChannelHandlerContext ctx, ChunkedMessage msg) {
        ArrayList<ChunkedMessage> sortedChunks = new ArrayList<>(chunks.get(msg.getId()));
        sortedChunks.sort(Comparator.comparingInt(ChunkedMessage::getSequenceNumber));

        String chunkedMessageChecksum = sortedChunks.get(0).getChecksum();
        byte[] payload = new byte[sortedChunks.get(0).getContentLength()];

        int offset = 0;
        for (ChunkedMessage chunk : sortedChunks) {
            System.arraycopy(chunk.getPayload(), 0, payload, offset, chunk.getPayload().length);
            offset += chunk.getPayload().length;
        }

        // Delete chunked messages
        chunks.asMap().remove(msg.getId());

        // Check if checksum is correct
        String checksum = DigestUtils.sha256Hex(payload);

        if (!checksum.equals(chunkedMessageChecksum)) {
            ctx.writeAndFlush(new StatusMessage(StatusMessage.Code.STATUS_PRECONDITION_FAILED, msg.getId()));

            if (LOG.isDebugEnabled()) {
                LOG.debug("[{}]: Dropped chunked message `{}` because checksum was invalid", ctx.channel().id().asShortText(), msg);
            }
        }
        else {
            ApplicationMessage applicationMessage = new ApplicationMessage(msg.getId(), msg.getSender(), msg.getRecipient(), payload, (short) 0);

            ctx.fireChannelRead(applicationMessage);
        }
    }

    private List<ByteBuffer> chunkedArray(byte[] source, int chunksize) {
        ArrayList<ByteBuffer> buffer = new ArrayList<>();

        int offset = 0;
        while (offset < source.length) {
            ByteBuffer byteBuffer;
            if (offset + chunksize > source.length) {
                byteBuffer = ByteBuffer.allocate(source.length - offset);
                byteBuffer.put(source, offset, source.length - offset);
                offset += source.length - offset;
            }
            else {
                byteBuffer = ByteBuffer.allocate(chunksize);
                byteBuffer.put(source, offset, chunksize);
                offset += chunksize;
            }

            buffer.add(byteBuffer);
        }

        return buffer;
    }
}
