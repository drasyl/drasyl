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
import org.drasyl.pipeline.handler.codec.MessageToMessageCodec;
import org.drasyl.remote.protocol.RemoteEnvelope;

import java.util.List;

/**
 * This codec converts {@link RemoteEnvelope} to {@link ByteBuf} an vice vera.
 */
@SuppressWarnings("java:S110")
@Stateless
public final class RemoteEnvelopeToByteBufCodec extends MessageToMessageCodec<ByteBuf, RemoteEnvelope<? extends MessageLite>, InetSocketAddressWrapper> {
    public static final RemoteEnvelopeToByteBufCodec INSTANCE = new RemoteEnvelopeToByteBufCodec();

    private RemoteEnvelopeToByteBufCodec() {
        // singleton
    }

    @Override
    protected void decode(final HandlerContext ctx,
                          final InetSocketAddressWrapper sender,
                          final ByteBuf msg,
                          final List<Object> out) throws Exception {
        out.add(RemoteEnvelope.of(msg.retain()));
    }

    @Override
    protected void encode(final HandlerContext ctx,
                          final InetSocketAddressWrapper recipient,
                          final RemoteEnvelope<? extends MessageLite> msg,
                          final List<Object> out) throws Exception {
        out.add(msg.getOrBuildByteBuf().retain());
    }
}
