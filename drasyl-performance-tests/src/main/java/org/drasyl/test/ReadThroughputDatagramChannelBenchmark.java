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
package org.drasyl.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * Reads for 60 seconds as fast as possible from a {@link NioDatagramChannel} and prints the
 * read throughput. Results are used as a baseline to compare with other channels.
 *
 * @see ReadThroughputDatagramChannelBenchmark
 */
public class ReadThroughputDatagramChannelBenchmark {
    private static final int PORT = SystemPropertyUtil.getInt("port", 12345);
    private static final int DURATION = SystemPropertyUtil.getInt("duration", 60);
    private static final LongAdder messagesRead = new LongAdder();
    private static final LongAdder bytesRead = new LongAdder();
    private static final List<Double> throughputPerSecond = new ArrayList<>();
    private static boolean doReceive;

    public static void main(final String[] args) throws InterruptedException {
        final NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            final Channel channel = new Bootstrap()
                    .group(group)
                    .channel(NioDatagramChannel.class)
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
                    final double megabytesPerSecond = bytesPerSecond / 1048576.0;
                    throughputPerSecond.add(megabytesPerSecond);

                    // Print the current second and throughput
                    System.out.println(String.format("%s : Second %3d         : %7.2f MB/s", StringUtil.simpleClassName(ReadThroughputDatagramChannelBenchmark.class), second, megabytesPerSecond));
                }
                doReceive = false;

                // Calculate and print the mean (average) throughput and standard deviation
                final double mean = throughputPerSecond.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                final double variance = throughputPerSecond.stream()
                        .mapToDouble(d -> Math.pow(d - mean, 2))
                        .average()
                        .orElse(0.0);
                final double standardDeviation = Math.sqrt(variance);
                System.out.println(String.format("%s : Average throughput : %7.2f MB/s (±  %7.2f MB/s)", StringUtil.simpleClassName(ReadThroughputDatagramChannelBenchmark.class), mean, standardDeviation));
                System.out.println(String.format("%s : Messages received  : %,7d", StringUtil.simpleClassName(ReadThroughputDatagramChannelBenchmark.class), messagesRead.sum()));

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
