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
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.Identity;
import org.drasyl.node.identity.IdentityManager;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import static org.drasyl.util.NumberUtil.numberToHumanDataRate;

/**
 * Helper class to write as fast as possible (on multiple threads) to a given udp server. Intended
 * to be used with max throughput readers.
 *
 * @see MaxThroughputDatagramChannelReader
 * @see MaxThroughputDrasylDatagramChannelReader
 */
@SuppressWarnings({ "java:S106", "java:S3776", "java:S4507" })
public class MaxThroughputChannelReaderHelper {
    private static final String CLAZZ_NAME = StringUtil.simpleClassName(MaxThroughputChannelReaderHelper.class);
    private static final String HOST = SystemPropertyUtil.get("host", "127.0.0.1");
    private static final int PORT = SystemPropertyUtil.getInt("port", 12345);
    private static final String IDENTITY = SystemPropertyUtil.get("identity", "benchmark.identity");
    private static final String SENDER = SystemPropertyUtil.get("sender", "benchmark_sender.identity");
    private static final int PACKET_SIZE = SystemPropertyUtil.getInt("packetsize", 1024);
    private static final int THREADS = SystemPropertyUtil.getInt("threads", 2);
    private static final int DURATION = SystemPropertyUtil.getInt("duration", 90);
    private static final LongAdder messagesWritten = new LongAdder();
    private static final LongAdder bytesWritten = new LongAdder();
    private static final List<Long> throughputPerSecond = new ArrayList<>();
    private static boolean doSend = true;

    public static void main(final String[] args) throws InterruptedException, IOException {
        // load/create identity
        final File identityFile = new File(IDENTITY);
        if (!identityFile.exists()) {
            System.out.println("No identity present. Generate new one. This may take a while ...");
            IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
        }
        final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

        // load/create sender
        final File senderFile = new File(SENDER);
        if (!senderFile.exists()) {
            System.out.println("No identity present. Generate new one. This may take a while ...");
            IdentityManager.writeIdentityFile(senderFile.toPath(), Identity.generateIdentity());
        }
        final Identity sender = IdentityManager.readIdentityFile(senderFile.toPath());

        System.out.println("My address = " + identity.getAddress());

        final ByteBuf payload = Unpooled.wrappedBuffer(new byte[PACKET_SIZE]);
        final ApplicationMessage message = ApplicationMessage.of(1, identity.getIdentityPublicKey(), sender.getIdentityPublicKey(), sender.getProofOfWork(), payload);

        System.out.println(message);

        final UnpooledByteBufAllocator alloc = UnpooledByteBufAllocator.DEFAULT;
        final ByteBuf encoded = message.encodeMessage(alloc);

        final InetSocketAddress remoteAddress = new InetSocketAddress(HOST, PORT);

        final ChannelFutureListener listener = future -> {
            if (!future.isSuccess() && !(future.cause() instanceof PortUnreachableException)) {
                future.channel().pipeline().fireExceptionCaught(future.cause());
            }
        };

        final EventLoopGroup group = new NioEventLoopGroup();
        try {
            final Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelDuplexHandler() {
                        @Override
                        public void channelActive(final ChannelHandlerContext ctx) throws Exception {
                            ctx.fireChannelActive();
                            scheduleWriteTask(ctx);
                        }

                        private void scheduleWriteTask(final ChannelHandlerContext ctx) {
                            if (ctx.channel().isActive()) {
                                ctx.executor().execute(() -> {
                                    while (doSend && ctx.channel().isWritable()) {
                                        messagesWritten.increment();
                                        bytesWritten.add(PACKET_SIZE);
                                        ctx.write(encoded.retain()).addListener(listener);
                                    }
                                    if (!doSend) {
                                        ctx.close();
                                    }
                                    ctx.flush();
                                });
                            }
                        }

                        @Override
                        public void channelWritabilityChanged(final ChannelHandlerContext ctx) throws Exception {
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
                    });

            final ChannelGroup channelGroup = new DefaultChannelGroup(group.next());
            for (int i = 0; i < THREADS; i++) {
                channelGroup.add(bootstrap.connect(remoteAddress).channel());
            }

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

            // Keep the main thread alive until the test is finished
            final ChannelGroupFuture channelFutures = channelGroup.newCloseFuture();
            channelFutures.await();
        }
        catch (final Exception e) {
            throw new RuntimeException(e);
        }
        finally {
            group.shutdownGracefully().await();
            System.exit(0);
        }
    }
}
