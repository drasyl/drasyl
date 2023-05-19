/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.remote.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.util.internal.UnstableApi;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Encodes {@link ByteBuf}s to {@link InetAddressedMessage}s and vice versa.
 */
@UnstableApi
@Sharable
public class ByteBufCodec extends MessageToMessageCodec<ByteBuf, InetAddressedMessage<ByteBuf>> {
    @Override
    public boolean acceptOutboundMessage(final Object msg) {
        return msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof ByteBuf;
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final InetAddressedMessage<ByteBuf> msg,
                          final List<Object> out) {
        final ByteBuf buf = msg.content();
        out.add(buf.retain());
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf buf,
                          final List<Object> out) {
        final InetSocketAddress sender = (InetSocketAddress) ctx.channel().remoteAddress();
        final InetAddressedMessage<ByteBuf> msg = new InetAddressedMessage<>(buf.retain(), null, sender);
        out.add(msg);
    }
}
