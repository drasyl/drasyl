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

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.SimpleOutboundHandler;
import org.drasyl.util.FutureUtil;

import java.util.concurrent.CompletableFuture;

import static org.drasyl.util.FutureUtil.toFuture;

/**
 * This handler sends all outbound messages to the super peer.
 */
public class SuperPeerOutboundMessageSinkHandler extends SimpleOutboundHandler<Message, CompressedPublicKey> {
    public static final String SUPER_PEER_OUTBOUND_MESSAGE_SINK_HANDLER = "SUPER_PEER_OUTBOUND_MESSAGE_SINK_HANDLER";
    private final PeerChannelGroup channelGroup;
    private final PeersManager peersManager;

    public SuperPeerOutboundMessageSinkHandler(final PeerChannelGroup channelGroup,
                                               final PeersManager peersManager) {
        this.channelGroup = channelGroup;
        this.peersManager = peersManager;
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final CompressedPublicKey recipient,
                                final Message msg,
                                final CompletableFuture<Void> future) {
        final CompressedPublicKey superPeer = peersManager.getSuperPeerKey();
        if (superPeer != null && !recipient.equals(superPeer)) {
            try {
                FutureUtil.completeOnAllOf(future, toFuture(channelGroup.writeAndFlush(superPeer, msg)));
            }
            catch (final IllegalArgumentException e2) {
                ctx.write(recipient, msg, future);
            }
        }
        else {
            ctx.write(recipient, msg, future);
        }
    }
}
