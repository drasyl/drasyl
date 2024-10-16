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
package org.drasyl.cli.sdon.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.tun.Tun4Packet;
import org.drasyl.channel.tun.TunChannel;

import static org.drasyl.cli.sdon.handler.NetworkConfigHandler.TUN_CHANNEL_KEY;

public class DrasylToTunHandler extends SimpleChannelInboundHandler<Tun4Packet> {
    public DrasylToTunHandler() {
        super(false);
    }

    @SuppressWarnings("java:S1905")
    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final Tun4Packet packet) {
        final TunChannel tun = ctx.channel().parent().attr(TUN_CHANNEL_KEY).get();
        if (tun != null) {
            tun.write(packet);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        final TunChannel tun = ctx.channel().parent().attr(TUN_CHANNEL_KEY).get();
        if (tun != null) {
            tun.flush();
        }
        ctx.fireChannelReadComplete();
    }
}
