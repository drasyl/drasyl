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
package org.drasyl.cli.tun.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.Tun4Packet;
import io.netty.channel.socket.TunPacket;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.List;

/**
 * This codec converts {@link TunPacket}s to {@link ByteBuf}s and vice versa.
 */
public class TunPacketCodec extends MessageToMessageCodec<ByteBuf, TunPacket> {
    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final TunPacket packet,
                          final List<Object> out) throws Exception {
        out.add(packet.content().retain());
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf byteBuf,
                          final List<Object> out) throws Exception {
        out.add(new Tun4Packet(byteBuf).retain());
    }
}
