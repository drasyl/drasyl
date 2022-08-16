/*
 * Copyright (c) 2020.
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
package org.drasyl.peer.connection.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.Message;
import org.mockito.Answers;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.ArrayList;
import java.util.Random;

import static org.mockito.Mockito.mock;

@State(Scope.Benchmark)
//@Fork(value = 1)
//@Warmup(iterations = 3)
//@Measurement(iterations = 3)
public class MessageEncoderBenchmark {
    private final ChannelHandlerContext ctx;
    private final Message msg;

    public MessageEncoderBenchmark() {
        try {
            ctx = mock(ChannelHandlerContext.class, Answers.RETURNS_DEEP_STUBS);
            final CompressedPublicKey sender = CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
            final ProofOfWork proofOfWork = ProofOfWork.of(6657650);
            final CompressedPublicKey recipient = CompressedPublicKey.of("033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55");
            final byte[] payload = new byte[1024 * 1024]; // 1 MB
            new Random().nextBytes(payload);
            msg = new ApplicationMessage(1, sender, proofOfWork, recipient, payload);
        }
        catch (final CryptoException e) {
            throw new RuntimeException(e);
        }
    }

    @Benchmark
    public void encode() throws MessageDecoderException {
        final ArrayList<Object> out = new ArrayList<>();
        MessageEncoder.INSTANCE.encode(ctx, msg, out);
        ((BinaryWebSocketFrame) out.get(0)).release();
    }
}