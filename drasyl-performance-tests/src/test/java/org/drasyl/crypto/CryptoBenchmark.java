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

import org.drasyl.AbstractBenchmark;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.RandomUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.security.PrivateKey;
import java.security.PublicKey;

@Fork(1)
@Warmup(iterations = 2)
@Measurement(iterations = 2)
@State(Scope.Benchmark)
public class CryptoBenchmark extends AbstractBenchmark {
    @Param({ "1", "256", "5120" })
    private int size;
    private byte[] message;
    private PublicKey publicKey;
    private PrivateKey privateKey;
    private byte[] signature;

    @Setup
    public void setup() {
        message = RandomUtil.randomBytes(size);
        publicKey = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22").toUncompressedKey();
        privateKey = CompressedPrivateKey.of("6b4df6d8b8b509cb984508a681076efce774936c17cf450819e2262a9862f8").toUncompressedKey();
        signature = HexUtil.fromString("304402200525a8e662d3f11fa28524de4bb83812765255db9a4d09ee5b8ede7880a54534022009416ea30daab4f8de3008c31b4ec831a0c163d08f0504b2632a2e7febdcbe06");
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void signMessage(final Blackhole blackhole) {
        try {
            blackhole.consume(Crypto.signMessage(privateKey, message));
        }
        catch (final CryptoException e) {
            handleUnexpectedException(e);
        }
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void verifySignature(final Blackhole blackhole) {
        blackhole.consume(Crypto.verifySignature(publicKey, message, signature));
    }
}
