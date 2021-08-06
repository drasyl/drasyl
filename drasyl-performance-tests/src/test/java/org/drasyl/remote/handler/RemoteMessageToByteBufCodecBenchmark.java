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
package org.drasyl.remote.handler;

import com.google.protobuf.ByteString;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
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
import io.netty.channel.ServerChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import org.drasyl.AbstractBenchmark;
import org.drasyl.channel.MigrationHandlerContext;
import org.drasyl.event.Event;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.handler.crypto.AgreementId;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.remote.protocol.HopCount;
import org.drasyl.remote.protocol.Nonce;
import org.drasyl.util.RandomUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import test.util.IdentityTestUtil;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@State(Scope.Benchmark)
public class RemoteMessageToByteBufCodecBenchmark extends AbstractBenchmark {
    private MigrationHandlerContext ctx;
    private InetSocketAddressWrapper sender;
    private InetSocketAddressWrapper recipient;
    private ByteBuf byteBuf;
    private ApplicationMessage message;

    @Setup
    public void setup() {
        try {
            ctx = new MyHandlerContext();
            sender = new InetSocketAddressWrapper("127.0.0.1", 25527);
            recipient = new InetSocketAddressWrapper("127.0.0.1", 25527);
            final byte[] payload = RandomUtil.randomBytes(1024);
            final AgreementId agreementId = AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey());
            message = ApplicationMessage.of(Nonce.randomNonce(), 0, IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork(), IdentityTestUtil.ID_2.getIdentityPublicKey(), HopCount.of(), agreementId, byte[].class.getName(), ByteString.copyFrom(payload));
            byteBuf = PooledByteBufAllocator.DEFAULT.directBuffer();
            message.writeTo(byteBuf);
        }
        catch (final IOException e) {
            handleUnexpectedException(e);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void decode(final Blackhole blackhole) {
        try {
            final List<Object> out = new ArrayList<>();
            RemoteMessageToByteBufCodec.INSTANCE.decode(ctx, sender, byteBuf.slice(), out);
            byteBuf.release();
            blackhole.consume(out);
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void encode(final Blackhole blackhole) {
        try {
            final List<Object> out = new ArrayList<>();
            RemoteMessageToByteBufCodec.INSTANCE.encode(ctx, recipient, message, out);
            byteBuf.release();
            blackhole.consume(out);
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    private static class MyHandlerContext extends MigrationHandlerContext {
        public MyHandlerContext() {
            super(new ChannelHandlerContext() {
                @Override
                public Channel channel() {
                    return new ServerChannel() {
                        @Override
                        public int compareTo(final Channel o) {
                            return 0;
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
                            return null;
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
                        public ChannelFuture bind(final SocketAddress localAddress,
                                                  final ChannelPromise promise) {
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
                        public Channel read() {
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
                        public Channel flush() {
                            return null;
                        }

                        @Override
                        public ChannelFuture writeAndFlush(final Object msg,
                                                           final ChannelPromise promise) {
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
                    };
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
                public ChannelFuture bind(final SocketAddress localAddress,
                                          final ChannelPromise promise) {
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
                public ChannelHandlerContext read() {
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
                public ChannelHandlerContext flush() {
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
            });
        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public Handler handler() {
            return null;
        }

        public CompletableFuture<Void> passInbound(final Address sender,
                                                   final Object msg,
                                                   final CompletableFuture<Void> future) {
            return null;
        }

        public CompletableFuture<Void> passEvent(final Event event,
                                                 final CompletableFuture<Void> future) {
            return null;
        }

        public CompletableFuture<Void> passOutbound(final Address recipient,
                                                    final Object msg,
                                                    final CompletableFuture<Void> future) {
            return null;
        }
    }
}
