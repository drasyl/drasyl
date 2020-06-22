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
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ChunkedMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({ "java:S107" })
public class ChunkedMessageOutput {
    private static final Logger LOG = LoggerFactory.getLogger(ChunkedMessageOutput.class);
    private final ChannelHandlerContext ctx;
    private final CompressedPublicKey sender;
    private final CompressedPublicKey recipient;
    private final Runnable removeAction;
    private final ByteBuf payload;
    private final int contentLength;
    private final int maxContentLength;
    private final String checksum;
    private final String msgID;
    private int progress;

    public ChunkedMessageOutput(ChannelHandlerContext ctx,
                                CompressedPublicKey sender,
                                CompressedPublicKey recipient,
                                int contentLength,
                                String checksum,
                                String msgID,
                                int maxContentLength,
                                Runnable removeAction,
                                long timeout) {
        this.ctx = ctx;
        this.sender = sender;
        this.recipient = recipient;
        this.contentLength = contentLength;
        this.checksum = checksum;
        this.msgID = msgID;
        this.maxContentLength = maxContentLength;
        this.payload = Unpooled.buffer();
        this.removeAction = removeAction;
        this.ctx.executor().schedule(() -> {
            this.ctx.writeAndFlush(new StatusMessage(StatusMessage.Code.STATUS_REQUEST_TIMEOUT, this.msgID));
            removeAction.run();
            ReferenceCountUtil.release(payload);
        }, timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Adds a chunk to the {@link ByteBuf payload}.
     *
     * @param chunk the chunk
     */
    public synchronized void addChunk(ChunkedMessage chunk) {
        try {
            int length = chunk.getPayload().length;
            if ((length + progress) > maxContentLength || (length + progress) > contentLength) {
                ctx.writeAndFlush(new StatusMessage(StatusMessage.Code.STATUS_PAYLOAD_TOO_LARGE, msgID));

                logDebug("[{}]: Dropped chunked message `{}` because payload is bigger ({} bytes) than the allowed content length.", ctx.channel().id().asShortText(), (length + progress));
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
                    logDebug("[{}]: Dropped chunked message `{}` because checksum was invalid", ctx.channel().id().asShortText(), msgID);
                }
                else {
                    try {
                        ctx.fireChannelRead(new ApplicationMessage(msgID, sender, recipient, payload.array(), (short) 0));
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

    private void logDebug(String format, Object... arguments) {
        if (LOG.isDebugEnabled()) {
            LOG.debug(format, arguments);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ChunkedMessageOutput that = (ChunkedMessageOutput) o;
        return Objects.equals(msgID, that.msgID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msgID);
    }
}
