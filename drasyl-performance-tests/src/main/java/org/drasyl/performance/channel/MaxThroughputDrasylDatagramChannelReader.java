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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.channel.DefaultDrasylServerChannelInitializer;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.UdpServerToDrasylHandler;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.Identity;
import org.drasyl.node.identity.IdentityManager;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import static org.drasyl.channel.DrasylServerChannelConfig.ARMING_ENABLED;
import static org.drasyl.channel.DrasylServerChannelConfig.UDP_BIND;
import static org.drasyl.util.NumberUtil.numberToHumanDataRate;

/**
 * Receives UDP packets for 60 seconds and calculates the read throughput. Results are used to
 * compare different channels.
 */
@SuppressWarnings({
        "java:S106",
        "java:S2142",
        "java:S3776",
        "java:S4507",
        "unchecked",
        "resource",
        "CallToPrintStackTrace",
        "BusyWait"
})
public class MaxThroughputDrasylDatagramChannelReader {
    private static final String CLAZZ_NAME = StringUtil.simpleClassName(MaxThroughputDrasylDatagramChannelReader.class);
    private static final String HOST = SystemPropertyUtil.get("host", "0.0.0.0");
    private static final int PORT = SystemPropertyUtil.getInt("port", 12345);
    private static final String IDENTITY = SystemPropertyUtil.get("identity", "benchmark.identity");
    private static final int DURATION = SystemPropertyUtil.getInt("duration", 60);
    private static final LongAdder messagesRead = new LongAdder();
    private static final LongAdder bytesRead = new LongAdder();
    private static final List<Long> throughputPerSecond = new ArrayList<>();
    private static boolean doReceive = true;

    public static void main(final String[] args) throws InterruptedException, IOException {
        System.out.printf("%s : HOST: %s%n", CLAZZ_NAME, HOST);
        System.out.printf("%s : PORT: %d%n", CLAZZ_NAME, PORT);
        System.out.printf("%s : DURATION: %d%n", CLAZZ_NAME, DURATION);

        // load/create identity
        final File identityFile = new File(IDENTITY);
        if (!identityFile.exists()) {
            System.out.println("No identity present. Generate new one. This may take a while ...");
            IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
        }
        final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

        System.out.println("My address = " + identity.getAddress());

        final EventLoopGroup group = new NioEventLoopGroup();
        try {
            final Channel channel = new ServerBootstrap()
                    .group(group)
                    .channel(DrasylServerChannel.class)
                    .option(UDP_BIND, new InetSocketAddress(HOST, PORT))
                    .option(ARMING_ENABLED, false)
                    .handler(new DefaultDrasylServerChannelInitializer())
                    .childHandler(new ChannelInboundHandlerAdapter())
                    .bind(identity)
                    .sync()
                    .channel();

            Channel udpChannel;
            while (true) {
                final UdpServer udpServer = channel.pipeline().get(UdpServer.class);
                if (udpServer != null) {
                    udpChannel = udpServer.udpChannel();
                    if (udpChannel != null) {
                        break;
                    }
                }

                System.out.println("Wait for udpChannel...");

                try {
                    Thread.sleep(1000);
                }
                catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            System.out.println(udpChannel.localAddress());

            udpChannel.pipeline().addBefore(udpChannel.pipeline().context(UdpServerToDrasylHandler.class).name(), null, new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
                    if (msg instanceof InetAddressedMessage<?> && ((InetAddressedMessage<?>) msg).content() instanceof ApplicationMessage) {
                        final InetAddressedMessage<ApplicationMessage> application = (InetAddressedMessage<ApplicationMessage>) msg;
                        final ByteBuf content = application.content().getPayload();
                        final int packetSize = content.readableBytes() + 104;
                        if (doReceive) {
                            messagesRead.increment();
                            bytesRead.add(packetSize);
                        }
                        application.release();
                    }
                }
            });

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

            // Keep the main thread alive until the test is finished
            channel.closeFuture().await();
        }
        finally {
            group.shutdownGracefully().await();
            System.exit(0);
        }
    }
}
