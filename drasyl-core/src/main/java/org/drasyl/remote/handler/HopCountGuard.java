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
import org.drasyl.pipeline.handler.OutboundMessageFilter;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;

import static org.drasyl.util.LoggingUtil.sanitizeLogArg;

/**
 * This handler ensures that {@link RemoteEnvelope}s do not infinitely circulate in the network. It
 * increments the hop counter of each outgoing message. If the limit of hops is reached, the message
 * is discarded. Otherwise the message can pass.
 */
@Stateless
public final class HopCountGuard extends OutboundMessageFilter<RemoteEnvelope<? extends MessageLite>, Address> {
    public static final HopCountGuard INSTANCE = new HopCountGuard();
    private static final Logger LOG = LoggerFactory.getLogger(HopCountGuard.class);

    private HopCountGuard() {
        // singleton
    }

    @Override
    protected boolean accept(final HandlerContext ctx,
                             final Address recipient,
                             final RemoteEnvelope<? extends MessageLite> msg) throws Exception {
        if (msg.getHopCount() < ctx.config().getRemoteMessageHopLimit()) {
            // route message to next hop (node)
            msg.incrementHopCount();

            return true;
        }
        else {
            return false;
        }
    }

    @Override
    protected void messageRejected(final HandlerContext ctx,
                                   final Address sender,
                                   final RemoteEnvelope<? extends MessageLite> msg,
                                   final CompletableFuture<Void> future) {
        // too many hops, discard message
        LOG.debug("Hop Count limit has been reached. End of lifespan of message has been reached. Discard message '{}'", () -> sanitizeLogArg(msg));
        future.completeExceptionally(new Exception("Hop Count limit has been reached. End of lifespan of message has been reached. Discard message."));
    }
}
