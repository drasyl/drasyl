/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.performance.channel;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import org.drasyl.channel.DefaultDrasylServerChannelInitializer;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.UdpServerChannelInitializer;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.EventLoopGroupUtil;
import org.openjdk.jmh.annotations.Param;

import java.net.InetSocketAddress;
import java.util.function.Function;

import static io.netty.channel.ChannelOption.IP_TOS;
import static org.drasyl.channel.DrasylServerChannelConfig.ARMING_ENABLED;
import static org.drasyl.channel.DrasylServerChannelConfig.UDP_BOOTSTRAP;

public class DrasylDatagramChannelWriteBenchmark extends AbstractChannelWriteBenchmark {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 12345;
    private static final IdentityPublicKey RECIPIENT = IdentityPublicKey.of("c909a27d9ec0127c57142c3e1547ba9f82bc605277380b2a8fc0fabafe2be4c9");
    private static final Identity SENDER = Identity.of(-2082598243,
            "18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127",
            "65f20fc3fdcaf569cdcf043f79047723d8856b0169bd4c475ba15ef1b37d27ae18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127");
    private static final int DRASYL_OVERHEAD = 104;
    @Param({ "1024" })
    private int packetSize;
    @Param({ "true", "false" })
    private boolean armingEnabled;
    @Param({ "true", "false" })
    private boolean pseudorandom;
    private EventLoopGroup group;
    private EventLoopGroup udpGroup;
    private Channel drasylServerChannel;

    @Override
    protected Channel setupChannel() throws Exception {
        System.setProperty("org.drasyl.nonce.pseudorandom", Boolean.toString(pseudorandom));

        group = new NioEventLoopGroup();
        udpGroup = EventLoopGroupUtil.getBestEventLoopGroup(1);
        drasylServerChannel = new ServerBootstrap()
                .group(group)
                .channel(DrasylServerChannel.class)
                .option(UDP_BOOTSTRAP, parent -> new Bootstrap()
                        .option(IP_TOS, 0xB8)
                        .group(udpGroup)
                        .channel(EventLoopGroupUtil.getBestDatagramChannel())
                        .handler(new UdpServerChannelInitializer(parent)))
                .option(ARMING_ENABLED, armingEnabled)
                .handler(new DefaultDrasylServerChannelInitializer())
                .childHandler(new ChannelInboundHandlerAdapter())
                .bind(SENDER)
                .sync()
                .channel();

        while (true) {
            final UdpServer udpServer = drasylServerChannel.pipeline().get(UdpServer.class);
            if (udpServer != null) {
                final DatagramChannel channel = udpServer.udpChannel();
                if (channel != null) {
                    return channel;
                }
            }

            Thread.sleep(1000);
        }
    }

    @Override
    protected Object buildMsg() {
        final InetSocketAddress targetAddress = new InetSocketAddress(HOST, PORT);
        final ByteBuf data = Unpooled.wrappedBuffer(new byte[packetSize]);
        final ApplicationMessage appMsg = ApplicationMessage.of(1, RECIPIENT, SENDER.getIdentityPublicKey(), SENDER.getProofOfWork(), data.retainedDuplicate());
        return new InetAddressedMessage<>(appMsg, targetAddress);
    }

    @Override
    protected Function<Object, Object> getMsgDuplicator() {
        return new Function<Object, Object>() {
            @Override
            public Object apply(final Object o) {
                final InetAddressedMessage<ApplicationMessage> oldInetMsg = (InetAddressedMessage<ApplicationMessage>) o;
                final ApplicationMessage oldAppMsg = oldInetMsg.content();
                final ApplicationMessage appMsg = ApplicationMessage.of(1, (IdentityPublicKey) oldAppMsg.getRecipient(), (IdentityPublicKey) oldAppMsg.getSender(), oldAppMsg.getProofOfWork(), oldAppMsg.getPayload().retainedDuplicate());
                return oldInetMsg.replace(appMsg);
            }
        };
    }

    @Override
    protected void teardownChannel() throws Exception {
        drasylServerChannel.close().await();
        group.shutdownGracefully().await();
        udpGroup.shutdownGracefully().await();
    }
}
