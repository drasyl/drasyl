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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.handler.codec.MessageToMessageCodec;
import org.drasyl.remote.protocol.PartialReadMessage;
import org.drasyl.remote.protocol.RemoteMessage;

import java.util.List;

/**
 * This codec converts {@link RemoteMessage}s to {@link ByteBuf}s an vice vera.
 */
@SuppressWarnings("java:S110")
@ChannelHandler.Sharable
@Stateless
public final class RemoteMessageToByteBufCodec extends MessageToMessageCodec<ByteBuf, RemoteMessage, InetSocketAddressWrapper> {
    public static final RemoteMessageToByteBufCodec INSTANCE = new RemoteMessageToByteBufCodec();

    private RemoteMessageToByteBufCodec() {
        // singleton
    }

    @Override
    protected void decode(final HandlerContext ctx,
                          final InetSocketAddressWrapper sender,
                          final ByteBuf msg,
                          final List<Object> out) throws Exception {
        out.add(PartialReadMessage.of(msg.retain()));
    }

    @Override
    protected void encode(final HandlerContext ctx,
                          final InetSocketAddressWrapper recipient,
                          final RemoteMessage msg,
                          final List<Object> out) throws Exception {
        final ByteBuf buffer = ctx.alloc();
        msg.writeTo(buffer);
        out.add(buffer);
    }
}
