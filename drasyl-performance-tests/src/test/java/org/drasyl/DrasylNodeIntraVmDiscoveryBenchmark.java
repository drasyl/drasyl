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

@State(Scope.Benchmark)
public class DrasylNodeIntraVmDiscoveryBenchmark extends AbstractBenchmark {
    private static final byte[] testPayload = new byte[1432];
    private DrasylNode node1;
    private DrasylNode node2;

    @Setup
    public void setup() {
        try {
            final Identity identity1 = IdentityTestUtil.ID_1;
            final Identity identity2 = IdentityTestUtil.ID_2;

            final DrasylConfig config1 = DrasylConfig.newBuilder()
                    .identityProofOfWork(identity1.getProofOfWork())
                    .identityPublicKey(identity1.getIdentityPublicKey())
                    .identitySecretKey(identity1.getIdentitySecretKey())
                    .intraVmDiscoveryEnabled(true)
                    .remoteLocalHostDiscoveryEnabled(false)
                    .remoteEnabled(false)
                    .build();
            final DrasylConfig config2 = DrasylConfig.newBuilder()
                    .identityProofOfWork(identity2.getProofOfWork())
                    .identityPublicKey(identity2.getIdentityPublicKey())
                    .identitySecretKey(identity2.getIdentitySecretKey())
                    .intraVmDiscoveryEnabled(true)
                    .remoteLocalHostDiscoveryEnabled(false)
                    .remoteEnabled(false)
                    .build();

            node1 = new DrasylNode(config1) {
                @Override
                public void onEvent(final @NonNull Event event) {
                }
            };

            node2 = new DrasylNode(config2) {
                @Override
                public void onEvent(final @NonNull Event event) {
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
        DrasylNodeSharedEventLoopGroupHolder.shutdown();
        System.err.println("Benchmark stopped.");
    }

    @Benchmark
    @Threads(Threads.MAX)
    @BenchmarkMode(Mode.Throughput)
    public void benchmark() {
        node1.send(node2.identity().getIdentityPublicKey(), testPayload).toCompletableFuture().join();
    }
}
