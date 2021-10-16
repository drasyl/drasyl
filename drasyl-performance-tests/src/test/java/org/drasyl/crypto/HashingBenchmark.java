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
package org.drasyl.crypto;

import com.google.common.hash.HashFunction;
import org.drasyl.AbstractBenchmark;
import org.drasyl.util.RandomUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class HashingBenchmark extends AbstractBenchmark {
    @Param({ "32", "1432" })
    private int size;
    private byte[] message;
    private HashFunction googleSha256;
    private HashFunction googleSha512;

    @Setup
    public void setup() {
        message = RandomUtil.randomBytes(size);
        googleSha256 = com.google.common.hash.Hashing.sha256();
        googleSha512 = com.google.common.hash.Hashing.sha512();
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void googleSHA256(final Blackhole blackhole) {
        blackhole.consume(googleSha256.hashBytes(message));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void googleSHA512(final Blackhole blackhole) {
        blackhole.consume(googleSha512.hashBytes(message));
    }
}
