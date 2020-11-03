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
package org.drasyl.pipeline;

import org.drasyl.event.Event;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.pipeline.address.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

import static org.drasyl.identity.IdentityManager.POW_DIFFICULTY;

/**
 * This handler filters out all messages received with invalid proof of work.
 */
public class InvalidProofOfWorkFilter extends SimpleInboundHandler<Message, Event, Address> {
    public static final String INVALID_PROOF_OF_WORK_FILTER = "INVALID_PROOF_OF_WORK_FILTER";
    private static final Logger LOG = LoggerFactory.getLogger(InvalidProofOfWorkFilter.class);

    @Override
    protected void matchedEventTriggered(final HandlerContext ctx,
                                         final Event event,
                                         final CompletableFuture<Void> future) {
        ctx.fireEventTriggered(event, future);
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final Message msg,
                               final CompletableFuture<Void> future) {
        if (msg.getProofOfWork().isValid(msg.getSender(), POW_DIFFICULTY)) {
            ctx.fireRead(sender, msg, future);
        }
        else {
            LOG.trace("Message with invalid proof of work dropped: {}", msg);
            future.completeExceptionally(new Exception("Message with invalid proof of work dropped"));
        }
    }
}