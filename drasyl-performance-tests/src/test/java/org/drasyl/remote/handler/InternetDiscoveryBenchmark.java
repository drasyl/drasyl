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
import org.drasyl.AbstractBenchmark;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.remote.handler.InternetDiscovery.Peer;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.remote.protocol.Nonce;
import org.drasyl.util.Pair;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import test.util.IdentityTestUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.time.Duration.ofDays;

@State(Scope.Benchmark)
public class InternetDiscoveryBenchmark extends AbstractBenchmark {
    private Map<Nonce, InternetDiscovery.Ping> openPingsCache;
    private Map<Pair<IdentityPublicKey, IdentityPublicKey>, Boolean> uniteAttemptsCache;
    private Map<IdentityPublicKey, Peer> peers;
    private Set<IdentityPublicKey> directConnectionPeers;
    private InternetDiscovery handler;
    private HandlerContext ctx;
    private Address recipient;
    private ApplicationMessage msg;
    private CompletableFuture<Void> future;
    private Set<IdentityPublicKey> superPeers;
    private IdentityPublicKey bestSuperPeer;

    @Setup
    public void setup() {
        openPingsCache = new HashMap<>();
        uniteAttemptsCache = new HashMap<>();
        peers = new HashMap<>();
        directConnectionPeers = new HashSet<>();
        superPeers = new HashSet<>();
        handler = new InternetDiscovery(openPingsCache, uniteAttemptsCache, peers, directConnectionPeers, superPeers, bestSuperPeer);

        ctx = new MyHandlerContext();
        recipient = new MyAddress();
        final IdentityPublicKey recipient = IdentityTestUtil.ID_1.getIdentityPublicKey();
        msg = ApplicationMessage.of(1, IdentityTestUtil.ID_2.getIdentityPublicKey(), IdentityTestUtil.ID_2.getProofOfWork(), recipient, byte[].class.getName(), ByteString.EMPTY);

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
        try {
            handler.matchedOutbound(ctx, recipient, msg, future);
        }
        catch (final IOException e) {
            handleUnexpectedException(e);
        }
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
            identity = IdentityTestUtil.ID_1;
            peersManager = new PeersManager(identity);
        }

        @Override
        public ByteBuf alloc() {
            return null;
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
