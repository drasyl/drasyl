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
package org.drasyl.peer.connection.pipeline;

import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.SimpleInboundHandler;

import java.util.concurrent.CompletableFuture;

/**
 * This handler tries to process inbound messages via TCP-based direct connection to another peers.
 */
public class DirectConnectionInboundMessageSinkHandler extends SimpleInboundHandler<Message, Event, CompressedPublicKey> {
    public static final String DIRECT_CONNECTION_INBOUND_MESSAGE_SINK_HANDLER = "DIRECT_CONNECTION_INBOUND_MESSAGE_SINK_HANDLER";
    private final PeerChannelGroup channelGroup;

    public DirectConnectionInboundMessageSinkHandler(final PeerChannelGroup channelGroup) {
        this.channelGroup = channelGroup;
    }

    @Override
    protected void matchedEventTriggered(final HandlerContext ctx,
                                         final Event event,
                                         final CompletableFuture<Void> future) {
        ctx.fireEventTriggered(event, future);
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final CompressedPublicKey sender,
                               final Message msg,
                               final CompletableFuture<Void> future) {
        channelGroup.writeAndFlush(msg.getRecipient(), msg).addListener(result -> {
            if (result.isSuccess()) {
                future.complete(null);
            }
            else {
                ctx.fireRead(sender, msg, future);
            }
        });
    }
}