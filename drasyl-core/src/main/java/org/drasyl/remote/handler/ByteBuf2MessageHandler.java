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

import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.remote.protocol.IntermediateEnvelope;

import java.util.concurrent.CompletableFuture;

/**
 * Handler that converts a given {@link ByteBuf} to a {@link IntermediateEnvelope} or {@link
 * IntermediateEnvelope}.
 */
public class ByteBuf2MessageHandler extends SimpleInboundHandler<ByteBuf, Address> {
    public static final ByteBuf2MessageHandler INSTANCE = new ByteBuf2MessageHandler();
    public static final String BYTE_BUF_2_MESSAGE_HANDLER = "BYTE_BUF_2_MESSAGE_HANDLER";

    private ByteBuf2MessageHandler() {
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final ByteBuf byteBuf,
                               final CompletableFuture<Void> future) {
        final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(byteBuf);
        ctx.fireRead(sender, envelope, future);
    }
}
