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

import org.drasyl.DrasylConfig;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedKeyPair;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.Serialization;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.serialization.SerializedApplicationMessage;
import org.drasyl.remote.handler.UdpDiscoveryHandler.Peer;
import org.drasyl.remote.protocol.MessageId;
import org.drasyl.util.Pair;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.time.Duration.ofDays;

@State(Scope.Benchmark)
@Fork(value = 1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
public class UdpDiscoveryHandlerBenchmark {
    private Map<MessageId, UdpDiscoveryHandler.OpenPing> openPingsCache;
    private Map<Pair<CompressedPublicKey, CompressedPublicKey>, Boolean> uniteAttemptsCache;
    private Map<CompressedPublicKey, Peer> peers;
    private Set<CompressedPublicKey> directConnectionPeers;
    private UdpDiscoveryHandler handler;
    private HandlerContext ctx;
    private Address recipient;
    private SerializedApplicationMessage msg;
    private CompletableFuture<Void> future;

    public UdpDiscoveryHandlerBenchmark() {
        try {
            openPingsCache = new HashMap<>();
            uniteAttemptsCache = new HashMap<>();
            peers = new HashMap<>();
            directConnectionPeers = new HashSet<>();
            handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, peers, directConnectionPeers);

            ctx = new MyHandlerContext();
            recipient = new MyAddress();
            final byte[] payload = new byte[1024];
            new Random().nextBytes(payload);
            msg = new SerializedApplicationMessage(CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"), CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4"), byte[].class, payload);
            future = new CompletableFuture<>();

            directConnectionPeers.add(msg.getRecipient());
            final Peer peer = new Peer();
            peer.inboundPongOccurred();
            peer.setAddress(InetSocketAddressWrapper.of(InetSocketAddress.createUnresolved("127.0.0.1", 25527)));
            peers.put(msg.getRecipient(), peer);
        }
        catch (final CryptoException e) {
            e.printStackTrace();
        }
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void matchedWrite() {
        handler.matchedWrite(ctx, msg.getRecipient(), msg, future);
    }

    private static class MyHandlerContext implements HandlerContext {
        private final DrasylConfig config;
        private final Identity identity;
        private final PeersManager peersManager;

        public MyHandlerContext() throws CryptoException {
            config = DrasylConfig.newBuilder()
                    .remotePingTimeout(ofDays(1))
                    .remotePingCommunicationTimeout(ofDays(1))
                    .build();
            identity = Identity.of(ProofOfWork.of(-2109504681),
                    CompressedKeyPair.of("AhqX0pwBbAthIabf+05czWQjaxb5mmqWU4IdG3RHOuQh",
                            "BRRzAewqH0MnNvidynNzpOwdfbXYzOjBHhWEO/ZcgsU="));
            peersManager = new PeersManager(event -> {
            }, identity);
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
            return peersManager;
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
