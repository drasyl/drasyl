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
import org.drasyl.pipeline.handler.InboundMessageFilter;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;

import static org.drasyl.util.LoggingUtil.sanitizeLogArg;

/**
 * This handler filters out all messages received from other networks.
 */
@SuppressWarnings("java:S110")
@Stateless
public final class OtherNetworkFilter extends InboundMessageFilter<RemoteEnvelope<? extends MessageLite>, Address> {
    public static final OtherNetworkFilter INSTANCE = new OtherNetworkFilter();
    private static final Logger LOG = LoggerFactory.getLogger(OtherNetworkFilter.class);

    private OtherNetworkFilter() {
        // singleton
    }

    @Override
    protected boolean accept(final HandlerContext ctx,
                             final Address sender,
                             final RemoteEnvelope<? extends MessageLite> msg) throws Exception {
        return msg.isChunk() || ctx.config().getNetworkId() == msg.getNetworkId();
    }

    @Override
    protected void messageRejected(final HandlerContext ctx,
                                   final Address sender,
                                   final RemoteEnvelope<? extends MessageLite> msg,
                                   final CompletableFuture<Void> future) {
        LOG.trace("Message from other network dropped: {}", () -> sanitizeLogArg(msg));
        future.completeExceptionally(new Exception("Message from other network dropped"));
    }
}
