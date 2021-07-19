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
package org.drasyl.remote.protocol;

import com.google.protobuf.ByteString;
import com.goterl.lazysodium.utils.SessionPair;
import org.drasyl.AbstractBenchmark;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.remote.handler.crypto.AgreementId;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;

import static org.drasyl.remote.protocol.Nonce.randomNonce;
import static org.drasyl.util.RandomUtil.randomBytes;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@State(Scope.Benchmark)
public class ArmedMessageBenchmark extends AbstractBenchmark {
    private ArmedMessage armedMessage;
    private SessionPair sessionPair;

    @Setup
    public void setup() {
        try {
            final AgreementId agreementId = AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey());
            final ApplicationMessage message = ApplicationMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), agreementId, String.class.getSimpleName(), ByteString.copyFrom(randomBytes(1024)));
            sessionPair = Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey());
            armedMessage = message.arm(new SessionPair(sessionPair.getTx(), sessionPair.getRx())); // we must invert the session pair for encryption
        }
        catch (final CryptoException | InvalidMessageFormatException e) {
            handleUnexpectedException(e);
        }
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void disarm(final Blackhole blackhole) {
        try {
            final FullReadMessage<?> disarmedMessage = armedMessage.disarm(sessionPair);
            blackhole.consume(disarmedMessage);
        }
        catch (final IOException e) {
            handleUnexpectedException(e);
        }
    }
}
