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
package org.drasyl.channel;

import com.google.protobuf.ByteString;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.ApplicationMessage;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * This codec converts {@link ApplicationMessage}s to {@link ByteString}s and vice vera.
 */
public class ApplicationMessageCodec extends MessageToMessageCodec<AddressedMessage<?, ?>, AddressedMessage<?, ?>> {
    private final int networkId;
    private final IdentityPublicKey myPublicKey;
    private final ProofOfWork myProofOfWork;

    public ApplicationMessageCodec(final int networkId,
                                   final IdentityPublicKey myPublicKey,
                                   final ProofOfWork myProofOfWork) {
        this.networkId = networkId;
        this.myPublicKey = requireNonNull(myPublicKey);
        this.myProofOfWork = requireNonNull(myProofOfWork);
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final AddressedMessage<?, ?> msg,
                          final List<Object> out) throws Exception {
        if (msg.message() instanceof ByteBuf && msg.address() instanceof IdentityPublicKey) {
            final ApplicationMessage wrappedMsg = ApplicationMessage.of(networkId, myPublicKey, myProofOfWork, (IdentityPublicKey) msg.address(), ((ByteBuf) msg.message()).retain());
            out.add(new AddressedMessage<>(wrappedMsg, msg.address()));
        }
        else {
            // pass through message
            out.add(msg.retain());
        }
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final AddressedMessage<?, ?> msg,
                          final List<Object> out) {
        if (msg.message() instanceof ApplicationMessage) {
            final AddressedMessage<ByteBuf, ?> unwrappedMsg = new AddressedMessage<>(((ApplicationMessage) msg.message()).getPayload().retain(), msg.address());
            out.add(unwrappedMsg);
        }
        else {
            // pass through message
            out.add(msg.retain());
        }
    }
}
