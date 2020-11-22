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

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.util.FutureUtil;

import java.util.concurrent.CompletableFuture;

import static org.drasyl.util.FutureUtil.toFuture;

/**
 * This handler sends all inbound messages to the super peer.
 */
public class SuperPeerInboundMessageSinkHandler extends SimpleInboundHandler<Message, Address> {
    public static final String SUPER_PEER_INBOUND_MESSAGE_SINK_HANDLER = "SUPER_PEER_INBOUND_MESSAGE_SINK_HANDLER";
    private final PeerChannelGroup channelGroup;

    public SuperPeerInboundMessageSinkHandler(final PeerChannelGroup channelGroup) {
        this.channelGroup = channelGroup;
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final Message msg,
                               final CompletableFuture<Void> future) {
        if (ctx.identity().getPublicKey().equals(msg.getRecipient())) {
            ctx.fireRead(sender, msg, future);
        }
        else {
            final CompressedPublicKey superPeer = ctx.peersManager().getSuperPeerKey();
            if (superPeer != null) {
                try {
                    FutureUtil.completeOnAllOf(future, toFuture(channelGroup.writeAndFlush(superPeer, msg)));
                }
                catch (final IllegalArgumentException e2) {
                    ctx.fireRead(sender, msg, future);
                }
            }
            else {
                ctx.fireRead(sender, msg, future);
            }
        }
    }
}