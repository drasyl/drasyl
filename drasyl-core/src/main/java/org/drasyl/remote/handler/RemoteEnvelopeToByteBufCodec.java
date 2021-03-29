/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
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
