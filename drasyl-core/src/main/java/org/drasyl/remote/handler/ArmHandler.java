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
package org.drasyl.remote.handler;

import com.google.protobuf.MessageLite;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.handler.codec.MessageToMessageCodec;
import org.drasyl.remote.protocol.RemoteEnvelope;

import java.util.List;

/**
 * Arms (sign/encrypt) outbound and disarms (verify/decrypt) inbound messages. Considers only
 * messages that are addressed from or to us. Messages that could not be (dis)armed) are dropped.
 */
@Stateless
@SuppressWarnings({ "java:S110" })
public final class ArmHandler extends MessageToMessageCodec<RemoteEnvelope<? extends MessageLite>, RemoteEnvelope<? extends MessageLite>, Address> {
    public static final ArmHandler INSTANCE = new ArmHandler();

    private ArmHandler() {
        // singleton
    }

    @Override
    protected void decode(final HandlerContext ctx,
                          final Address sender,
                          final RemoteEnvelope<? extends MessageLite> msg,
                          final List<Object> out) throws Exception {
        if (ctx.identity().getPublicKey().equals(msg.getRecipient())) {
            // disarm all messages addressed to us
            out.add(msg.disarm(ctx.identity().getPrivateKey()));
        }
        else {
            out.add(msg.retain());
        }
    }

    @Override
    protected void encode(final HandlerContext ctx,
                          final Address recipient,
                          final RemoteEnvelope<? extends MessageLite> msg,
                          final List<Object> out) throws Exception {
        if (ctx.identity().getPublicKey().equals(msg.getSender())) {
            // arm all messages from us
            out.add(msg.arm(ctx.identity().getPrivateKey()));
        }
        else {
            out.add(msg.retain());
        }
    }
}
