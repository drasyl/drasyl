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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import static org.drasyl.util.NumberUtil.numberToHumanDataRate;

/**
 * Reads for 60 seconds as fast as possible from a {@link DatagramChannel} and prints the
 * read throughput. Results are used as a baseline to compare with other channels.
 *
 * @see MaxThroughputDatagramChannelReader
 */
@SuppressWarnings({
        "java:S106",
        "java:S2142",
        "java:S3776",
        "java:S4507",
        "CallToPrintStackTrace"
})
public class MaxThroughputDatagramChannelReader {
    private static final String CLAZZ_NAME = StringUtil.simpleClassName(MaxThroughputDatagramChannelReader.class);
    private static final boolean KQUEUE = SystemPropertyUtil.getBoolean("kqueue", false);
    private static final boolean EPOLL = SystemPropertyUtil.getBoolean("epoll", false);
    private static final int PORT = SystemPropertyUtil.getInt("port", 12345);
    private static final int DURATION = SystemPropertyUtil.getInt("duration", 60);
    private static final LongAdder messagesRead = new LongAdder();
    private static final LongAdder bytesRead = new LongAdder();
    private static final List<Long> throughputPerSecond = new ArrayList<>();
    private static boolean doReceive;

    public static void main(final String[] args) throws InterruptedException {
        System.out.printf("%s : KQUEUE: %b%n", CLAZZ_NAME, KQUEUE);
        System.out.printf("%s : EPOLL: %b%n", CLAZZ_NAME, EPOLL);
        System.out.printf("%s : PORT: %d%n", CLAZZ_NAME, PORT);
        System.out.printf("%s : DURATION: %d%n", CLAZZ_NAME, DURATION);

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
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
                            if (msg instanceof DatagramPacket) {
                                final DatagramPacket packet = (DatagramPacket) msg;
                                final ByteBuf content = packet.content();
                                final int packetSize = content.readableBytes();
                                if (doReceive) {
                                    messagesRead.increment();
                                    bytesRead.add(packetSize);
                                }
                                content.release();
                            }
                        }
                    })
                    .bind(PORT)
                    .sync()
                    .channel();

            // wait 10 seconds to allow user to start udp client
            for (int i = 10; i > 0; i--) {
                System.out.println("Remaining time: " + i + " seconds");
                try {
                    Thread.sleep(1000); // Sleep for 1 second (1000 milliseconds)
                }
                catch (final InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Done!");
            doReceive = true;

            // Start a thread to print the throughput every second
            new Thread(() -> {
                for (int second = 1; second <= DURATION; second++) {
                    final long startBytes = bytesRead.sum();
                    try {
                        Thread.sleep(1000);
                    }
                    catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    final long endBytes = bytesRead.sum();
                    final long bytesPerSecond = endBytes - startBytes;
                    throughputPerSecond.add(bytesPerSecond);

                    // Print the current second and throughput
                    System.out.printf("%s : Second %3d         : %14s%n", CLAZZ_NAME, second, numberToHumanDataRate(bytesPerSecond * 8, (short) 2));
                }
                doReceive = false;

                // Calculate and print the mean (average) throughput and standard deviation
                final double mean = throughputPerSecond.stream().mapToLong(Long::longValue).average().orElse(0.0);
                final double variance = throughputPerSecond.stream()
                        .mapToDouble(d -> Math.pow(d - mean, 2))
                        .average()
                        .orElse(0.0);
                final double standardDeviation = Math.sqrt(variance);
                System.out.printf("%s : Average throughput : %14s (±  %14s)%n", CLAZZ_NAME, numberToHumanDataRate(mean * 8, (short) 2), numberToHumanDataRate(standardDeviation * 8, (short) 2));
                System.out.printf("%s : Messages sent      : %,14d%n", CLAZZ_NAME, messagesRead.sum());
                System.out.printf("%s : Messages sent/s    : %,14d%n", CLAZZ_NAME, messagesRead.sum() / DURATION);

                // Close the channel after the test completes
                channel.close().syncUninterruptibly();
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
