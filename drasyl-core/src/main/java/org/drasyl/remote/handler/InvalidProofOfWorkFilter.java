/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.remote.handler;

import com.google.protobuf.MessageLite;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.identity.IdentityManager.POW_DIFFICULTY;
import static org.drasyl.util.LoggingUtil.sanitizeLogArg;

/**
 * This handler filters out all messages received with invalid proof of work.
 */
@Stateless
public final class InvalidProofOfWorkFilter extends SimpleInboundHandler<IntermediateEnvelope<? extends MessageLite>, Address> {
    public static final InvalidProofOfWorkFilter INSTANCE = new InvalidProofOfWorkFilter();
    private static final Logger LOG = LoggerFactory.getLogger(InvalidProofOfWorkFilter.class);

    private InvalidProofOfWorkFilter() {
        // singleton
    }

    @Override
    protected void matchedInbound(final HandlerContext ctx,
                                  final Address sender,
                                  final IntermediateEnvelope<? extends MessageLite> msg,
                                  final CompletableFuture<Void> future) {
        try {
            if (msg.isChunk() || msg.getProofOfWork().isValid(msg.getSender(), POW_DIFFICULTY)) {
                ctx.passInbound(sender, msg, future);
            }
            else {
                LOG.trace("Message with invalid proof of work dropped: '{}'", () -> sanitizeLogArg(msg));
                future.completeExceptionally(new Exception("Message with invalid proof of work dropped."));
                ReferenceCountUtil.safeRelease(msg);
            }
        }
        catch (final IOException e) {
            LOG.debug("Message {} can't be read and was dropped: '{}'", () -> sanitizeLogArg(msg), () -> e);
            future.completeExceptionally(new Exception("Message can't be read and was dropped due to the following error: ", e));
            ReferenceCountUtil.safeRelease(msg);
        }
    }
}
