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

package org.drasyl.remote.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.remote.message.RemoteMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.LoggingUtil.sanitizeLogArg;

/**
 * Handler that converts a given {@link ByteBuf} to a {@link RemoteMessage}.
 */
public class ByteBuf2MessageHandler extends SimpleInboundHandler<ByteBuf, Address> {
    public static final ByteBuf2MessageHandler INSTANCE = new ByteBuf2MessageHandler();
    public static final String BYTE_BUF_2_MESSAGE_HANDLER = "BYTE_BUF_2_MESSAGE_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(ByteBuf2MessageHandler.class);

    private ByteBuf2MessageHandler() {
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final ByteBuf byteBuf,
                               final CompletableFuture<Void> future) {
        try {
            final InputStream inputStream = new ByteBufInputStream(byteBuf);
            final RemoteMessage message = requireNonNull(JACKSON_READER.readValue(inputStream, RemoteMessage.class));

            ctx.fireRead(sender, message, future);
        }
        catch (final IOException e) {
            LOG.warn("Unable to deserialize '{}': {}", sanitizeLogArg(byteBuf), e.getMessage());
            future.completeExceptionally(new Exception("Message could not be deserialized.", e));
        }
        finally {
            byteBuf.release();
        }
    }
}
