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
import org.drasyl.pipeline.handler.filter.InboundMessageFilter;
import org.drasyl.remote.protocol.RemoteEnvelope;

import java.util.concurrent.CompletableFuture;

/**
 * This handler filters out all messages received from other networks.
 */
@SuppressWarnings("java:S110")
@Stateless
public final class OtherNetworkFilter extends InboundMessageFilter<RemoteEnvelope<? extends MessageLite>, Address> {
    public static final OtherNetworkFilter INSTANCE = new OtherNetworkFilter();

    private OtherNetworkFilter() {
        // singleton
    }

    @Override
    protected boolean accept(final HandlerContext ctx,
                             final Address sender,
                             final RemoteEnvelope<? extends MessageLite> msg) throws Exception {
        return msg.isChunk() || ctx.config().getNetworkId() == msg.getNetworkId();
    }

    @SuppressWarnings("java:S112")
    @Override
    protected void messageRejected(final HandlerContext ctx,
                                   final Address sender,
                                   final RemoteEnvelope<? extends MessageLite> msg,
                                   final CompletableFuture<Void> future) throws Exception {
        throw new Exception("Message from other network dropped");
    }
}
