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
package org.drasyl.peer.connection.server.handler;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ExceptionMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BooleanSupplier;

import static org.drasyl.peer.connection.message.ExceptionMessage.Error.ERROR_PEER_UNAVAILABLE;

/**
 * This handler acts as a channel creation guard. A new channel should not be created, if the {@code
 * isOpenSupplier} returns <code>false</code>. Used by the {@link Server} to prevent new connections
 * from being established during shutdown.
 */
public class ServerNewConnectionsGuard extends SimpleChannelInboundHandler<Message> {
    public static final String CONNECTION_GUARD = "connectionGuard";
    private static final Logger LOG = LoggerFactory.getLogger(ServerNewConnectionsGuard.class);
    private final Identity identity;
    private final BooleanSupplier acceptNewConnectionsSupplier;

    public ServerNewConnectionsGuard(final Identity identity,
                                     final BooleanSupplier acceptNewConnectionsSupplier) {
        this.identity = identity;
        this.acceptNewConnectionsSupplier = acceptNewConnectionsSupplier;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final Message msg) {
        if (acceptNewConnectionsSupplier.getAsBoolean()) {
            ReferenceCountUtil.retain(msg);
            ctx.fireChannelRead(msg);
        }
        else {
            ctx.writeAndFlush(new ExceptionMessage(identity.getPublicKey(), identity.getProofOfWork(), ERROR_PEER_UNAVAILABLE, msg.getId())).addListener(ChannelFutureListener.CLOSE);
            LOG.debug("{} blocked creation of channel {}.", getClass().getSimpleName(), ctx.channel().id());
        }
    }
}