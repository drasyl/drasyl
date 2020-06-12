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
package org.drasyl.peer.connection.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.peer.connection.message.RelayableMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This handler ensures that {@link RelayableMessage}s do not infinitely circulate in the network.
 * It increments the hop counter of each outgoing message. If the limit of hops is reached, the
 * message is discarded. Otherwise the message can pass.
 */
public class RelayableMessageGuard extends SimpleChannelOutboundHandler<RelayableMessage> {
    public static final String HOP_COUNT_GUARD = "relayableMessageGuard";
    private static final Logger LOG = LoggerFactory.getLogger(RelayableMessageGuard.class);
    private final short messageHopLimit;

    public RelayableMessageGuard(short messageHopLimit) {
        this.messageHopLimit = messageHopLimit;
    }

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx,
                                 RelayableMessage msg, ChannelPromise promise) {
        if (msg.getHopCount() < messageHopLimit) {
            // route message to next hop (node)
            msg.incrementHopCount();
            ctx.write(msg, promise);
        }
        else {
            // too many hops, discard message
            if (LOG.isDebugEnabled()) {
                LOG.debug("[{}]: Hop Count limit has been reached. End of lifespan of message has been reached. Discard message '{}'", ctx.channel().id().asShortText(), msg);
            }
            ReferenceCountUtil.release(msg);
            promise.setSuccess();
        }
    }
}
