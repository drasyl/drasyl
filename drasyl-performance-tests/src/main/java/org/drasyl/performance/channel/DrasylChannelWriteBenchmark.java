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
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.AbstractBenchmark;
import org.drasyl.channel.DefaultDrasylServerChannelInitializer;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.handler.remote.UdpServerChannelInitializer;
import org.drasyl.identity.DrasylAddress;
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
import static org.drasyl.channel.DrasylServerChannelConfig.PEERS_MANAGER;
import static org.drasyl.channel.DrasylServerChannelConfig.UDP_BOOTSTRAP;
import static org.drasyl.performance.IdentityBenchmarkUtil.ID_1;

public class DrasylChannelWriteBenchmark extends AbstractBenchmark {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 12345;
    private static final String RECIPIENT = SystemPropertyUtil.get("recipient", "c909a27d9ec0127c57142c3e1547ba9f82bc605277380b2a8fc0fabafe2be4c9");
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
    private final Identity identity = ID_1;
    private Channel drasylChannel;
    private IdentityPublicKey recipient;
    private ByteBuf data;
    private ByteBuf msg;

    @SuppressWarnings("unchecked")
    @Setup
    public void setup() {
        System.setProperty("org.drasyl.nonce.pseudorandom", Boolean.toString(pseudorandom));

        final InetSocketAddress targetAddress = new InetSocketAddress(HOST, PORT);
        // ensure write buffer is big enough so channel will not become unwritable before next flush
        final WriteBufferWaterMark waterMark = new WriteBufferWaterMark(flushAfter * (packetSize + DRASYL_OVERHEAD) * 2, flushAfter * (packetSize + DRASYL_OVERHEAD) * 2);

        try {
            group = new NioEventLoopGroup();
            udpGroup = EventLoopGroupUtil.getBestEventLoopGroup(1);
            channel = new ServerBootstrap()
                    .group(group)
                    .channel(DrasylServerChannel.class)
                    .option(WRITE_BUFFER_WATER_MARK, waterMark)
                    .option(UDP_BOOTSTRAP, parent -> new Bootstrap()
                            .option(IP_TOS, 0xB8)
                            .option(WRITE_BUFFER_WATER_MARK, waterMark)
                            .group(udpGroup)
                            .channel(EventLoopGroupUtil.getBestDatagramChannel())
                            .handler(new UdpServerChannelInitializer(parent)))
                    .option(PEERS_MANAGER, new PeersManager() {
                        @Override
                        public InetSocketAddress resolve(final DrasylAddress peerKey) {
                            if (recipient.equals(peerKey)) {
                                return targetAddress;
                            }
                            return super.resolve(peerKey);
                        }
                    })
                    .option(ARMING_ENABLED, armingEnabled)
                    .handler(new DefaultDrasylServerChannelInitializer())
                    .childHandler(new ChannelInboundHandlerAdapter())
                    .bind(identity)
                    .sync()
                    .channel();
            recipient = IdentityPublicKey.of(RECIPIENT);
            drasylChannel = ((DrasylServerChannel) channel).serve(recipient).sync().channel();
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
        msg = data.retainedDuplicate();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void write(final Blackhole blackhole) {
        while (!drasylChannel.isWritable()) {
            // wait until udpChannel is writable again
        }

        final ChannelFuture future;
        if (flush) {
            future = drasylChannel.writeAndFlush(msg);
        }
        else {
            future = drasylChannel.write(msg);
        }
        blackhole.consume(future);
    }
}
