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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.drasyl.AbstractBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import java.net.PortUnreachableException;
import java.util.concurrent.atomic.AtomicLong;

public class DatagramChannelReadBenchmark extends AbstractBenchmark {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 12345;
    @Param({ "3" })
    private int writeThreads;
    @Param({ "1024" })
    private int packetSize;
    @Param({ "nio", "kqueue", "epoll" })
    private String channelImpl;
    private EventLoopGroup group;
    private ByteBuf data;
    private boolean doWrite = true;
    private ChannelGroup writeChannels;
    private final AtomicLong receivedMsgs = new AtomicLong();

    @SuppressWarnings("unchecked")
    @Setup
    public void setup() {
        final Class<? extends DatagramChannel> channelClass;
        if ("kqueue".equals(channelImpl)) {
            group = new KQueueEventLoopGroup(writeThreads + 1);
            channelClass = KQueueDatagramChannel.class;
        }
        else if ("epoll".equals(channelImpl)) {
            group = new EpollEventLoopGroup(writeThreads + 1);
            channelClass = EpollDatagramChannel.class;
        }
        else {
            group = new NioEventLoopGroup(writeThreads + 1);
            channelClass = NioDatagramChannel.class;
        }

        data = Unpooled.wrappedBuffer(new byte[packetSize]);

        try {
            final Bootstrap writeBootstrap = new Bootstrap()
                    .group(group)
                    .channel(channelClass)
                    .handler(new MyChannelDuplexHandler());

            writeChannels = new DefaultChannelGroup(group.next());
            for (int i = 0; i < writeThreads; i++) {
                writeChannels.add(writeBootstrap.connect(HOST, PORT).sync().channel());
            }

            final Bootstrap readBootstrap = new Bootstrap()
                    .group(group)
                    .channel(channelClass)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
                            if (msg instanceof DatagramPacket) {
                                ((DatagramPacket) msg).content().release();
                                receivedMsgs.incrementAndGet();
                            }
                        }
                    });
            readBootstrap.bind(HOST, PORT);
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
            writeChannels.close().await();
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
