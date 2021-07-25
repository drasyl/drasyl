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
import io.netty.buffer.PooledByteBufAllocator;
import org.drasyl.AbstractBenchmark;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.remote.handler.crypto.AgreementId;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.remote.protocol.HopCount;
import org.drasyl.remote.protocol.Nonce;
import org.drasyl.util.RandomUtil;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import test.util.IdentityTestUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@State(Scope.Benchmark)
public class RemoteMessageToByteBufCodecBenchmark extends AbstractBenchmark {
    private HandlerContext ctx;
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

    private static class MyHandlerContext implements HandlerContext {
        @Override
        public ByteBuf alloc() {
            return PooledByteBufAllocator.DEFAULT.ioBuffer();
        }

        @Override
        public ByteBuf alloc(final boolean preferDirect) {
            return null;
        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public Handler handler() {
            return null;
        }

        @Override
        public HandlerContext passException(final Exception cause) {
            return null;
        }

        @Override
        public CompletableFuture<Void> passInbound(final Address sender,
                                                   final Object msg,
                                                   final CompletableFuture<Void> future) {
            return null;
        }

        @Override
        public CompletableFuture<Void> passEvent(final Event event,
                                                 final CompletableFuture<Void> future) {
            return null;
        }

        @Override
        public CompletableFuture<Void> passOutbound(final Address recipient,
                                                    final Object msg,
                                                    final CompletableFuture<Void> future) {
            return null;
        }

        @Override
        public DrasylConfig config() {
            return null;
        }

        @Override
        public Pipeline pipeline() {
            return null;
        }

        @Override
        public DrasylScheduler independentScheduler() {
            return null;
        }

        @Override
        public DrasylScheduler dependentScheduler() {
            return null;
        }

        @Override
        public Identity identity() {
            return null;
        }

        @Override
        public PeersManager peersManager() {
            return null;
        }

        @Override
        public Serialization inboundSerialization() {
            return null;
        }

        @Override
        public Serialization outboundSerialization() {
            return null;
        }
    }
}
