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
package org.drasyl.handler.remote;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.DatagramChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.util.internal.UnstableApi;

import static java.util.Objects.requireNonNull;

@UnstableApi
public class UdpMulticastServerChannelInitializer extends ChannelInitializer<DatagramChannel> {
    private final ChannelHandlerContext drasylCtx;

    public UdpMulticastServerChannelInitializer(final ChannelHandlerContext drasylCtx) {
        this.drasylCtx = requireNonNull(drasylCtx);
    }

    @Override
    protected void initChannel(final DatagramChannel ch) throws Exception {
        final ChannelPipeline p = ch.pipeline();

        p.addLast(new DatagramCodec());
        p.addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
                final UdpMulticastServer multicastServer = (UdpMulticastServer) drasylCtx.handler();
                multicastServer.multicastRead((InetAddressedMessage<ByteBuf>) msg);
            }

            @Override
            public void channelReadComplete(final ChannelHandlerContext ctx) {
                final UdpMulticastServer multicastServer = (UdpMulticastServer) drasylCtx.handler();
                multicastServer.multicastReadComplete();

                ctx.fireChannelReadComplete();
            }
        });
    }
}
