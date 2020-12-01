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
package org.drasyl.loopback.handler;

import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This handler processes inbound messages addressed to the local node.
 */
public class LoopbackInboundMessageSinkHandler extends SimpleInboundHandler<ApplicationMessage, Address> {
    public static final String LOOPBACK_INBOUND_MESSAGE_SINK_HANDLER = "LOOPBACK_INBOUND_MESSAGE_SINK_HANDLER";
    private final AtomicBoolean started;

    public LoopbackInboundMessageSinkHandler(final AtomicBoolean started) {
        this.started = started;
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final ApplicationMessage msg,
                               final CompletableFuture<Void> future) {
        if (!ctx.identity().getPublicKey().equals(msg.getRecipient())) {
            future.completeExceptionally(new Exception("I'm not the recipient"));
        }
        else if (!started.get()) {
            future.completeExceptionally(new Exception("Node is not running"));
        }
        else {
            ctx.fireRead(sender, msg, future);
        }
    }
}
