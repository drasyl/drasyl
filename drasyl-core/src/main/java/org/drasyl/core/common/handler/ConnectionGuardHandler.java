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
package org.drasyl.core.common.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.core.common.messages.IMessage;
import org.drasyl.core.common.messages.Reject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BooleanSupplier;

/**
 * This handler acts as a channel creation guard. A new channel should not be created, if the {@code
 * isOpenSupplier} returns false.
 */
public class ConnectionGuardHandler extends SimpleChannelInboundHandler<IMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionGuardHandler.class);
    public static final String CONNECTION_GUARD = "connectionGuard";
    private final BooleanSupplier isOpenSupplier;

    public ConnectionGuardHandler(BooleanSupplier isOpenSupplier) {
        this.isOpenSupplier = isOpenSupplier;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IMessage msg) {
        if (isOpenSupplier.getAsBoolean()) {
            ctx.fireChannelRead(msg);
        }
        else {
            try {
                ctx.writeAndFlush(new Reject());
                ctx.close();
                LOG.debug("ConnectionGuard blocked creation of channel {}.", ctx.channel().id());
            }
            finally {
                ReferenceCountUtil.release(msg);
            }
        }
    }
}
