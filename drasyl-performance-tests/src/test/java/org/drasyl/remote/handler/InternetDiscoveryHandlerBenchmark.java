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

import org.drasyl.AbstractBenchmark;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedKeyPair;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.remote.handler.InternetDiscoveryHandler.Peer;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.remote.protocol.MessageId;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.util.Pair;
import org.drasyl.util.RandomUtil;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.time.Duration.ofDays;

@State(Scope.Benchmark)
public class InternetDiscoveryHandlerBenchmark extends AbstractBenchmark {
    private Map<MessageId, InternetDiscoveryHandler.Ping> openPingsCache;
    private Map<Pair<CompressedPublicKey, CompressedPublicKey>, Boolean> uniteAttemptsCache;
    private Map<CompressedPublicKey, Peer> peers;
    private Set<CompressedPublicKey> directConnectionPeers;
    private InternetDiscoveryHandler handler;
    private HandlerContext ctx;
    private Address recipient;
    private IntermediateEnvelope<Application> msg;
    private CompletableFuture<Void> future;
    private Set<CompressedPublicKey> superPeers;
    private CompressedPublicKey bestSuperPeer;

    @Setup
    public void setup() {
        openPingsCache = new HashMap<>();
        uniteAttemptsCache = new HashMap<>();
        peers = new HashMap<>();
        directConnectionPeers = new HashSet<>();
        superPeers = new HashSet<>();
        handler = new InternetDiscoveryHandler(openPingsCache, uniteAttemptsCache, peers, directConnectionPeers, superPeers, bestSuperPeer);

        ctx = new MyHandlerContext();
        recipient = new MyAddress();
        final byte[] payload = RandomUtil.randomBytes(1024);
        final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
        msg = IntermediateEnvelope.application(1, CompressedPublicKey.of("0248b7221b49775dcae85b02fdc9df41fbed6236c72c5c0356b59961190d3f8a13"), ProofOfWork.of(16425882), CompressedPublicKey.of("0248b7221b49775dcae85b02fdc9df41fbed6236c72c5c0356b59961190d3f8a13"), byte[].class.getName(), new byte[]{});

        future = new CompletableFuture<>();

        directConnectionPeers.add(recipient);
        final Peer peer = new Peer();
        peer.inboundPingOccurred();
        peer.setAddress(new InetSocketAddressWrapper("127.0.0.1", 25527));
        peers.put(recipient, peer);
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void matchedWrite() {
        handler.matchedOutbound(ctx, recipient, msg, future);
    }

    private static class MyHandlerContext implements HandlerContext {
        private final DrasylConfig config;
        private final Identity identity;
        private final PeersManager peersManager;

        public MyHandlerContext() {
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
