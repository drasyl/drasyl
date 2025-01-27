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

import io.netty.buffer.ByteBufHolder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.AbstractBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

abstract class AbstractChannelWriteBenchmark extends AbstractBenchmark {
    private Channel channel;
    protected WriteHandler<Object> writeHandler;

    @SuppressWarnings("unchecked")
    @Setup
    public void setup() {
        try {
            channel = setupChannel();
            final Object msg = buildMsg();
            final Function<Object, Object> msgDuplicator = getMsgDuplicator();

            // Add a handler to send packets as fast as possible
            writeHandler = new WriteHandler<>(msg, msgDuplicator);
            channel.pipeline().addLast(writeHandler);
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    protected abstract Channel setupChannel() throws Exception;

    protected abstract Object buildMsg();

    protected abstract Function<Object, Object> getMsgDuplicator();

    @TearDown
    public void teardown() {
        try {
            writeHandler.stopWriting();
            channel.close().await();
            teardownChannel();
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    protected abstract void teardownChannel() throws Exception;

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void write(final Blackhole blackhole) {
        while (writeHandler.messagesWritten().get() < 1) {
            // do nothing
        }
        blackhole.consume(writeHandler.messagesWritten().getAndDecrement());
    }

    protected static class WriteHandler<E> extends ChannelDuplexHandler {
        private final AtomicLong messagesWritten;
        private final E msg;
        private final Function<E, E> msgDuplicator;
        private volatile ChannelFutureListener writeListener;
        private volatile boolean stopWriting;

        WriteHandler(final AtomicLong messagesWritten,
                     final E msg,
                     final Function<E, E> msgDuplicator) {
            this.messagesWritten = messagesWritten;
            this.msg = requireNonNull(msg);
            this.msgDuplicator = requireNonNull(msgDuplicator);
        }

        public WriteHandler(final E msg, final Function<E, E> msgDuplicator) {
            this(new AtomicLong(), msg, msgDuplicator);
        }

        public WriteHandler(final ByteBufHolder msg) {
            this(new AtomicLong(), (E) msg, e -> (E) ((ByteBufHolder) e).retainedDuplicate());
        }

        public AtomicLong messagesWritten() {
            return messagesWritten;
        }

        public void stopWriting() {
            stopWriting = true;
        }

        @Override
        public void handlerAdded(final ChannelHandlerContext ctx) {
            this.writeListener = new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture future) {
                    if (future.isSuccess()) {
                        AbstractChannelWriteBenchmark.WriteHandler.this.messagesWritten.getAndIncrement();
                    }
                    else {
                        future.channel().pipeline().fireExceptionCaught(future.cause());
                    }
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
