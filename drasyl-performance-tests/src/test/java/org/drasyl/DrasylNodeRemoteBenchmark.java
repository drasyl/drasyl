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

import org.drasyl.annotation.NonNull;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.identity.Identity;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import test.util.IdentityTestUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
public class DrasylNodeRemoteBenchmark extends AbstractBenchmark {
    private DrasylNode node1;
    private DrasylNode node2;
    private static CompletableFuture<Void>[] futures;
    private final static AtomicInteger THREAD_INDEX = new AtomicInteger(0);

    @SuppressWarnings("unchecked")
    @Setup
    public void setup() {
        try {
            futures = new CompletableFuture[Runtime.getRuntime().availableProcessors()];

            final Identity identity1 = IdentityTestUtil.ID_1;
            final Identity identity2 = IdentityTestUtil.ID_2;

            final DrasylConfig config1 = DrasylConfig.newBuilder()
                    .identityProofOfWork(identity1.getProofOfWork())
                    .identityPublicKey(identity1.getIdentityPublicKey())
                    .identitySecretKey(identity1.getIdentitySecretKey())
                    .intraVmDiscoveryEnabled(false)
                    .remoteLocalHostDiscoveryEnabled(false)
                    .remoteEnabled(true)
                    .remoteBindHost(InetAddress.getByName("127.0.0.1"))
                    .remoteBindPort(22528)
                    .remoteSuperPeerEnabled(false)
                    .remoteStaticRoutes(Map.of(identity2.getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", 22529)))
                    .monitoringEnabled(false)
                    .build();
            final DrasylConfig config2 = DrasylConfig.newBuilder()
                    .identityProofOfWork(identity2.getProofOfWork())
                    .identityPublicKey(identity2.getIdentityPublicKey())
                    .identitySecretKey(identity2.getIdentitySecretKey())
                    .intraVmDiscoveryEnabled(false)
                    .remoteBindHost(InetAddress.getByName("127.0.0.1"))
                    .remoteBindPort(22529)
                    .remoteSuperPeerEnabled(false)
                    .remoteStaticRoutes(Map.of(identity1.getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", 22528)))
                    .remoteLocalHostDiscoveryEnabled(false)
                    .remoteEnabled(true)
                    .monitoringEnabled(false)
                    .build();

            final CompletableFuture<Void> node1Ready = new CompletableFuture<>();
            node1 = new DrasylNode(config1) {
                @Override
                public void onEvent(final @NonNull Event event) {
                    if (event instanceof PeerDirectEvent && ((PeerDirectEvent) event).getPeer().getIdentityPublicKey().equals(identity2.getIdentityPublicKey()) && !node1Ready.isDone()) {
                        node1Ready.complete(null);
                    }
                }
            };

            final CompletableFuture<Void> node2Ready = new CompletableFuture<>();
            node2 = new DrasylNode(config2) {
                @Override
                public void onEvent(final @NonNull Event event) {
                    if (event instanceof MessageEvent && Objects.equals(((MessageEvent) event).getSender(), node1.identity().getIdentityPublicKey()) && ((MessageEvent) event).getPayload() instanceof Integer) {
                        final int index = (int) ((MessageEvent) event).getPayload();
                        futures[index].complete(null);
                    }
                    else if (event instanceof PeerDirectEvent && ((PeerDirectEvent) event).getPeer().getIdentityPublicKey().equals(identity1.getIdentityPublicKey()) && !node2Ready.isDone()) {
                        node2Ready.complete(null);
                    }
                }
            };

            node1.start().join();
            node2.start().join();
            node1Ready.join();
            node2Ready.join();
            System.err.println("Benchmark started.");
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    @TearDown
    public void teardown() {
        node1.shutdown().join();
        node2.shutdown().join();
    }

    @State(Scope.Thread)
    public static class ThreadState {
        private int index;

        public ThreadState() {
            try {
                index = THREAD_INDEX.getAndIncrement();
            }
            catch (final Exception e) {
                handleUnexpectedException(e);
            }
        }
    }

    @Benchmark
    @Threads(Threads.MAX)
    @BenchmarkMode(Mode.Throughput)
    public void benchmark(final ThreadState state) {
        futures[state.index] = new CompletableFuture<>();
        node1.send(node2.identity().getIdentityPublicKey(), state.index);
        futures[state.index].join();
    }

    @Override
    protected int getForks() {
        return 1;
    }
}
