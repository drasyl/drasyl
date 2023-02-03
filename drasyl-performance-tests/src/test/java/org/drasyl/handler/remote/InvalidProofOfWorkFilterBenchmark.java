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
package org.drasyl.handler.remote;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import org.drasyl.AbstractBenchmark;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_3;

@Fork(1)
@Warmup(iterations = 1)
@Measurement(iterations = 1)
@State(Scope.Benchmark)
public class InvalidProofOfWorkFilterBenchmark extends AbstractBenchmark {
    private MyHandlerContext ctx;
    private InetSocketAddress msgSender;
    private ApplicationMessage msgAddressedToMe;
    private ApplicationMessage msgAddressedToOther;
    private InvalidProofOfWorkFilter handler;

    @Setup
    public void setup() {
        ctx = new MyHandlerContext();
        msgSender = new InetSocketAddress("127.0.0.1", 22529);
        msgAddressedToMe = ApplicationMessage.of(1337, ID_1.getIdentityPublicKey(), ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), Unpooled.buffer());
        msgAddressedToOther = ApplicationMessage.of(1337, ID_3.getIdentityPublicKey(), ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), Unpooled.buffer());
        handler = new InvalidProofOfWorkFilter();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void channelReadMessageAddressedToMe() { // relay?
        try {
            handler.channelRead0(ctx, new InetAddressedMessage<>(msgAddressedToMe, null, msgSender));
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void channelReadMessageAddressedToOther() {
        try {
            handler.channelRead0(ctx, new InetAddressedMessage<>(msgAddressedToOther, null, msgSender));
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    private class MyHandlerContext implements ChannelHandlerContext {
        @Override
        public Channel channel() {
            return new MyChannel();
        }

        @Override
        public EventExecutor executor() {
            return null;
        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public ChannelHandler handler() {
            return null;
        }

        @Override
        public boolean isRemoved() {
            return false;
        }

        @Override
        public ChannelHandlerContext fireChannelRegistered() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelUnregistered() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelActive() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelInactive() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireExceptionCaught(final Throwable cause) {
            return null;
        }

        @Override
        public ChannelHandlerContext fireUserEventTriggered(final Object evt) {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelRead(final Object msg) {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelReadComplete() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelWritabilityChanged() {
            return null;
        }

        @Override
        public ChannelHandlerContext read() {
            return null;
        }

        @Override
        public ChannelHandlerContext flush() {
            return null;
        }

        @Override
        public ChannelPipeline pipeline() {
            return null;
        }

        @Override
        public ByteBufAllocator alloc() {
            return null;
        }

        @Override
        public <T> Attribute<T> attr(final AttributeKey<T> key) {
            return null;
        }

        @Override
        public <T> boolean hasAttr(final AttributeKey<T> key) {
            return false;
        }

        @Override
        public ChannelFuture bind(final SocketAddress localAddress) {
            return null;
        }

        @Override
        public ChannelFuture connect(final SocketAddress remoteAddress) {
            return null;
        }

        @Override
        public ChannelFuture connect(final SocketAddress remoteAddress,
                                     final SocketAddress localAddress) {
            return null;
        }

        @Override
        public ChannelFuture disconnect() {
            return null;
        }

        @Override
        public ChannelFuture close() {
            return null;
        }

        @Override
        public ChannelFuture deregister() {
            return null;
        }

        @Override
        public ChannelFuture bind(final SocketAddress localAddress, final ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture connect(final SocketAddress remoteAddress,
                                     final ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture connect(final SocketAddress remoteAddress,
                                     final SocketAddress localAddress,
                                     final ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture disconnect(final ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture close(final ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture deregister(final ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture write(final Object msg) {
            return null;
        }

        @Override
        public ChannelFuture write(final Object msg, final ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture writeAndFlush(final Object msg, final ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture writeAndFlush(final Object msg) {
            return null;
        }

        @Override
        public ChannelPromise newPromise() {
            return null;
        }

        @Override
        public ChannelProgressivePromise newProgressivePromise() {
            return null;
        }

        @Override
        public ChannelFuture newSucceededFuture() {
            return null;
        }

        @Override
        public ChannelFuture newFailedFuture(final Throwable cause) {
            return null;
        }

        @Override
        public ChannelPromise voidPromise() {
            return null;
        }

        private class MyChannel implements Channel {
            @Override
            public ChannelId id() {
                return null;
            }

            @Override
            public EventLoop eventLoop() {
                return null;
            }

            @Override
            public Channel parent() {
                return null;
            }

            @Override
            public ChannelConfig config() {
                return null;
            }

            @Override
            public boolean isOpen() {
                return false;
            }

            @Override
            public boolean isRegistered() {
                return false;
            }

            @Override
            public boolean isActive() {
                return false;
            }

            @Override
            public ChannelMetadata metadata() {
                return null;
            }

            @Override
            public SocketAddress localAddress() {
                return ID_1.getAddress();
            }

            @Override
            public SocketAddress remoteAddress() {
                return null;
            }

            @Override
            public ChannelFuture closeFuture() {
                return null;
            }

            @Override
            public boolean isWritable() {
                return false;
            }

            @Override
            public long bytesBeforeUnwritable() {
                return 0;
            }

            @Override
            public long bytesBeforeWritable() {
                return 0;
            }

            @Override
            public Unsafe unsafe() {
                return null;
            }

            @Override
            public ChannelPipeline pipeline() {
                return null;
            }

            @Override
            public ByteBufAllocator alloc() {
                return null;
            }

            @Override
            public Channel read() {
                return null;
            }

            @Override
            public Channel flush() {
                return null;
            }

            @Override
            public ChannelFuture bind(SocketAddress localAddress) {
                return null;
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress) {
                return null;
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress,
                                         SocketAddress localAddress) {
                return null;
            }

            @Override
            public ChannelFuture disconnect() {
                return null;
            }

            @Override
            public ChannelFuture close() {
                return null;
            }

            @Override
            public ChannelFuture deregister() {
                return null;
            }

            @Override
            public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
                return null;
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
                return null;
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress,
                                         SocketAddress localAddress,
                                         ChannelPromise promise) {
                return null;
            }

            @Override
            public ChannelFuture disconnect(ChannelPromise promise) {
                return null;
            }

            @Override
            public ChannelFuture close(ChannelPromise promise) {
                return null;
            }

            @Override
            public ChannelFuture deregister(ChannelPromise promise) {
                return null;
            }

            @Override
            public ChannelFuture write(Object msg) {
                return null;
            }

            @Override
            public ChannelFuture write(Object msg, ChannelPromise promise) {
                return null;
            }

            @Override
            public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
                return null;
            }

            @Override
            public ChannelFuture writeAndFlush(Object msg) {
                return null;
            }

            @Override
            public ChannelPromise newPromise() {
                return null;
            }

            @Override
            public ChannelProgressivePromise newProgressivePromise() {
                return null;
            }

            @Override
            public ChannelFuture newSucceededFuture() {
                return null;
            }

            @Override
            public ChannelFuture newFailedFuture(Throwable cause) {
                return null;
            }

            @Override
            public ChannelPromise voidPromise() {
                return null;
            }

            @Override
            public <T> Attribute<T> attr(AttributeKey<T> key) {
                return null;
            }

            @Override
            public <T> boolean hasAttr(AttributeKey<T> key) {
                return false;
            }

            @Override
            public int compareTo(Channel o) {
                return 0;
            }
        }
    }
}
