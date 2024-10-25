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
package org.drasyl.cli.sdon;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.DatagramChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.tun.Tun4Packet;
import org.drasyl.channel.tun.TunChannel;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.cli.ChannelOptionsDefaultProvider;
import org.drasyl.cli.sdon.channel.SdoNodeChannelInitializer;
import org.drasyl.cli.sdon.channel.SdoNodeChildChannelInitializer;
import org.drasyl.handler.remote.UdpServerChannelInitializer;
import org.drasyl.handler.remote.UdpServerToDrasylHandler;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static org.drasyl.cli.sdon.handler.NetworkConfigHandler.TUN_CHANNEL_KEY;
import static org.drasyl.cli.tun.handler.TunPacketCodec.MAGIC_NUMBER;

@Command(
        name = "node",
        header = "",
        defaultValueProvider = ChannelOptionsDefaultProvider.class
)
public class SdonNodeCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(SdonNodeCommand.class);
    @Option(
            names = { "-c", "--controller" },
            description = "Controller to connect to.",
            paramLabel = "<public-key>",
            required = true
    )
    private IdentityPublicKey controller;
    @Option(
            names = { "--tag" }
    )
    private String[] tags;

    @Override
    protected ChannelHandler getServerChannelInitializer(final Worm<Integer> exitCode) {
        return new SdoNodeChannelInitializer(onlineTimeoutMillis, out, err, exitCode, controller, tags);
    }

    @Override
    protected ChannelHandler getChildChannelInitializer(final Worm<Integer> exitCode) {
        return new SdoNodeChildChannelInitializer(out, err, exitCode, controller);
    }

    @Override
    protected ChannelHandler getUdpChannelInitializer(final DrasylServerChannel parent) {
        return new UdpServerChannelInitializer(parent) {
            @Override
            protected void initChannel(final DatagramChannel ch) {
                super.initChannel(ch);

                final ChannelPipeline p = ch.pipeline();

                ch.pipeline().addBefore(p.context(UdpServerToDrasylHandler.class).name(), null, new ChannelDuplexHandler() {
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
                                final TunChannel tunChannel = ctx.channel().attr(TUN_CHANNEL_KEY).get();
                                if (tunChannel != null) {
                                    tunChannel.writeAndFlush(packet).addListener(FIRE_EXCEPTION_ON_FAILURE);
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
                });
            }
        };
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}

