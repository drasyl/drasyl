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

import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.handler.codec.MessageToMessageEncoder;
import org.drasyl.remote.protocol.IntermediateEnvelope;

import java.io.IOException;
import java.util.List;

/**
 * Handler that converts a given {@link IntermediateEnvelope} to a {@link ByteBuf}.
 */
@SuppressWarnings("java:S110")
@Stateless
public final class Message2ByteBufHandler extends MessageToMessageEncoder<IntermediateEnvelope<MessageLite>, InetSocketAddressWrapper> {
    public static final Message2ByteBufHandler INSTANCE = new Message2ByteBufHandler();
    public static final String MESSAGE_2_BYTE_BUF_HANDLER = "MESSAGE_2_BYTE_BUF_HANDLER";

    private Message2ByteBufHandler() {
        // singleton
    }

    @Override
    protected void encode(final HandlerContext ctx,
                          final InetSocketAddressWrapper recipient,
                          final IntermediateEnvelope<MessageLite> msg,
                          final List<Object> out) throws IOException {
        out.add(msg.getOrBuildByteBuf().retain());
    }
}
