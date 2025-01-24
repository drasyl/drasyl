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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.drasyl.AbstractBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.net.InetSocketAddress;

import static io.netty.channel.ChannelOption.WRITE_BUFFER_WATER_MARK;

public class DatagramChannelWriteBenchmark extends AbstractBenchmark {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 12345;
    @Param({ "1024" })
    private int packetSize;
    @Param({ "32" })
    private int flushAfter;
    @Param({ "nio", "kqueue", "epoll" })
    private String channelImpl;
    private boolean flush;
    private int messagesSinceFlush;
    private EventLoopGroup group;
    private Channel channel;
    private InetSocketAddress targetAddress;
    private ByteBuf data;

    @SuppressWarnings("unchecked")
    @Setup
    public void setup() {
        // ensure write buffer is big enough so channel will not become unwritable before next flush
        final WriteBufferWaterMark writeBufferWaterMark = new WriteBufferWaterMark(flushAfter * packetSize * 2, flushAfter * packetSize * 2);

        final Class<? extends DatagramChannel> channelClass;
        if ("kqueue".equals(channelImpl)) {
            group = new KQueueEventLoopGroup();
            channelClass = KQueueDatagramChannel.class;
        }
        else if ("epoll".equals(channelImpl)) {
            group = new EpollEventLoopGroup();
            channelClass = EpollDatagramChannel.class;
        }
        else {
            group = new NioEventLoopGroup();
            channelClass = NioDatagramChannel.class;
        }

        try {
            channel = new Bootstrap()
                    .group(group)
                    .channel(channelClass)
                    .option(WRITE_BUFFER_WATER_MARK, writeBufferWaterMark)
                    .handler(new ChannelInboundHandlerAdapter())
                    .bind(0)
                    .sync()
                    .channel();
            targetAddress = new InetSocketAddress(HOST, PORT);
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
        while (!channel.isWritable()) {
            // wait until channel is writable again
        }

        final DatagramPacket msg = new DatagramPacket(data.retainedDuplicate(), targetAddress);
        final ChannelFuture future;
        if (flush) {
            future = channel.writeAndFlush(msg);
        }
        else {
            future = channel.write(msg);
        }
        blackhole.consume(future);
    }
}
