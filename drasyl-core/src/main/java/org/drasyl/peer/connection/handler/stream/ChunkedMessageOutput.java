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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.crypto.Hashing;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ChunkedMessage;
import org.drasyl.peer.connection.message.MessageId;
import org.drasyl.peer.connection.message.StatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for merging incoming {@link ChunkedMessage}s into one {@link
 * ApplicationMessage}.
 */
@SuppressWarnings({ "java:S107" })
public class ChunkedMessageOutput {
    private static final Logger LOG = LoggerFactory.getLogger(ChunkedMessageOutput.class);
    private final ChannelHandlerContext ctx;
    private final CompressedPublicKey sender;
    private final ProofOfWork proofOfWork;
    private final CompressedPublicKey recipient;
    private final Runnable removeAction;
    private final ByteBuf payload;
    private final int contentLength;
    private final int maxContentLength;
    private final String checksum;
    private final MessageId msgID;
    private int progress;

    /**
     * Generates a new ChunkedMessageOutput with combines multiple {@link ChunkedMessage}s into one
     * {@link ApplicationMessage}.
     *
     * @param ctx              the channel handler context
     * @param sender           the sender of the chunks
     * @param proofOfWork      the sender's proof of work
     * @param recipient        the recipient of the chunks
     * @param contentLength    the total length of the resulting {@link ApplicationMessage} payload
     * @param checksum         the checksum of the resulting payload
     * @param msgID            the message id of all chunks and the resulting {@link
     *                         ApplicationMessage}
     * @param maxContentLength the max content length on this node
     * @param removeAction     the remove action to remove this class from the {@link
     *                         ChunkedMessageHandler#chunks}
     * @param timeout          the timeout for receiving all chunks, after this timeout the {@link
     *                         #removeAction} is called
     */
    public ChunkedMessageOutput(final ChannelHandlerContext ctx,
                                final CompressedPublicKey sender,
                                final ProofOfWork proofOfWork,
                                final CompressedPublicKey recipient,
                                final int contentLength,
                                final String checksum,
                                final MessageId msgID,
                                final int maxContentLength,
                                final Runnable removeAction,
                                final long timeout) {
        this(ctx, sender, proofOfWork, recipient, contentLength, checksum, msgID, maxContentLength, Unpooled.buffer(), 0, removeAction);
        this.ctx.executor().schedule(() -> {
            this.ctx.writeAndFlush(new StatusMessage(StatusMessage.Code.STATUS_REQUEST_TIMEOUT, this.msgID));
            removeAction.run();
            payload.release();

            logDebug("Dropped chunked message `{}` because timeout has expired.", ctx, msgID);
        }, timeout, TimeUnit.MILLISECONDS);
    }

    ChunkedMessageOutput(final ChannelHandlerContext ctx,
                         final CompressedPublicKey sender,
                         final ProofOfWork proofOfWork,
                         final CompressedPublicKey recipient,
                         final int contentLength,
                         final String checksum,
                         final MessageId msgID,
                         final int maxContentLength,
                         final ByteBuf payload,
                         final int progress,
                         final Runnable removeAction) {
        this.ctx = ctx;
        this.sender = sender;
        this.proofOfWork = proofOfWork;
        this.recipient = recipient;
        this.removeAction = removeAction;
        this.payload = payload;
        this.contentLength = contentLength;
        this.maxContentLength = maxContentLength;
        this.checksum = checksum;
        this.msgID = msgID;
        this.progress = progress;
    }

    /**
     * Adds a chunk to the {@link ByteBuf payload}.
     *
     * @param chunk the chunk
     */
    public synchronized void addChunk(final ChunkedMessage chunk) {
        try {
            final int length = chunk.getPayload().length;
            if ((length + progress) > maxContentLength || (length + progress) > contentLength) {
                ctx.writeAndFlush(new StatusMessage(StatusMessage.Code.STATUS_PAYLOAD_TOO_LARGE, msgID));

                // Release resources on invalid chunk
                payload.release();
                removeAction.run();

                logDebug("Dropped chunked message `{}` because payload is bigger ({} bytes) than the allowed content length.", ctx, (length + progress));

                return;
            }

            if (length != 0) {
                payload.writeBytes(chunk.payloadAsByteBuf(), 0, length);
                progress += length;
            }
            else {
                // truncated zeros
                payload.capacity(progress);
                if (!checksum.equals(Hashing.murmur3x64Hex(payload.array()))) {
                    ctx.writeAndFlush(new StatusMessage(StatusMessage.Code.STATUS_PRECONDITION_FAILED, msgID));
                    logDebug("Dropped chunked message `{}` because checksum was invalid", ctx, msgID);
                }
                else {
                    try {
                        ctx.fireChannelRead(new ApplicationMessage(msgID, sender, proofOfWork, recipient, payload.array(), (short) 0));
                    }
                    finally {
                        payload.release();
                        removeAction.run();
                    }
                }
            }
        }
        finally {
            ReferenceCountUtil.release(chunk);
        }
    }

    private void logDebug(final String format,
                          final ChannelHandlerContext ctx,
                          final Object... arguments) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[" + ctx.channel().id().asShortText() + "]:" + format, arguments); // NOSONAR
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ChunkedMessageOutput that = (ChunkedMessageOutput) o;
        return Objects.equals(msgID, that.msgID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msgID);
    }
}