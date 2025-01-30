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

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.NumberUtil.numberToHumanDataRate;
import static org.drasyl.util.Preconditions.requirePositive;

@SuppressWarnings({ "finally", "java:S106", "java:S112", "java:S3077" })
abstract class AbstractMaxThroughputWriter {
    private final int duration;
    private final List<Long> throughputPerSecond = new ArrayList<>();

    AbstractMaxThroughputWriter(final int duration) {
        this.duration = requirePositive(duration);
    }

    public void run() throws Exception {
        try {
            final Channel channel = setupChannel();
            final Object msg = buildMsg();
            final UnaryOperator<Object> msgDuplicator = getMsgDuplicator();

            // Add a handler to send packets as fast as possible
            final WriteHandler<Object> writeHandler = new WriteHandler<>(msg, msgDuplicator);
            channel.pipeline().addLast(writeHandler);

            // Start a thread to print the throughput every second
            new Thread(() -> {
                for (int second = 1; second <= duration; second++) {
                    final long startBytes = bytesWritten(writeHandler);
                    try {
                        Thread.sleep(1000);
                    }
                    catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    final long endBytes = bytesWritten(writeHandler);
                    final long bytesPerSecond = endBytes - startBytes;
                    throughputPerSecond.add(bytesPerSecond);

                    // Print the current second and throughput
                    System.out.printf("%s : Second %3d         : %14s%n", className(), second, numberToHumanDataRate(bytesPerSecond * 8, (short) 2));
                }
                writeHandler.stopWriting();
                channel.close().syncUninterruptibly();

                // Calculate and print the mean (average) throughput and standard deviation
                final double mean = throughputPerSecond.stream().mapToLong(Long::longValue).average().orElse(0.0);
                final double variance = throughputPerSecond.stream()
                        .mapToDouble(d -> Math.pow(d - mean, 2))
                        .average()
                        .orElse(0.0);
                final double standardDeviation = Math.sqrt(variance);
                System.out.printf("%s : Average throughput : %14s (±  %14s)%n", className(), numberToHumanDataRate(mean * 8, (short) 2), numberToHumanDataRate(standardDeviation * 8, (short) 2));
                System.out.printf("%s : Messages sent      : %,14d%n", className(), writeHandler.messagesWritten());
                System.out.printf("%s : Messages sent/s    : %,14d%n", className(), writeHandler.messagesWritten() / duration);
            }).start();

            // Keep the main thread alive
            channel.closeFuture().await();
        }
        finally {
            teardownChannel();
            System.exit(0);
        }
    }

    protected abstract Channel setupChannel() throws Exception;

    protected abstract Object buildMsg();

    protected abstract UnaryOperator<Object> getMsgDuplicator();

    protected abstract long bytesWritten(final WriteHandler<?> writeHandler);

    protected abstract void teardownChannel() throws InterruptedException;

    protected abstract String className();

    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    protected static class WriteHandler<E> extends ChannelDuplexHandler {
        private volatile long messagesWritten;
        private final E msg;
        private final Function<E, E> msgDuplicator;
        private volatile ChannelFutureListener writeListener;
        private volatile boolean stopWriting;

        WriteHandler(final long messagesWritten,
                     final E msg,
                     final UnaryOperator<E> msgDuplicator) {
            this.messagesWritten = messagesWritten;
            this.msg = requireNonNull(msg);
            this.msgDuplicator = requireNonNull(msgDuplicator);
        }

        public WriteHandler(final E msg, final UnaryOperator<E> msgDuplicator) {
            this(0, msg, msgDuplicator);
        }

        public long messagesWritten() {
            return messagesWritten;
        }

        public void stopWriting() {
            stopWriting = true;
        }

        @Override
        public void handlerAdded(final ChannelHandlerContext ctx) {
            this.writeListener = future -> {
                if (future.isSuccess()) {
                    WriteHandler.this.messagesWritten++;
                }
                else {
                    future.channel().pipeline().fireExceptionCaught(future.cause());
                }
            };

            if (ctx.channel().isActive()) {
                doWrite(ctx);
            }
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            ctx.fireChannelActive();
            doWrite(ctx);
        }

        private void doWrite(final ChannelHandlerContext ctx) {
            final Channel channel = ctx.channel();
            if (stopWriting || !channel.isActive()) {
                ReferenceCountUtil.release(msg);
                ctx.flush();
                return;
            }

            while (!stopWriting && channel.isWritable()) {
                ctx.write(msgDuplicator.apply(msg)).addListener(writeListener);
            }

            ctx.flush();
        }

        @Override
        public void channelWritabilityChanged(final ChannelHandlerContext ctx) {
            if (ctx.channel().isWritable()) {
                // channel is writable again try to continue writing
                doWrite(ctx);
            }
            ctx.fireChannelWritabilityChanged();
        }
    }
}
