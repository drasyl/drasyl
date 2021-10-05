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

import com.goterl.lazysodium.utils.SessionPair;
import org.drasyl.AbstractBenchmark;
import org.drasyl.handler.remote.protocol.Nonce;
import org.drasyl.identity.KeyAgreementPublicKey;
import org.drasyl.identity.KeyAgreementSecretKey;
import org.drasyl.identity.KeyPair;
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
import test.util.IdentityTestUtil;

import java.util.Arrays;

@State(Scope.Benchmark)
public class EncryptionBenchmark extends AbstractBenchmark {
    @Param({ "1", "256", "1432", "5120" })
    private int size;
    private byte[] message;
    private KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> alice;
    private SessionPair sessionAlice;
    private SessionPair sessionBob;
    private byte[] encrypted;
    private Nonce nonce;

    @Setup
    public void setup() {
        try {
            alice = IdentityTestUtil.ID_1.getKeyAgreementKeyPair();
            final KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> bob = IdentityTestUtil.ID_2.getKeyAgreementKeyPair();
            sessionAlice = Crypto.INSTANCE.generateSessionKeyPair(alice, bob.getPublicKey());
            sessionBob = Crypto.INSTANCE.generateSessionKeyPair(bob, alice.getPublicKey());
            assert Arrays.equals(sessionAlice.getTx(), sessionBob.getRx()) : "Session key not valid!";
            assert Arrays.equals(sessionAlice.getRx(), sessionBob.getTx()) : "Session key not valid!";
            message = RandomUtil.randomBytes(size);
            nonce = Nonce.randomNonce();
            encrypted = Crypto.INSTANCE.encrypt(message, new byte[0], nonce, sessionAlice);
        }
        catch (final CryptoException e) {
            handleUnexpectedException(e);
        }
    }

    @Benchmark
    @Threads(Threads.MAX)
    @BenchmarkMode(Mode.Throughput)
    public void encryptWithNonce(final Blackhole blackhole) {
        try {
            blackhole.consume(Crypto.INSTANCE.encrypt(message, new byte[0], Nonce.randomNonce(), sessionAlice));
        }
        catch (final CryptoException e) {
            handleUnexpectedException(e);
        }
    }

    @Benchmark
    @Threads(Threads.MAX)
    @BenchmarkMode(Mode.Throughput)
    public void encryptWithoutNonce(final Blackhole blackhole) {
        try {
            blackhole.consume(Crypto.INSTANCE.encrypt(message, new byte[0], nonce, sessionAlice));
        }
        catch (final CryptoException e) {
            handleUnexpectedException(e);
        }
    }

    @Benchmark
    @Threads(Threads.MAX)
    @BenchmarkMode(Mode.Throughput)
    public void decrypt(final Blackhole blackhole) {
        try {
            blackhole.consume(Crypto.INSTANCE.decrypt(encrypted, new byte[0], nonce, sessionBob));
        }
        catch (final CryptoException e) {
            handleUnexpectedException(e);
        }
    }
}
