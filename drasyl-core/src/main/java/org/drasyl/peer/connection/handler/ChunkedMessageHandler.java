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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ChunkedMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Allows sending bigger messages in chunks.
 */
public class ChunkedMessageHandler extends SimpleChannelDuplexHandler<ChunkedMessage, ApplicationMessage> {
    public static final String CHUNK_HANDLER = "chunkHandler";
    public static final int CHUNK_SIZE = 32768; // 2^15 bytes for payload and 2^15 bytes for meta-data
    private static final Logger LOG = LoggerFactory.getLogger(ChunkedMessageHandler.class);
    private final int maxContentLength;
    // TODO: Delete chunks if after n seconds not all chunks have arrived
    private final Multimap<String, ChunkedMessage> chunks;
//    private final Map<String, >

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
            LOG.warn("Payload is bigger than max content length. Message with id `{}` was not sent.", msg.getId());
            return;
        }

        if (msg.getPayload().length > CHUNK_SIZE) {
            sendChunkedMessage(ctx, msg, promise);
        }
        else {
            // Skip
            ReferenceCountUtil.retain(msg);
            ctx.write(msg, promise);
        }
    }

    /**
     * Initializes a stream for chunked messages and writes all necessary information into this
     * handler, so that the execution can be paused if the I/O buffer is full.
     */
    private void initChunkedStream(ChannelHandlerContext ctx,
                                   ApplicationMessage msg,
                                   ChannelPromise promise) {
        // Generate checksum
        String checksum = DigestUtils.sha256Hex(msg.getPayload());
        AtomicReference<String> id = new AtomicReference<>();
        AtomicInteger sequenceNumber = new AtomicInteger();
        ByteBuf sourcePayload = msg.payloadAsByteBuf();
    }

    private void sendChunkedMessage(ChannelHandlerContext ctx,
                                    ApplicationMessage msg,
                                    ChannelPromise promise) {
        // Generate checksum
        String checksum = DigestUtils.sha256Hex(msg.getPayload());
        AtomicReference<String> id = new AtomicReference<>();
        AtomicInteger sequenceNumber = new AtomicInteger();
        ByteBuf sourcePayload = msg.payloadAsByteBuf();
        promise.setSuccess();

        try {
            List<ByteBuf> chunkedArray = chunkedArray(sourcePayload, CHUNK_SIZE);

            chunkedArray.forEach(byteBuf -> {
                ChunkedMessage chunkedMessage;
                if (id.get() == null) {
                    chunkedMessage = new ChunkedMessage(msg.getSender(), msg.getRecipient(), new byte[byteBuf.readableBytes()], msg.getPayload().length, checksum);
                    id.set(chunkedMessage.getId());
                }
                else {
                    chunkedMessage = ChunkedMessage.createFollowChunk(msg.getSender(), msg.getRecipient(), id.get(), new byte[byteBuf.readableBytes()], sequenceNumber.get());
                }

                byteBuf.readBytes(chunkedMessage.getPayload());
                sequenceNumber.getAndIncrement();
                if (ctx.channel().isWritable()) {
                    ctx.writeAndFlush(chunkedMessage);
                }
                else {
                    System.err.println("Channel is currently not writable!");
                }
            });

            // Send last chunk
            ctx.writeAndFlush(ChunkedMessage.createLastChunk(msg.getSender(), msg.getRecipient(), id.get(), sequenceNumber.get()));
        }
        finally {
            // Release no longer needed ByteBufs
            ReferenceCountUtil.release(sourcePayload);
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
            ApplicationMessage applicationMessage = new ApplicationMessage(msg.getId(), msg.getRecipient(), msg.getSender(), payload, (short) 0);

            ctx.fireChannelRead(applicationMessage);
        }
    }

    /**
     * Creates a list of {@link ByteBuf}s from the given <code>source</code>. The source {@link
     * ByteBuf} is not modified during this process.
     *
     * @param source    the source {@link ByteBuf}
     * @param chunksize the chunk size
     * @return a list of {@link ByteBuf} chunks/slices of the source
     */
    private List<ByteBuf> chunkedArray(ByteBuf source, int chunksize) {
        ArrayList<ByteBuf> buffer = new ArrayList<>();

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
