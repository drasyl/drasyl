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
package org.drasyl.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.remote.protocol.ArmedMessage;
import org.drasyl.remote.protocol.BodyChunkMessage;
import org.drasyl.remote.protocol.HeadChunkMessage;
import org.drasyl.remote.protocol.PartialReadMessage;
import org.drasyl.remote.protocol.RemoteMessage;
import org.drasyl.remote.protocol.UnarmedMessage;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * This codec converts {@link RemoteMessage}s to {@link ByteBuf}s an vice vera.
 */
@SuppressWarnings("java:S110")
@ChannelHandler.Sharable
public final class RemoteMessageToByteBufCodec extends MessageToMessageCodec<DatagramPacket, AddressedRemoteMessage> {
    public static final RemoteMessageToByteBufCodec INSTANCE = new RemoteMessageToByteBufCodec();

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final AddressedRemoteMessage msg,
                          final List<Object> out) throws Exception {
        final ByteBuf buffer = ctx.alloc().ioBuffer();
        msg.content().writeTo(buffer);
        out.add(new DatagramPacket(buffer, (InetSocketAddress) msg.recipient()));
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final DatagramPacket msg,
                          final List<Object> out) throws Exception {
        final PartialReadMessage content = PartialReadMessage.of(msg.content().retain());

        if (content instanceof HeadChunkMessage) {
            out.add(new AddressedHeadChunkMessage((HeadChunkMessage) content, null, msg.sender()));
        }
        else if (content instanceof BodyChunkMessage) {
            out.add(new AddressedBodyChunkMessage((BodyChunkMessage) content, null, msg.sender()));
        }
        else if (content instanceof ArmedMessage) {
            out.add(new AddressedArmedMessage((ArmedMessage) content, null, msg.sender()));
        }
        else {
            out.add(new AddressedUnarmedMessage((UnarmedMessage) content, null, msg.sender()));
        }
    }
}
