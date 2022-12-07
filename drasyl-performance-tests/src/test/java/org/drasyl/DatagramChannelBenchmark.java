/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ReferenceCountUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.buffer.UnpooledByteBufAllocator.DEFAULT;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.RandomUtil.randomBytes;

@Fork(1)
@Warmup(iterations = 1)
@Measurement(iterations = 1)
@State(Scope.Benchmark)
public class DatagramChannelBenchmark extends AbstractBenchmark {
    private static int MSG_LEN = 1380;
    private AtomicReference<CountDownLatch> latch = new AtomicReference<>();
    private DatagramPacket msg;
    private NioEventLoopGroup nioGroup;
    private Channel receivingCh;
    private Channel sendingCh;

    @Setup
    public void setup() {
        nioGroup = new NioEventLoopGroup();
        final InetSocketAddress receiverAddress = new InetSocketAddress("127.0.0.1", 1234);

        receivingCh = new Bootstrap()
                .group(nioGroup).channel(NioDatagramChannel.class)
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx,
                                            Object msg) throws Exception {
                        ReferenceCountUtil.release(msg);
                        latch.get().countDown();
                    }
                })
                .bind(receiverAddress).syncUninterruptibly().channel();

        sendingCh = new Bootstrap()
                .group(nioGroup).channel(NioDatagramChannel.class)
                .handler(new ChannelInboundHandlerAdapter())
                .bind(0).syncUninterruptibly().channel();

        final ByteBuf payload = DEFAULT.buffer(MSG_LEN).writeBytes(randomBytes(MSG_LEN));
        msg = new DatagramPacket(payload, receiverAddress);
    }

    @TearDown
    public void tearDown() {
        receivingCh.close().awaitUninterruptibly();
        sendingCh.close().awaitUninterruptibly();
        nioGroup.shutdownGracefully().awaitUninterruptibly();
    }

    @Setup(Level.Invocation)
    public void setupChannelRead() {
        latch.set(new CountDownLatch(1));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void channelRead(final Blackhole blackhole) throws InterruptedException {
        blackhole.consume(sendingCh.writeAndFlush(msg.retain()));
        latch.get().await(1_000, MILLISECONDS);
    }
}
