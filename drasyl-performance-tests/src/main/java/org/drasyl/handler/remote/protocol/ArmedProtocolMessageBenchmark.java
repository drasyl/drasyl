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
package org.drasyl.handler.remote.protocol;

import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.drasyl.AbstractBenchmark;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.sodium.SessionPair;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;

import static org.drasyl.util.RandomUtil.randomBytes;
import static util.IdentityBenchmarkUtil.ID_1;
import static util.IdentityBenchmarkUtil.ID_2;

@State(Scope.Benchmark)
public class ArmedProtocolMessageBenchmark extends AbstractBenchmark {
    private ArmedProtocolMessage armedMessage;
    private SessionPair sessionPair;

    @Setup
    public void setup() {
        try {
            final ApplicationMessage message = ApplicationMessage.of(HopCount.of(), false, 0, Nonce.randomNonce(), ID_2.getIdentityPublicKey(), ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), Unpooled.wrappedBuffer(randomBytes(1024)));
            sessionPair = Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey());
            armedMessage = message.arm(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, SessionPair.of(sessionPair.getTx(), sessionPair.getRx())); // we must invert the session pair for encryption
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
            final FullReadMessage<?> disarmedMessage = armedMessage.disarm(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, sessionPair);
            blackhole.consume(disarmedMessage);
        }
        catch (final IOException e) {
            handleUnexpectedException(e);
        }
    }
}
