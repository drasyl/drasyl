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
