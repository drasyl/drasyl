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
    public void GenerateProofOfWorkDifficulty01(Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 1));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty02(Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 2));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty03(Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 3));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty04(Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 4));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty05(Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 5));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty06(Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 6));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty07(Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 7));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty08(Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 8));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty09(Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 9));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.AverageTime)
    public void GenerateProofOfWorkDifficulty10(Blackhole blackhole) {
        blackhole.consume(ProofOfWork.generateProofOfWork(publicKey, (byte) 10));
    }
}
