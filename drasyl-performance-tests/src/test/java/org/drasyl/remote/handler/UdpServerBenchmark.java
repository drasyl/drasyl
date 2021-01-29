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
package org.drasyl.remote.handler;

import org.drasyl.DrasylConfig;
import org.drasyl.event.Node;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedKeyPair;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.remote.protocol.AddressedByteBuf;
import org.drasyl.util.ReferenceCountUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.drasyl.DrasylNode.getBestEventLoop;

@State(Scope.Benchmark)
@Fork(value = 1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
public class UdpServerBenchmark {
    private DatagramSocket socket;
    private static CompletableFuture<Void>[] futures;
    private final static AtomicInteger THREAD_INDEX = new AtomicInteger(0);

    @SuppressWarnings("unchecked")
    public UdpServerBenchmark() {
        try {
            futures = new CompletableFuture[Runtime.getRuntime().availableProcessors()];
            socket = new DatagramSocket();

            final Identity identity2 = Identity.of(ProofOfWork.of(-2145822673),
                    CompressedKeyPair.of("AgUAcj2PUQ8jqQpF4yANhFuPUlwSWpuzb9gIX6rzkc6g",
                            "DkEGET4hDK87hwVhGN8wl9SIL0cSKcY0MRsa3LrV0/U="));

            final UdpServer handler = new UdpServer(getBestEventLoop(2));

            final DrasylConfig config2 = DrasylConfig.newBuilder()
                    .remoteBindPort(22528)
                    .identityProofOfWork(identity2.getProofOfWork())
                    .identityPublicKey(identity2.getPublicKey())
                    .identityPrivateKey(identity2.getPrivateKey())
                    .build();

            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config2, identity2, new PeersManager((e) -> {
            }, identity2),
                    TypeValidator.ofInboundValidator(config2),
                    TypeValidator.ofOutboundValidator(config2),
                    new SimpleInboundHandler<AddressedByteBuf, Address>() {
                        @Override
                        protected void matchedRead(final HandlerContext ctx,
                                                   final Address sender,
                                                   final AddressedByteBuf msg,
                                                   final CompletableFuture<Void> future) {
                            try {
                                final int index = msg.getContent().readableBytes();
                                futures[index].complete(null);
                                future.complete(null);
                            }
                            finally {
                                ReferenceCountUtil.safeRelease(msg);
                            }
                        }
                    }, handler);

            pipeline.processInbound(new NodeUpEvent(Node.of(identity2))).join();
        }
        catch (final Exception e) {
            e.printStackTrace();
        }
    }

    @State(Scope.Thread)
    public static class ThreadState {
        private DatagramPacket packet;
        private int index;

        public ThreadState() {
            try {
                index = THREAD_INDEX.getAndIncrement();
                packet = new DatagramPacket(new byte[index], index, InetAddress.getLocalHost(), 22528);
            }
            catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Benchmark
    @Threads(Threads.MAX)
    @BenchmarkMode(Mode.Throughput)
    public void receiveMaxThreads(final ThreadState state) {
        try {
            futures[state.index] = new CompletableFuture<>();
            socket.send(state.packet);
            futures[state.index].join();
        }
        catch (final IOException e) {
            e.printStackTrace();
        }
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void receiveSingleThread(final ThreadState state) {
        try {
            futures[state.index] = new CompletableFuture<>();
            socket.send(state.packet);
            futures[state.index].join();
        }
        catch (final IOException e) {
            e.printStackTrace();
        }
    }
}
