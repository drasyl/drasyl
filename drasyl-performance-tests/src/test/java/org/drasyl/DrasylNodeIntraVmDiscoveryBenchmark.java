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
package org.drasyl;

import org.drasyl.event.Event;
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
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
public class DrasylNodeIntraVmDiscoveryBenchmark extends AbstractBenchmark {
    private static final byte[] testPayload = new byte[1432];
    private DrasylNode node1;
    private DrasylNode node2;

    @Setup
    public void setup() {
        try {
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
                    .intraVmDiscoveryEnabled(true)
                    .localHostDiscoveryEnabled(false)
                    .remoteEnabled(false)
                    .monitoringEnabled(false)
                    .build();
            final DrasylConfig config2 = DrasylConfig.newBuilder()
                    .identityProofOfWork(identity2.getProofOfWork())
                    .identityPublicKey(identity2.getPublicKey())
                    .identityPrivateKey(identity2.getPrivateKey())
                    .intraVmDiscoveryEnabled(true)
                    .localHostDiscoveryEnabled(false)
                    .remoteEnabled(false)
                    .monitoringEnabled(false)
                    .build();

            node1 = new DrasylNode(config1) {
                @Override
                public void onEvent(final @NotNull Event event) {
                }
            };

            node2 = new DrasylNode(config2) {
                @Override
                public void onEvent(final @NotNull Event event) {
                }
            };

            node1.start().join();
            node2.start().join();
            System.err.println("Benchmark started.");
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    @TearDown
    public void tearDown() {
        node1.shutdown().join();
        node2.shutdown().join();
        DrasylNode.irrevocablyTerminate();
        System.err.println("Benchmark stopped.");
    }

    @Benchmark
    @Threads(Threads.MAX)
    @BenchmarkMode(Mode.Throughput)
    public void benchmark() {
        node1.send(node2.identity().getPublicKey(), testPayload).join();
    }
}
