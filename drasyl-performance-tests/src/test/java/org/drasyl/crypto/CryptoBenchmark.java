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
