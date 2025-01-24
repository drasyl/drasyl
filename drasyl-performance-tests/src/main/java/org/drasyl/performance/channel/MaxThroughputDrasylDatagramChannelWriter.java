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
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.channel.DefaultDrasylServerChannelInitializer;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.UdpServerChannelInitializer;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.identity.IdentityManager;
import org.drasyl.util.EventLoopGroupUtil;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import static io.netty.channel.ChannelOption.IP_TOS;
import static io.netty.channel.ChannelOption.WRITE_BUFFER_WATER_MARK;
import static org.drasyl.channel.DrasylServerChannelConfig.ARMING_ENABLED;
import static org.drasyl.channel.DrasylServerChannelConfig.UDP_BOOTSTRAP;
import static org.drasyl.util.NumberUtil.numberToHumanDataRate;

/**
 * Writes for 60 seconds as fast as possible to the {@link DatagramChannel} used by drasyl and
 * prints the write throughput. Results are used as a baseline to compare with other channels.
 *
 * @see MaxThroughputDatagramChannelWriter
 */
@SuppressWarnings({ "java:S106", "java:S3776", "java:S4507" })
public class MaxThroughputDrasylDatagramChannelWriter {
    private static final String CLAZZ_NAME = StringUtil.simpleClassName(MaxThroughputDrasylDatagramChannelWriter.class);
    private static final String HOST = SystemPropertyUtil.get("host", "127.0.0.1");
    private static final int PORT = SystemPropertyUtil.getInt("port", 12345);
    private static final String IDENTITY = SystemPropertyUtil.get("identity", "benchmark.identity");
    private static final int PACKET_SIZE = SystemPropertyUtil.getInt("packetsize", 1024);
    private static final int DURATION = SystemPropertyUtil.getInt("duration", 60);
    private static final String RECIPIENT = SystemPropertyUtil.get("recipient", "c909a27d9ec0127c57142c3e1547ba9f82bc605277380b2a8fc0fabafe2be4c9");
    private static final int FLUSH_AFTER = SystemPropertyUtil.getInt("flushafter", 32);
    private static final int DRASYL_OVERHEAD = 104;
    private static int messagesSinceFlush;
    private static final LongAdder messagesWritten = new LongAdder();
    private static final LongAdder bytesWritten = new LongAdder();
    private static final List<Long> throughputPerSecond = new ArrayList<>();
    private static boolean doSend = true;

    public static void main(final String[] args) throws InterruptedException, IOException {
        System.out.printf("%s : HOST: %s%n", CLAZZ_NAME, HOST);
        System.out.printf("%s : PORT: %d%n", CLAZZ_NAME, PORT);
        System.out.printf("%s : IDENTITY: %s%n", CLAZZ_NAME, IDENTITY);
        System.out.printf("%s : PACKET_SIZE: %d%n", CLAZZ_NAME, PACKET_SIZE);
        System.out.printf("%s : DURATION: %d%n", CLAZZ_NAME, DURATION);
        System.out.printf("%s : RECIPIENT: %s%n", CLAZZ_NAME, RECIPIENT);
        System.out.printf("%s : FLUSH_AFTER: %d%n", CLAZZ_NAME, FLUSH_AFTER);

        // load/create identity
        final File identityFile = new File(IDENTITY);
        if (!identityFile.exists()) {
            System.out.println("No identity present. Generate new one. This may take a while ...");
            IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
        }
        final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

        System.out.println("My address = " + identity.getAddress());

        final EventLoopGroup group = new NioEventLoopGroup();
        final EventLoopGroup udpGroup = EventLoopGroupUtil.getBestEventLoopGroup(1);
        try {
            final Channel channel = new ServerBootstrap()
                    .group(group)
                    .channel(DrasylServerChannel.class)
                    .option(UDP_BOOTSTRAP, parent -> new Bootstrap()
                            .option(IP_TOS, 0xB8)
                            .option(WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(FLUSH_AFTER * (PACKET_SIZE + DRASYL_OVERHEAD) * 2, FLUSH_AFTER * (PACKET_SIZE + DRASYL_OVERHEAD) * 2))
                            .group(udpGroup.next())
                            .channel(EventLoopGroupUtil.getBestDatagramChannel())
                            .handler(new UdpServerChannelInitializer(parent)))
                    .option(ARMING_ENABLED, false)
                    .handler(new DefaultDrasylServerChannelInitializer())
                    .childHandler(new ChannelInboundHandlerAdapter())
                    .bind(identity)
                    .sync()
                    .channel();

            final InetSocketAddress targetAddress = new InetSocketAddress(HOST, PORT);
            final IdentityPublicKey recipient = IdentityPublicKey.of(RECIPIENT);

            DatagramChannel udpChannel;
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

            final ChannelFutureListener listener = future -> {
                if (doSend && !future.isSuccess()) {
                    future.cause().printStackTrace();
                }
            };

            // Start a thread to send packets as fast as possible
            final DatagramChannel finalUdpChannel = udpChannel;
            new Thread(() -> {
                final ByteBuf data = Unpooled.wrappedBuffer(new byte[PACKET_SIZE]);

                while (doSend) {
                    if (finalUdpChannel.isWritable()) {
                        final ApplicationMessage appMsg = ApplicationMessage.of(1, recipient, identity.getIdentityPublicKey(), identity.getProofOfWork(), data.retainedDuplicate());
                        final InetAddressedMessage<ApplicationMessage> inetMsg = new InetAddressedMessage<>(appMsg, targetAddress);

                        final ChannelFuture future;
                        if (++messagesSinceFlush >= FLUSH_AFTER) {
                            future = channel.writeAndFlush(inetMsg);
                            messagesSinceFlush = 0;
                        }
                        else {
                            future = channel.write(inetMsg);
                        }
                        future.addListener(listener);
                        messagesWritten.increment();
                        bytesWritten.add(PACKET_SIZE + (long) DRASYL_OVERHEAD);
                    }
                }
                finalUdpChannel.close().syncUninterruptibly();
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
                    System.out.printf("%s : Second %3d: %14s%n", CLAZZ_NAME, second, numberToHumanDataRate(bytesPerSecond * 8, (short) 2));
                }
                doSend = false;

                // Calculate and print the mean (average) throughput and standard deviation
                final double mean = throughputPerSecond.stream().mapToDouble(Long::longValue).average().orElse(0.0);
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
            udpGroup.shutdownGracefully().await();
            System.exit(0);
        }
    }
}
