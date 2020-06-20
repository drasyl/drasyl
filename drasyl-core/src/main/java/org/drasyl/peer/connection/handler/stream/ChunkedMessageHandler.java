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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.crypto.Hashing;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.handler.SimpleChannelDuplexHandler;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ChunkedMessage;
import org.drasyl.peer.connection.message.RelayableMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Allows sending bigger messages in chunks.
 */
public class ChunkedMessageHandler extends SimpleChannelDuplexHandler<ChunkedMessage, ApplicationMessage> {
    public static final String CHUNK_HANDLER = "chunkHandler";
    public static final int CHUNK_SIZE = 32768; // 32768 := 2^15 bytes for payload and 2^15 bytes for meta-data
    private static final Logger LOG = LoggerFactory.getLogger(ChunkedMessageHandler.class);
    private final int maxContentLength;
    // TODO: Delete chunks if after n seconds not all chunks have arrived
    private final Multimap<String, ChunkedMessage> chunks;
    private final CompressedPublicKey myIdentity;

    public ChunkedMessageHandler(int maxContentLength, CompressedPublicKey myIdentity) {
        super(true, false, false);
        this.maxContentLength = maxContentLength;
        chunks = HashMultimap.create();
        this.myIdentity = myIdentity;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx,
                                ChunkedMessage msg) throws Exception {
        if (!msg.getRecipient().equals(myIdentity)) {
            // Only relaying...
            ReferenceCountUtil.retain(msg);
            ctx.fireChannelRead(msg);
            return;
        }

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
            if (LOG.isWarnEnabled()) {
                LOG.warn("[{}]: Payload is bigger than max content length. Message with id `{}` was not sent.", ctx.channel().id().asShortText(), msg.getId());
            }

            ReferenceCountUtil.release(msg);
            promise.setFailure(new IllegalArgumentException("Payload was to big."));
            return;
        }

        if (msg.getPayload().length > CHUNK_SIZE) {
            ChunkedMessageInput chunkedMessageInput = new ChunkedMessageInput(msg, CHUNK_SIZE);
            ctx.writeAndFlush(chunkedMessageInput, promise);
        }
        else {
            // Skip
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
        String checksum = Hashing.murmur3_128Hex(payload);

        if (!checksum.equals(chunkedMessageChecksum)) {
            ctx.writeAndFlush(new StatusMessage(StatusMessage.Code.STATUS_PRECONDITION_FAILED, msg.getId()));

            if (LOG.isDebugEnabled()) {
                LOG.debug("[{}]: Dropped chunked message `{}` because checksum was invalid", ctx.channel().id().asShortText(), msg);
            }
        }
        else {
            RelayableMessage applicationMessage = new ApplicationMessage(msg.getId(), msg.getRecipient(), msg.getSender(), payload, (short) 0);

            ctx.fireChannelRead(applicationMessage);
        }
    }
}
