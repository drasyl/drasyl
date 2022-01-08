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

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.tun.Tun4Packet;
import org.drasyl.crypto.HexUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Routes inbound messages to the given tun-based channel.
 * <p>
 * This handler has to be placed in a {@link org.drasyl.channel.DrasylChannel}.
 */
public class DrasylToTunHandler extends SimpleChannelInboundHandler<Tun4Packet> {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylToTunHandler.class);
    private final Channel tun;

    public DrasylToTunHandler(final Channel tun) {
        this.tun = requireNonNull(tun);
    }

    @SuppressWarnings("java:S1905")
    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final Tun4Packet packet) {
        LOG.trace("Got packet `{}` from peer `{}`. Pass to TUN device `{}`", () -> packet, ctx.channel()::remoteAddress, tun::localAddress);
        LOG.trace("https://hpd.gasmi.net/?data={}&force=ipv4", () -> HexUtil.bytesToHex(ByteBufUtil.getBytes(packet.content())));
        tun.writeAndFlush(packet.retain()).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                ctx.pipeline().fireExceptionCaught(f.cause());
            }
        });
    }
}
