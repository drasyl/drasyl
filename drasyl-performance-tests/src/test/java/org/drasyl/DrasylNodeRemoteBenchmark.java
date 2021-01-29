/*
 * Copyright (c) 2021.
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
package org.drasyl;

import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.identity.CompressedKeyPair;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
@Fork(value = 1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
public class DrasylNodeRemoteBenchmark {
    private DrasylNode node1;
    private DrasylNode node2;
    private static CompletableFuture<Void>[] futures;
    private final static AtomicInteger THREAD_INDEX = new AtomicInteger(0);

    @SuppressWarnings("unchecked")
    public DrasylNodeRemoteBenchmark() {
        try {
            futures = new CompletableFuture[Runtime.getRuntime().availableProcessors()];

            final Identity identity1 = Identity.of(ProofOfWork.of(-2109504681),
                    CompressedKeyPair.of("AhqX0pwBbAthIabf+05czWQjaxb5mmqWU4IdG3RHOuQh",
                            "BRRzAewqH0MnNvidynNzpOwdfbXYzOjBHhWEO/ZcgsU="));
            final Identity identity2 = Identity.of(ProofOfWork.of(-2145822673),
                    CompressedKeyPair.of("AgUAcj2PUQ8jqQpF4yANhFuPUlwSWpuzb9gIX6rzkc6g",
                            "DkEGET4hDK87hwVhGN8wl9SIL0cSKcY0MRsa3LrV0/U="));

            final DrasylConfig config1 = DrasylConfig.newBuilder()
                    .identityProofOfWork(identity1.getProofOfWork())
                    .identityPublicKey(identity1.getPublicKey())
                    .identityPrivateKey(identity1.getPrivateKey())
                    .intraVmDiscoveryEnabled(false)
                    .localHostDiscoveryEnabled(false)
                    .remoteEnabled(true)
                    .monitoringEnabled(false)
                    .build();
            final DrasylConfig config2 = DrasylConfig.newBuilder()
                    .identityProofOfWork(identity2.getProofOfWork())
                    .identityPublicKey(identity2.getPublicKey())
                    .identityPrivateKey(identity2.getPrivateKey())
                    .intraVmDiscoveryEnabled(false)
                    .localHostDiscoveryEnabled(false)
                    .remoteEnabled(true)
                    .monitoringEnabled(false)
                    .build();

            final CompletableFuture<Void> node1Online = new CompletableFuture<>();
            node1 = new DrasylNode(config1) {
                @Override
                public void onEvent(final @NotNull Event event) {
                    if (event instanceof NodeOnlineEvent && !node1Online.isDone()) {
                        node1Online.complete(null);
                    }
                }
            };

            final CompletableFuture<Void> node2Online = new CompletableFuture<>();
            final CompletableFuture<Void> directConnection = new CompletableFuture<>();
            node2 = new DrasylNode(config2) {
                @Override
                public void onEvent(final @NotNull Event event) {
                    if (event instanceof MessageEvent && Objects.equals(((MessageEvent) event).getSender(), node1.identity().getPublicKey()) && ((MessageEvent) event).getPayload() instanceof Integer) {
                        final int index = (int) ((MessageEvent) event).getPayload();
                        futures[index].complete(null);
                    }
                    else if (event instanceof NodeOnlineEvent && !node2Online.isDone()) {
                        node2Online.complete(null);
                    }
                    else if (event instanceof PeerDirectEvent && ((PeerDirectEvent) event).getPeer().getPublicKey().equals(node1.identity().getPublicKey()) && !directConnection.isDone()) {
                        directConnection.complete(null);
                    }
                }
            };

            node1.start().join();
            node2.start().join();
            node1Online.join();
            node2Online.join();
            node1.send(node2.identity().getPublicKey(), new byte[0]);
            directConnection.join();
            System.err.println("Benchmark started.");
        }
        catch (final Exception e) {
            e.printStackTrace();
        }
    }

    @State(Scope.Thread)
    public static class ThreadState {
        private int index;

        public ThreadState() {
            try {
                index = THREAD_INDEX.getAndIncrement();
            }
            catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Benchmark
    @Threads(Threads.MAX)
    @BenchmarkMode(Mode.Throughput)
    public void benchmark(final ThreadState state) {
        futures[state.index] = new CompletableFuture<>();
        node1.send(node2.identity().getPublicKey(), state.index);
        futures[state.index].join();
    }
}
