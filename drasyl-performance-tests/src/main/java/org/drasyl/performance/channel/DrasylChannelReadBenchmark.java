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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.AbstractBenchmark;
import org.drasyl.channel.DefaultDrasylServerChannelInitializer;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.sodium.SessionPair;
import org.drasyl.handler.remote.UdpServerChannelInitializer;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.InvalidMessageFormatException;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.Identity;
import org.drasyl.util.EventLoopGroupUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.util.concurrent.atomic.AtomicLong;

import static io.netty.channel.ChannelOption.IP_TOS;
import static org.drasyl.channel.DrasylServerChannelConfig.ARMING_ENABLED;
import static org.drasyl.channel.DrasylServerChannelConfig.UDP_BIND;
import static org.drasyl.channel.DrasylServerChannelConfig.UDP_BOOTSTRAP;

public class DrasylChannelReadBenchmark extends AbstractBenchmark {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 12345;
    private static final Identity WRITER_IDENTITY = Identity.of(-2082598243,
            "18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127",
            "65f20fc3fdcaf569cdcf043f79047723d8856b0169bd4c475ba15ef1b37d27ae18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127");
    private static final Identity RECEIVER_IDENTITY = Identity.of(-2122268831,
            "622d860a23517b0e20e59d8a481db4da2c89649c979d7318bc4ef19828f4663e",
            "fc10ab6bb85c51c453dbfe44c0c29d96d1a365257ad871dea49c29c98f1f8421622d860a23517b0e20e59d8a481db4da2c89649c979d7318bc4ef19828f4663e");
    @Param({ "1" })
    private int writeThreads;
    @Param({ "1024" })
    private int packetSize;
    @Param({ "true", "false" })
    private boolean armingEnabled;
    @Param({ "true", "false" })
    private boolean pseudorandom;
    private EventLoopGroup group;
    private EventLoopGroup udpGroup;
    private ByteBuf data;
    private boolean doWrite = true;
    private ChannelGroup writeChannels;
    private final AtomicLong receivedMsgs = new AtomicLong();
    private Channel readerChannel;

    @SuppressWarnings("unchecked")
    @Setup
    public void setup() throws CryptoException, InvalidMessageFormatException {
        System.setProperty("org.drasyl.nonce.pseudorandom", Boolean.toString(pseudorandom));

        group = new NioEventLoopGroup(writeThreads + 1);
        udpGroup = EventLoopGroupUtil.getBestEventLoopGroup(1);

        final ByteBuf payload = Unpooled.wrappedBuffer(new byte[packetSize]);
        RemoteMessage message = ApplicationMessage.of(1, RECEIVER_IDENTITY.getIdentityPublicKey(), WRITER_IDENTITY.getIdentityPublicKey(), WRITER_IDENTITY.getProofOfWork(), payload);
        final UnpooledByteBufAllocator alloc = UnpooledByteBufAllocator.DEFAULT;
        if (armingEnabled) {
            final SessionPair sessionPair = Crypto.INSTANCE.generateSessionKeyPair(RECEIVER_IDENTITY.getKeyAgreementKeyPair(), WRITER_IDENTITY.getKeyAgreementPublicKey());
            message = ((ApplicationMessage) message).arm(alloc, Crypto.INSTANCE, SessionPair.of(sessionPair.getTx(), sessionPair.getRx()));
        }
        data = message.encodeMessage(alloc);

        try {
            final Bootstrap writeBootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new MyChannelDuplexHandler());

            writeChannels = new DefaultChannelGroup(group.next());
            for (int i = 0; i < writeThreads; i++) {
                writeChannels.add(writeBootstrap.connect(HOST, PORT).sync().channel());
            }

            final ServerBootstrap readerBootstrap = new ServerBootstrap()
                    .group(group, udpGroup)
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
                            receivedMsgs.incrementAndGet();
                        }
                    });
            readerChannel = readerBootstrap
                    .bind(RECEIVER_IDENTITY)
                    .sync()
                    .channel();
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    @TearDown
    public void teardown() {
        try {
            doWrite = false;
            data.release();
            readerChannel.close().await();
            writeChannels.close().await();
            udpGroup.shutdownGracefully().await();
            group.shutdownGracefully().await();
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void read() {
        while (receivedMsgs.get() < 1) {
            // do nothing
        }
        receivedMsgs.getAndDecrement();
    }

    @Sharable
    private class MyChannelDuplexHandler extends ChannelDuplexHandler {
        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            ctx.fireChannelActive();
            scheduleWriteTask(ctx);
        }

        private void scheduleWriteTask(final ChannelHandlerContext ctx) {
            if (ctx.channel().isActive()) {
                ctx.executor().execute(() -> {
                    while (doWrite && ctx.channel().isWritable()) {
                        ctx.write(data.retain());
                    }
                    if (!doWrite) {
                        ctx.close();
                    }
                    ctx.flush();
                });
            }
        }

        @Override
        public void channelWritabilityChanged(final ChannelHandlerContext ctx) {
            if (ctx.channel().isWritable()) {
                scheduleWriteTask(ctx);
            }

            ctx.fireChannelWritabilityChanged();
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx,
                                    final Throwable cause) {
            if (!(cause instanceof PortUnreachableException)) {
                cause.printStackTrace();
            }
        }
    }
}
