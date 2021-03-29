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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class ProofOfWorkBenchmark {
    private CompressedPublicKey publicKey;

    @Setup
    public void setup() {
        publicKey = CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9");
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty01(final Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 1));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty02(final Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 2));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty03(final Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 3));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty04(final Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 4));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty05(final Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 5));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty06(final Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 6));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty07(final Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 7));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty08(final Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 8));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty09(final Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 9));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty10(final Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 10));
    }
}
