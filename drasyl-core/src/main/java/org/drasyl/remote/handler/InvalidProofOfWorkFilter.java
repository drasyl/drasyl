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
import org.drasyl.pipeline.handler.InboundMessageFilter;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;

import static org.drasyl.identity.IdentityManager.POW_DIFFICULTY;
import static org.drasyl.util.LoggingUtil.sanitizeLogArg;

/**
 * This handler filters out all messages received with invalid proof of work.
 */
@SuppressWarnings("java:S110")
@Stateless
public final class InvalidProofOfWorkFilter extends InboundMessageFilter<RemoteEnvelope<? extends MessageLite>, Address> {
    public static final InvalidProofOfWorkFilter INSTANCE = new InvalidProofOfWorkFilter();
    private static final Logger LOG = LoggerFactory.getLogger(InvalidProofOfWorkFilter.class);

    private InvalidProofOfWorkFilter() {
        // singleton
    }

    @Override
    protected boolean accept(final HandlerContext ctx,
                             final Address sender,
                             final RemoteEnvelope<? extends MessageLite> msg) throws Exception {
        return !ctx.identity().getIdentityPublicKey().equals(msg.getRecipient()) || msg.getProofOfWork().isValid(msg.getSender(), POW_DIFFICULTY);
    }

    @Override
    protected void messageRejected(final HandlerContext ctx,
                                   final Address sender,
                                   final RemoteEnvelope<? extends MessageLite> msg,
                                   final CompletableFuture<Void> future) {
        LOG.trace("Message with invalid proof of work dropped: `{}`", () -> sanitizeLogArg(msg));
        future.completeExceptionally(new Exception("Message with invalid proof of work dropped."));
    }
}
