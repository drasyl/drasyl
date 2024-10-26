/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.tun.Tun4Packet;
import org.drasyl.channel.tun.TunChannel;
import org.drasyl.crypto.HexUtil;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static org.drasyl.cli.sdon.config.TunPolicy.TUN_CHANNEL_KEY;

/**
 * Passes TUN messages from the {@link io.netty.channel.socket.DatagramChannel} to {@link TunChannel}.
 */
public class UdpServerToTunHandler extends ChannelDuplexHandler {
    public static final int MAGIC_NUMBER = 899_812_335;
    private static final Logger LOG = LoggerFactory.getLogger(UdpServerToTunHandler.class);
    private final DrasylServerChannel parent;

    public UdpServerToTunHandler(final DrasylServerChannel parent) {
        this.parent = parent;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx,
                            final Object msg) {
        if (msg instanceof InetAddressedMessage<?> && ((InetAddressedMessage<?>) msg).content() instanceof ApplicationMessage) {
            final ApplicationMessage appMsg = ((InetAddressedMessage<ApplicationMessage>) msg).content();
            final ByteBuf payload = appMsg.getPayload();
            if (payload.readableBytes() >= Integer.BYTES) {
                payload.markReaderIndex();
                if (payload.readInt() != MAGIC_NUMBER) {
                    payload.resetReaderIndex();
                    ctx.fireChannelRead(msg);
                    return;
                }

                final Tun4Packet packet = new Tun4Packet(payload.slice());
                LOG.debug("Got packet from drasyl `{}`.", () -> packet);
                LOG.trace("https://hpd.gasmi.net/?data={}&force=ipv4", () -> HexUtil.bytesToHex(ByteBufUtil.getBytes(packet.content())));

                final TunChannel tunChannel = parent.attr(TUN_CHANNEL_KEY).get();
                if (tunChannel != null) {
                    final ChannelFuture channelFuture = tunChannel.writeAndFlush(packet);
                    channelFuture.addListener(FIRE_EXCEPTION_ON_FAILURE);
                }
                else {
                    appMsg.release();
                }
            }
            else {
                ctx.fireChannelRead(msg);
            }
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }
}
