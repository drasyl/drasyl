/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.remote.crypto;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import org.drasyl.AbstractBenchmark;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.crypto.CryptoException;
import org.drasyl.handler.remote.protocol.HopCount;
import org.drasyl.handler.remote.protocol.InvalidMessageFormatException;
import org.drasyl.handler.remote.protocol.Nonce;
import org.drasyl.handler.remote.protocol.UnarmedProtocolMessage;
import org.drasyl.util.RandomUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.drasyl.handler.remote.protocol.PrivateHeader.MessageType.APPLICATION;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@Fork(1)
@Warmup(iterations = 1)
@Measurement(iterations = 1)
@State(Scope.Benchmark)
public class UnarmedMessageDecoderBenchmark extends AbstractBenchmark {
    private ChannelHandlerContext ctx;
    private UnarmedMessageDecoder instance;
    private InetAddressedMessage<UnarmedProtocolMessage> inboundMsg;
    private ByteBuf buf;

    @Setup
    public void setup() throws InvalidMessageFormatException, CryptoException {
        ctx = new MyHandlerContext();
        final byte[] payload = RandomUtil.randomBytes(1023);
        buf = PooledByteBufAllocator.DEFAULT.buffer(1024).writeByte(APPLICATION.getNumber()).writeBytes(payload);
        instance = new UnarmedMessageDecoder();
    }

    @Setup(Level.Invocation)
    public void setupDecode() {
        InetSocketAddress peerAddress = new InetSocketAddress("127.0.0.1", 25527);
        UnarmedProtocolMessage message = UnarmedProtocolMessage.of(HopCount.of(), true, 0, Nonce.randomNonce(), ID_1.getIdentityPublicKey(), ID_2.getIdentityPublicKey(), ID_2.getProofOfWork(), buf.slice());
        inboundMsg = new InetAddressedMessage<>(message, null, peerAddress);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void decode(final Blackhole blackhole) {
        try {
            final List<Object> out = new ArrayList<>();
            instance.decode(ctx, inboundMsg, out);
            blackhole.consume(out);
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    private static class MyHandlerContext implements ChannelHandlerContext {
        @Override
        public Channel channel() {
            return null;
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
            return PooledByteBufAllocator.DEFAULT;
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
    }
}
