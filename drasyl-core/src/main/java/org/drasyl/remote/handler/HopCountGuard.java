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
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.remote.protocol.AddressedIntermediateEnvelope;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.util.LoggingUtil.sanitizeLogArg;

/**
 * This handler ensures that {@link IntermediateEnvelope<MessageLite>}s do not infinitely circulate
 * in the network. It increments the hop counter of each outgoing message. If the limit of hops is
 * reached, the message is discarded. Otherwise the message can pass.
 */
@Stateless
public final class HopCountGuard extends SimpleOutboundHandler<AddressedIntermediateEnvelope<MessageLite>, Address> {
    public static final HopCountGuard INSTANCE = new HopCountGuard();
    public static final String HOP_COUNT_GUARD = "HOP_COUNT_GUARD";
    private static final Logger LOG = LoggerFactory.getLogger(HopCountGuard.class);

    private HopCountGuard() {
        // singleton
    }

    @Override
    protected void matchedOutbound(final HandlerContext ctx,
                                   final Address recipient,
                                   final AddressedIntermediateEnvelope<MessageLite> msg,
                                   final CompletableFuture<Void> future) {
        try {
            if (msg.getContent().getHopCount() < ctx.config().getRemoteMessageHopLimit()) {
                // route message to next hop (node)
                msg.getContent().incrementHopCount();

                ctx.passOutbound(recipient, msg, future);
            }
            else {
                // too many hops, discard message
                LOG.debug("Hop Count limit has been reached. End of lifespan of message has been reached. Discard message '{}'", () -> sanitizeLogArg(msg));
                ReferenceCountUtil.safeRelease(msg);
                future.completeExceptionally(new Exception("Hop Count limit has been reached. End of lifespan of message has been reached. Discard message."));
            }
        }
        catch (final IOException e) {
            LOG.error("Unable to read/increment hop count from message. Discard message '{}'", () -> sanitizeLogArg(msg), () -> e);
            ReferenceCountUtil.safeRelease(msg);
            future.completeExceptionally(new Exception("Unable to read/increment hop count from message.", e));
        }
    }
}
