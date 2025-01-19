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
package org.drasyl.performance;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * Writes for 60 seconds as fast as possible to an empty {@link NioDatagramChannel} and prints the
 * write throughput. Results are used as a baseline to compare with other channels.
 *
 * @see WriteThroughputDrasylDatagramChannelBenchmark
 */
public class WriteThroughputDatagramChannelBenchmark {
    private static final String HOST = SystemPropertyUtil.get("host", "127.0.0.1");
    private static final int PORT = SystemPropertyUtil.getInt("port", 12345);
    private static final int PACKET_SIZE = SystemPropertyUtil.getInt("packetsize", 1024);
    private static final int DURATION = SystemPropertyUtil.getInt("duration", 60);
    private static final LongAdder messagesWritten = new LongAdder();
    private static final LongAdder bytesWritten = new LongAdder();
    private static final List<Double> throughputPerSecond = new ArrayList<>();
    private static boolean doSend = true;

    public static void main(final String[] args) throws InterruptedException {
        final EventLoopGroup group = new NioEventLoopGroup();
        try {
            final Channel channel = new Bootstrap()
                    .group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInboundHandlerAdapter())
                    .bind(0)
                    .sync()
                    .channel();

            final InetSocketAddress targetAddress = new InetSocketAddress(HOST, PORT);

            final ChannelFutureListener listener = new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        future.cause().printStackTrace();
                    }
                }
            };

            // Start a thread to send packets as fast as possible
            new Thread(() -> {
                final ByteBuf data = Unpooled.wrappedBuffer(new byte[PACKET_SIZE]);
                while (doSend) {
                    if (channel.isWritable()) {
                        channel.writeAndFlush(new DatagramPacket(data.retainedDuplicate(), targetAddress)).addListener(listener);
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
                    final double megabytesPerSecond = bytesPerSecond / 1048576.0;
                    throughputPerSecond.add(megabytesPerSecond);

                    // Print the current second and throughput
                    System.out.printf("%s : Second %3d         : %7.2f MB/s%n", StringUtil.simpleClassName(WriteThroughputDatagramChannelBenchmark.class), second, megabytesPerSecond);
                }
                doSend = false;

                // Calculate and print the mean (average) throughput and standard deviation
                final double mean = throughputPerSecond.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                final double variance = throughputPerSecond.stream()
                        .mapToDouble(d -> Math.pow(d - mean, 2))
                        .average()
                        .orElse(0.0);
                final double standardDeviation = Math.sqrt(variance);
                System.out.printf("%s : Average throughput : %7.2f MB/s (±  %7.2f MB/s)%n", StringUtil.simpleClassName(WriteThroughputDatagramChannelBenchmark.class), mean, standardDeviation);
                System.out.printf("%s : Messages sent      : %,7d%n", StringUtil.simpleClassName(WriteThroughputDatagramChannelBenchmark.class), messagesWritten.sum());
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

