/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.remote;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import org.drasyl.AbstractBenchmark;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.crypto.CryptoException;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.InvalidMessageFormatException;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;

import static io.netty.buffer.UnpooledByteBufAllocator.DEFAULT;
import static org.drasyl.util.RandomUtil.randomBytes;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@State(Scope.Benchmark)
public class ApplicationMessageToPayloadCodecBenchmark extends AbstractBenchmark {
    private static final int NETWORK_ID = 0;
    private static final int MSG_LEN = 1380;
    private ApplicationMessageToPayloadCodec handler;
    private List<Object> out;
    private AddressedEnvelope<ApplicationMessage, ?> msg;

    @Setup
    public void setup() throws CryptoException, InvalidMessageFormatException {
        handler = new ApplicationMessageToPayloadCodec(0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork());
    }

    @Setup(Level.Invocation)
    public void setupChannelRead() {
        out = new ArrayList<>();
        final ByteBuf payload = DEFAULT.buffer(MSG_LEN).writeBytes(randomBytes(MSG_LEN));
        msg = new OverlayAddressedMessage<>(ApplicationMessage.of(NETWORK_ID, ID_1.getIdentityPublicKey(), ID_2.getIdentityPublicKey(), ID_2.getProofOfWork(), payload), ID_2.getIdentityPublicKey());
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void decode(final Blackhole blackhole) {
        handler.decode(null, msg, out);
        blackhole.consume(out);
    }
}
