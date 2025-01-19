/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.remote.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.AbstractBenchmark;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import static org.drasyl.util.RandomUtil.randomBytes;
import static org.openjdk.jmh.annotations.Level.Invocation;
import static util.IdentityTestUtil.ID_1;
import static util.IdentityTestUtil.ID_2;

@Fork(value = 1, jvmArgsPrepend = {
        "-Dio.netty.leakDetectionLevel=DISABLED",
        "-Dorg.drasyl.nonce.pseudorandom=true"
})
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
public class ApplicationMessageBenchmark extends AbstractBenchmark {
    public static final PooledByteBufAllocator ALLOC = PooledByteBufAllocator.DEFAULT;
    public static final HopCount HOP_COUNT = HopCount.of();
    public static final boolean IS_ARMED = false;
    public static final int NETWORK_ID = 0;
    public static final IdentityPublicKey RECIPIENT = ID_2.getIdentityPublicKey();
    public static final IdentityPublicKey SENDER = ID_1.getIdentityPublicKey();
    public static final ProofOfWork PROOF_OF_WORK = ID_1.getProofOfWork();
    public static final ByteBuf PAYLOAD = ALLOC.buffer(1024).writeBytes(randomBytes(1024));
    private ByteBuf buf;

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void encodeMessage(final Blackhole blackhole) {
        final ApplicationMessage application = ApplicationMessage.of(HOP_COUNT, IS_ARMED, NETWORK_ID, Nonce.randomNonce(), RECIPIENT, SENDER, PROOF_OF_WORK, PAYLOAD);
        buf = application.encodeMessage(ALLOC);
        blackhole.consume(buf);
    }

    @TearDown(Invocation)
    public void tearDown() {
        ReferenceCountUtil.safeRelease(buf);
    }
}
