/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
import org.drasyl.crypto.HexUtil;
import org.drasyl.util.ImmutableByteArray;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class IdentityPublicKeyBenchmark extends AbstractBenchmark {
    private byte[] bytes;
    private String hexString;
    private ImmutableByteArray immutableBytes;
    private IdentityPublicKey publicKey;
    private IdentityPublicKey publicKey2;

    @Setup
    public void setup() {
        hexString = "18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127";
        bytes = HexUtil.fromString(hexString);
        immutableBytes = ImmutableByteArray.of(bytes);
        publicKey = IdentityPublicKey.of(hexString);
        publicKey2 = IdentityPublicKey.of("6377d1139c212ee1649a82e0388d55dbdf68ebce4bde031945b60c2e8ac94dc7");
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void ofString(final Blackhole blackhole) {
        blackhole.consume(IdentityPublicKey.of(hexString));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void ofBytes(final Blackhole blackhole) {
        blackhole.consume(IdentityPublicKey.of(bytes));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void ofImmutableBytes(final Blackhole blackhole) {
        blackhole.consume(IdentityPublicKey.of(immutableBytes));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void benchmarkHashCode(final Blackhole blackhole) {
        blackhole.consume(publicKey.hashCode());
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void autoValueHashCode(final Blackhole blackhole) {
        blackhole.consume(publicKey.getBytes().hashCode());
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void benchmarkEquals(final Blackhole blackhole) {
        blackhole.consume(publicKey.equals(publicKey2));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void autoValueEquals(final Blackhole blackhole) {
        blackhole.consume(publicKey.getBytes().equals(publicKey2.getBytes()));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void toByteArray(final Blackhole blackhole) {
        blackhole.consume(publicKey.toByteArray());
    }
}
