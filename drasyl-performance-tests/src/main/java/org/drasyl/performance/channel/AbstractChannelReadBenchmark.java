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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.group.ChannelGroup;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.AbstractBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;

@SuppressWarnings({
        "JmhInspections",
        "DataFlowIssue",
        "java:S112",
        "java:S2142",
        "StatementWithEmptyBody"
})
abstract class AbstractChannelReadBenchmark extends AbstractBenchmark {
    protected final AtomicLong receivedMsgs = new AtomicLong();
    private ChannelGroup writeChannels;
    private Channel readChannel;

    @Setup
    public void setup() {
        try {
            writeChannels = setupWriteChannels();
            readChannel = setupReadChannel();
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    protected abstract ChannelGroup setupWriteChannels() throws Exception;

    protected abstract Channel setupReadChannel() throws InterruptedException;

    @TearDown
    public void teardown() {
        try {
            writeChannels.forEach(ch -> ch.pipeline().get(WriteHandler.class).stopWriting());
            writeChannels.close().await();
            readChannel.close().await();
            teardownChannel();
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    protected abstract void teardownChannel() throws InterruptedException;

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void read() {
        while (receivedMsgs.get() < 1) {
            // do nothing
        }
        receivedMsgs.getAndDecrement();
    }

    @SuppressWarnings("unchecked")
    protected static class WriteHandler<E> extends ChannelDuplexHandler {
        private final E msg;
        private final UnaryOperator<E> msgDuplicator;
        private volatile boolean stopWriting;

        public WriteHandler(final E msg,
                            final UnaryOperator<E> msgDuplicator) {
            this.msg = requireNonNull(msg);
            this.msgDuplicator = requireNonNull(msgDuplicator);
        }

        public WriteHandler(final ByteBuf msg) {
            this((E) msg, e -> (E) ((ByteBuf) e).retainedDuplicate());
        }

        public void stopWriting() {
            stopWriting = true;
        }

        @Override
        public void handlerAdded(final ChannelHandlerContext ctx) {
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
                ctx.write(msgDuplicator.apply(msg)).addListener(FIRE_EXCEPTION_ON_FAILURE);
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
