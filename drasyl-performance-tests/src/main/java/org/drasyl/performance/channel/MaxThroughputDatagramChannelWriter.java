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
import io.netty.channel.ChannelFutureListener;
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
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import static io.netty.channel.ChannelOption.WRITE_BUFFER_WATER_MARK;
import static org.drasyl.util.NumberUtil.numberToHumanDataRate;

/**
 * Writes for 60 seconds as fast as possible to an empty {@link DatagramChannel} and prints the
 * write throughput. Results are used as a baseline to compare with other channels.
 *
 * @see MaxThroughputDrasylDatagramChannelWriter
 */
@SuppressWarnings({ "java:S106", "java:S3776", "java:S4507" })
public class MaxThroughputDatagramChannelWriter {
    private static final String CLAZZ_NAME = StringUtil.simpleClassName(MaxThroughputDatagramChannelWriter.class);
    private static final boolean KQUEUE = SystemPropertyUtil.getBoolean("kqueue", false);
    private static final boolean EPOLL = SystemPropertyUtil.getBoolean("epoll", false);
    private static final String HOST = SystemPropertyUtil.get("host", "127.0.0.1");
    private static final int PORT = SystemPropertyUtil.getInt("port", 12345);
    private static final int PACKET_SIZE = SystemPropertyUtil.getInt("packetsize", 1024);
    private static final int DURATION = SystemPropertyUtil.getInt("duration", 10);
    private static final int FLUSH_AFTER = SystemPropertyUtil.getInt("flushafter", 32);
    private static int messagesSinceFlush;
    private static final LongAdder messagesWritten = new LongAdder();
    private static final LongAdder bytesWritten = new LongAdder();
    private static final List<Long> throughputPerSecond = new ArrayList<>();
    private static boolean doSend = true;

    public static void main(final String[] args) throws InterruptedException {
        System.out.printf("%s : KQUEUE: %b%n", CLAZZ_NAME, KQUEUE);
        System.out.printf("%s : EPOLL: %b%n", CLAZZ_NAME, EPOLL);
        System.out.printf("%s : HOST: %s%n", CLAZZ_NAME, HOST);
        System.out.printf("%s : PORT: %d%n", CLAZZ_NAME, PORT);
        System.out.printf("%s : PACKET_SIZE: %d%n", CLAZZ_NAME, PACKET_SIZE);
        System.out.printf("%s : DURATION: %d%n", CLAZZ_NAME, DURATION);
        System.out.printf("%s : FLUSH_AFTER: %d%n", CLAZZ_NAME, FLUSH_AFTER);

        final EventLoopGroup group;
        final Class<? extends DatagramChannel> channelClass;
        if (KQUEUE) {
            group = new KQueueEventLoopGroup();
            channelClass = KQueueDatagramChannel.class;
        }
        else if (EPOLL) {
            group = new EpollEventLoopGroup();
            channelClass = EpollDatagramChannel.class;
        }
        else {
            group = new NioEventLoopGroup();
            channelClass = NioDatagramChannel.class;
        }
        try {
            final Channel channel = new Bootstrap()
                    .group(group)
                    .channel(channelClass)
                    .option(WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(FLUSH_AFTER * PACKET_SIZE * 2, FLUSH_AFTER * PACKET_SIZE * 2))
                    .handler(new ChannelInboundHandlerAdapter())
                    .bind(0)
                    .sync()
                    .channel();

            final InetSocketAddress targetAddress = new InetSocketAddress(HOST, PORT);

            final ChannelFutureListener listener = future -> {
                if (doSend && !future.isSuccess()) {
                    future.cause().printStackTrace();
                }
            };

            // Start a thread to send packets as fast as possible
            new Thread(() -> {
                final ByteBuf data = Unpooled.wrappedBuffer(new byte[PACKET_SIZE]);
                while (doSend) {
                    if (channel.isWritable()) {
                        final DatagramPacket msg = new DatagramPacket(data.retainedDuplicate(), targetAddress);
                        final ChannelFuture future;
                        if (++messagesSinceFlush >= FLUSH_AFTER) {
                            future = channel.writeAndFlush(msg);
                            messagesSinceFlush = 0;
                        }
                        else {
                            future = channel.write(msg);
                        }
                        future.addListener(listener);
                        messagesWritten.increment();
                        bytesWritten.add(PACKET_SIZE);
                    }
                }
                channel.close().syncUninterruptibly();
            }).start();

            // Start a thread to print the throughput every second
            new Thread(() -> {
                for (int second = 1; second <= DURATION; second++) {
                    final long startBytes = bytesWritten.sum();
                    try {
                        Thread.sleep(1000);
                    }
                    catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    final long endBytes = bytesWritten.sum();
                    final long bytesPerSecond = endBytes - startBytes;
                    throughputPerSecond.add(bytesPerSecond);

                    // Print the current second and throughput
                    System.out.printf("%s : Second %3d         : %14s%n", CLAZZ_NAME, second, numberToHumanDataRate(bytesPerSecond * 8, (short) 2));
                }
                doSend = false;

                // Calculate and print the mean (average) throughput and standard deviation
                final double mean = throughputPerSecond.stream().mapToLong(Long::longValue).average().orElse(0.0);
                final double variance = throughputPerSecond.stream()
                        .mapToDouble(d -> Math.pow(d - mean, 2))
                        .average()
                        .orElse(0.0);
                final double standardDeviation = Math.sqrt(variance);
                System.out.printf("%s : Average throughput : %14s (±  %14s)%n", CLAZZ_NAME, numberToHumanDataRate(mean * 8, (short) 2), numberToHumanDataRate(standardDeviation * 8, (short) 2));
                System.out.printf("%s : Messages sent      : %,14d%n", CLAZZ_NAME, messagesWritten.sum());
                System.out.printf("%s : Messages sent/s    : %,14d%n", CLAZZ_NAME, messagesWritten.sum() / DURATION);
            }).start();

            // Keep the main thread alive
            channel.closeFuture().await();
        }
        finally {
            group.shutdownGracefully().await();
            System.exit(0);
        }
    }
}

