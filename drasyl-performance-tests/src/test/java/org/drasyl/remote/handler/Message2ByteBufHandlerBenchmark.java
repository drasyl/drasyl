/*
 * Copyright (c) 2020-2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.remote.handler;

import com.google.protobuf.MessageLite;
import org.drasyl.AbstractBenchmark;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.protocol.AddressedIntermediateEnvelope;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.util.RandomUtil;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@State(Scope.Benchmark)
public class Message2ByteBufHandlerBenchmark extends AbstractBenchmark {
    private HandlerContext ctx;
    private Address recipient;
    private AddressedIntermediateEnvelope<MessageLite> msg;
    private CompletableFuture<Void> future;

    @Setup
    public void setup() {
        try {
            ctx = new MyHandlerContext();
            recipient = new MyAddress();
            final InetSocketAddressWrapper msgSender = new InetSocketAddressWrapper("127.0.0.1", 25527);
            final InetSocketAddressWrapper msgRecipient = new InetSocketAddressWrapper("127.0.0.1", 25527);
            final byte[] payload = RandomUtil.randomBytes(1024);
            final IntermediateEnvelope<Application> content = IntermediateEnvelope.application(1337, CompressedPublicKey.of("034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d"), ProofOfWork.of(3556154), CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"), byte[].class.getName(), payload);
            msg = new AddressedIntermediateEnvelope<>(msgSender, msgRecipient, content.getOrBuildByteBuf());
            future = new CompletableFuture<>();
        }
        catch (final IOException e) {
            handleUnexpectedException(e);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void matchedRead() {
        Message2ByteBufHandler.INSTANCE.matchedWrite(ctx, recipient, msg, future);
    }

    private static class MyHandlerContext implements HandlerContext {
        @Override
        public String name() {
            return null;
        }

        @Override
        public Handler handler() {
            return null;
        }

        @Override
        public HandlerContext fireExceptionCaught(final Exception cause) {
            return null;
        }

        @Override
        public CompletableFuture<Void> fireRead(final Address sender,
                                                final Object msg,
                                                final CompletableFuture<Void> future) {
            return null;
        }

        @Override
        public CompletableFuture<Void> fireEventTriggered(final Event event,
                                                          final CompletableFuture<Void> future) {
            return null;
        }

        @Override
        public CompletableFuture<Void> write(final Address recipient,
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

    private static class MyAddress implements Address {
    }
}
