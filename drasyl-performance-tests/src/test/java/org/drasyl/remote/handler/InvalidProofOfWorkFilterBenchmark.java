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

import com.google.protobuf.MessageLite;
import org.drasyl.AbstractBenchmark;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import test.util.IdentityTestUtil;

import java.util.concurrent.CompletableFuture;

import static java.time.Duration.ofDays;
import static java.util.Objects.requireNonNull;

@State(Scope.Benchmark)
public class InvalidProofOfWorkFilterBenchmark extends AbstractBenchmark {
    private MyHandlerContext ctx;
    private MyAddress msgSender;
    private RemoteEnvelope<? extends MessageLite> msgAddressedToMe;
    private RemoteEnvelope<? extends MessageLite> msgNotAddressedToMe;

    @Setup
    public void setup() {
        final Identity sender = IdentityTestUtil.ID_1;
        ctx = new MyHandlerContext(IdentityTestUtil.ID_2);
        msgSender = new MyAddress();
        msgAddressedToMe = RemoteEnvelope.application(1337, sender.getIdentityPublicKey(), sender.getProofOfWork(), ctx.identity().getIdentityPublicKey(), byte[].class.getName(), new byte[0]);
        msgNotAddressedToMe = RemoteEnvelope.application(1337, sender.getIdentityPublicKey(), sender.getProofOfWork(), IdentityTestUtil.ID_3.getIdentityPublicKey(), byte[].class.getName(), new byte[0]);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void acceptMsgAddressedToMe() {
        try {
            InvalidProofOfWorkFilter.INSTANCE.accept(ctx, msgSender, msgAddressedToMe);
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void acceptMsgNotAddressedToMe() {
        try {
            InvalidProofOfWorkFilter.INSTANCE.accept(ctx, msgSender, msgNotAddressedToMe);
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    private static class MyHandlerContext implements HandlerContext {
        private final DrasylConfig config;
        private final Identity identity;

        public MyHandlerContext(final Identity identity) {
            config = DrasylConfig.newBuilder()
                    .remotePingTimeout(ofDays(1))
                    .remotePingCommunicationTimeout(ofDays(1))
                    .build();
            this.identity = requireNonNull(identity);
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
            return config;
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
            return identity;
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

    private static class MyAddress implements Address {
    }
}
