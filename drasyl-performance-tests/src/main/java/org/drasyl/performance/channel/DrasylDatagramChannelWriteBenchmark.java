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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.AbstractBenchmark;
import org.drasyl.channel.DefaultDrasylServerChannelInitializer;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.UdpServerChannelInitializer;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.EventLoopGroupUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.net.InetSocketAddress;

import static io.netty.channel.ChannelOption.IP_TOS;
import static io.netty.channel.ChannelOption.WRITE_BUFFER_WATER_MARK;
import static org.drasyl.channel.DrasylServerChannelConfig.ARMING_ENABLED;
import static org.drasyl.channel.DrasylServerChannelConfig.UDP_BOOTSTRAP;

public class DrasylDatagramChannelWriteBenchmark extends AbstractBenchmark {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 12345;
    private static final IdentityPublicKey RECIPIENT = IdentityPublicKey.of("c909a27d9ec0127c57142c3e1547ba9f82bc605277380b2a8fc0fabafe2be4c9");
    private static final Identity SENDER = Identity.of(-2082598243,
            "18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127",
            "65f20fc3fdcaf569cdcf043f79047723d8856b0169bd4c475ba15ef1b37d27ae18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127");
    private static final int DRASYL_OVERHEAD = 104;
    @Param({ "1024" })
    private int packetSize;
    @Param({ "32" })
    private int flushAfter;
    @Param({ "true", "false" })
    private boolean armingEnabled;
    @Param({ "true", "false" })
    private boolean pseudorandom;
    private boolean flush;
    private int messagesSinceFlush;
    private EventLoopGroup group;
    private EventLoopGroup udpGroup;
    private Channel channel;
    private InetSocketAddress targetAddress;
    private Channel udpChannel;
    private ByteBuf data;

    @SuppressWarnings("unchecked")
    @Setup
    public void setup() {
        System.setProperty("org.drasyl.nonce.pseudorandom", Boolean.toString(pseudorandom));

        // ensure write buffer is big enough so channel will not become unwritable before next flush
        final WriteBufferWaterMark writeBufferWaterMark = new WriteBufferWaterMark(flushAfter * (packetSize + DRASYL_OVERHEAD) * 2, flushAfter * (packetSize + DRASYL_OVERHEAD) * 2);

        try {
            group = new NioEventLoopGroup();
            udpGroup = EventLoopGroupUtil.getBestEventLoopGroup(1);
            channel = new ServerBootstrap()
                    .group(group)
                    .channel(DrasylServerChannel.class)
                    .option(UDP_BOOTSTRAP, parent -> new Bootstrap()
                            .option(IP_TOS, 0xB8)
                            .option(WRITE_BUFFER_WATER_MARK, writeBufferWaterMark)
                            .group(udpGroup)
                            .channel(EventLoopGroupUtil.getBestDatagramChannel())
                            .handler(new UdpServerChannelInitializer(parent)))
                    .option(ARMING_ENABLED, armingEnabled)
                    .handler(new DefaultDrasylServerChannelInitializer())
                    .childHandler(new ChannelInboundHandlerAdapter())
                    .bind(SENDER)
                    .sync()
                    .channel();
            targetAddress = new InetSocketAddress(HOST, PORT);
            while (true) {
                final UdpServer udpServer = channel.pipeline().get(UdpServer.class);
                if (udpServer != null) {
                    udpChannel = udpServer.udpChannel();
                    if (udpChannel != null) {
                        break;
                    }
                }

                try {
                    Thread.sleep(1000);
                }
                catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            data = Unpooled.wrappedBuffer(new byte[packetSize]);
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    @TearDown
    public void teardown() {
        try {
            data.release();
            channel.close().await();
            group.shutdownGracefully().await();
            udpGroup.shutdownGracefully().await();
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    @Setup(Level.Invocation)
    public void setupWrite() {
        if (++messagesSinceFlush >= flushAfter) {
            flush = true;
            messagesSinceFlush = 0;
        }
        else {
            flush = false;
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void write(final Blackhole blackhole) {
        while (!udpChannel.isWritable()) {
            // wait until udpChannel is writable again
        }

        final ApplicationMessage appMsg = ApplicationMessage.of(1, RECIPIENT, SENDER.getIdentityPublicKey(), SENDER.getProofOfWork(), data.retainedDuplicate());
        final InetAddressedMessage<ApplicationMessage> inetMsg = new InetAddressedMessage<>(appMsg, targetAddress);
        final ChannelFuture future;
        if (flush) {
            future = udpChannel.writeAndFlush(inetMsg);
        }
        else {
            future = udpChannel.write(inetMsg);
        }
        blackhole.consume(future);
    }
}
