/*
 * Copyright (c) 2020-2021.
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
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.handler.codec.MessageToMessageDecoder;
import org.drasyl.remote.protocol.IntermediateEnvelope;

import java.io.IOException;
import java.util.List;

/**
 * Handler that converts a given {@link ByteBuf} to a {@link IntermediateEnvelope}.
 */
@SuppressWarnings("java:S110")
@Stateless
public final class ByteBuf2MessageHandler extends MessageToMessageDecoder<ByteBuf, Address> {
    public static final ByteBuf2MessageHandler INSTANCE = new ByteBuf2MessageHandler();
    public static final String BYTE_BUF_2_MESSAGE_HANDLER = "BYTE_BUF_2_MESSAGE_HANDLER";

    private ByteBuf2MessageHandler() {
        // singleton
    }

    @Override
    protected void decode(final HandlerContext ctx,
                          final Address recipient,
                          final ByteBuf msg,
                          final List<Object> out) throws IOException {
        out.add(IntermediateEnvelope.of(msg.retain()));
    }
}
