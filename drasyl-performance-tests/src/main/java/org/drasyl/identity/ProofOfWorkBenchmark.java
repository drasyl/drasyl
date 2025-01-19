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
package org.drasyl.identity;

import org.drasyl.AbstractBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;
import org.drasyl.performance.IdentityBenchmarkUtil;

import static org.drasyl.identity.Identity.POW_DIFFICULTY;

@State(Scope.Benchmark)
public class ProofOfWorkBenchmark extends AbstractBenchmark {
    private IdentityPublicKey publicKey;
    private ProofOfWork invalidProofOfWork;
    private ProofOfWork validProofOfWork;

    @Setup
    public void setup() {
        publicKey = IdentityBenchmarkUtil.ID_1.getIdentityPublicKey();
        invalidProofOfWork = ProofOfWork.of(1);
        validProofOfWork = IdentityBenchmarkUtil.ID_1.getProofOfWork();
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void validationInvalid(final Blackhole blackhole) {
        blackhole.consume(invalidProofOfWork.isValid(publicKey, (byte) 1));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void validationValid(final Blackhole blackhole) {
        blackhole.consume(validProofOfWork.isValid(publicKey, POW_DIFFICULTY));
    }
}
