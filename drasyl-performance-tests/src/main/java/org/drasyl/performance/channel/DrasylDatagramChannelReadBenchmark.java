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
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.channel.DefaultDrasylServerChannelInitializer;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.sodium.SessionPair;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.UdpServerChannelInitializer;
import org.drasyl.handler.remote.UdpServerToDrasylHandler;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.Identity;
import org.drasyl.util.EventLoopGroupUtil;
import org.openjdk.jmh.annotations.Param;

import java.net.InetSocketAddress;
import java.net.PortUnreachableException;

import static io.netty.channel.ChannelOption.IP_TOS;
import static org.drasyl.channel.DrasylServerChannelConfig.ARMING_ENABLED;
import static org.drasyl.channel.DrasylServerChannelConfig.UDP_BIND;
import static org.drasyl.channel.DrasylServerChannelConfig.UDP_BOOTSTRAP;

@SuppressWarnings({
        "NewClassNamingConvention",
        "java:S2142",
        "java:S4507",
        "resource",
        "unchecked",
        "BusyWait"
})
public class DrasylDatagramChannelReadBenchmark extends AbstractChannelReadBenchmark {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 12345;
    private static final Identity WRITER_IDENTITY = Identity.of(-2082598243,
            "18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127",
            "65f20fc3fdcaf569cdcf043f79047723d8856b0169bd4c475ba15ef1b37d27ae18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127");
    private static final Identity RECEIVER_IDENTITY = Identity.of(-2122268831,
            "622d860a23517b0e20e59d8a481db4da2c89649c979d7318bc4ef19828f4663e",
            "fc10ab6bb85c51c453dbfe44c0c29d96d1a365257ad871dea49c29c98f1f8421622d860a23517b0e20e59d8a481db4da2c89649c979d7318bc4ef19828f4663e");
    @Param({ "2" })
    private int writeThreads;
    @Param({ "1024" })
    private int packetSize;
    @Param({ "true", "false" })
    private boolean armingEnabled;
    @Param({ "true", "false" })
    private boolean pseudorandom;
    private EventLoopGroup writeGroup;
    private EventLoopGroup readGroup;
    private EventLoopGroup udpGroup;
    private Channel drasylServerChannel;

    @Override
    protected ChannelGroup setupWriteChannels() throws Exception {
        System.setProperty("org.drasyl.nonce.pseudorandom", Boolean.toString(pseudorandom));

        final ByteBuf payload = Unpooled.wrappedBuffer(new byte[packetSize]);
        RemoteMessage message = ApplicationMessage.of(1, RECEIVER_IDENTITY.getIdentityPublicKey(), WRITER_IDENTITY.getIdentityPublicKey(), WRITER_IDENTITY.getProofOfWork(), payload);
        final UnpooledByteBufAllocator alloc = UnpooledByteBufAllocator.DEFAULT;
        if (armingEnabled) {
            final SessionPair sessionPair = Crypto.INSTANCE.generateSessionKeyPair(RECEIVER_IDENTITY.getKeyAgreementKeyPair(), WRITER_IDENTITY.getKeyAgreementPublicKey());
            message = ((ApplicationMessage) message).arm(alloc, Crypto.INSTANCE, SessionPair.of(sessionPair.getTx(), sessionPair.getRx()));
        }
        final ByteBuf msg = message.encodeMessage(alloc);

        writeGroup = new NioEventLoopGroup(writeThreads);

        final Bootstrap writeBootstrap = new Bootstrap()
                .group(writeGroup)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        ch.pipeline().addLast(new WriteHandler<>(msg.retain()));
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void exceptionCaught(final ChannelHandlerContext ctx,
                                                        final Throwable cause) {
                                if (!(cause instanceof PortUnreachableException)) {
                                    ctx.fireExceptionCaught(cause);
                                }
                            }
                        });
                    }
                });

        final ChannelGroup writeChannels = new DefaultChannelGroup(writeGroup.next());
        for (int i = 0; i < writeThreads; i++) {
            writeChannels.add(writeBootstrap.connect(HOST, PORT).sync().channel());
        }

        return writeChannels;
    }

    @Override
    protected Channel setupReadChannel() throws InterruptedException {
        readGroup = new NioEventLoopGroup(1);
        udpGroup = EventLoopGroupUtil.getBestEventLoopGroup(1);

        final ServerBootstrap drasylServerBootstrap = new ServerBootstrap()
                .group(readGroup)
                .channel(DrasylServerChannel.class)
                .option(UDP_BOOTSTRAP, parent -> new Bootstrap()
                        .option(IP_TOS, 0xB8)
                        .group(udpGroup)
                        .channel(EventLoopGroupUtil.getBestDatagramChannel())
                        .handler(new UdpServerChannelInitializer(parent)))
                .option(UDP_BIND, new InetSocketAddress(HOST, PORT))
                .option(ARMING_ENABLED, armingEnabled)
                .handler(new DefaultDrasylServerChannelInitializer())
                .childHandler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(final ChannelHandlerContext ctx,
                                            final Object msg) {
                        ReferenceCountUtil.release(msg);
                    }
                });
        drasylServerChannel = drasylServerBootstrap
                .bind(RECEIVER_IDENTITY)
                .sync()
                .channel();

        Channel channel;
        while (true) {
            final UdpServer udpServer = drasylServerChannel.pipeline().get(UdpServer.class);
            if (udpServer != null) {
                channel = udpServer.udpChannel();
                if (channel != null) {
                    break;
                }
            }

            Thread.sleep(1000);
        }

        channel.pipeline().addBefore(channel.pipeline().context(UdpServerToDrasylHandler.class).name(), null, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
                if (msg instanceof InetAddressedMessage<?> && ((InetAddressedMessage<?>) msg).content() instanceof ApplicationMessage) {
                    final InetAddressedMessage<ApplicationMessage> application = (InetAddressedMessage<ApplicationMessage>) msg;
                    application.release();
                    receivedMsgs.incrementAndGet();
                }
            }
        });

        return channel;
    }

    @Override
    protected void teardownChannel() throws InterruptedException {
        drasylServerChannel.close().sync();
        udpGroup.shutdownGracefully().await();
        writeGroup.shutdownGracefully().await();
        readGroup.shutdownGracefully().await();
    }
}
