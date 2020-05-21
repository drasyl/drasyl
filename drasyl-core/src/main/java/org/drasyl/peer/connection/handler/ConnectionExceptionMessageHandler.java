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

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fires a channel wide close event, when a {@link ConnectionExceptionMessage} received.
 */
@Sharable
public class ConnectionExceptionMessageHandler extends SimpleChannelInboundHandler<ConnectionExceptionMessage> {
    public static final ConnectionExceptionMessageHandler INSTANCE = new ConnectionExceptionMessageHandler();
    public static final String EXCEPTION_MESSAGE_HANDLER = "connectionExceptionMessageHandler";
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionExceptionMessageHandler.class);

    private ConnectionExceptionMessageHandler() {
        // singleton
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ConnectionExceptionMessage msg) {
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("[{}]: received {}. Close channel", ctx.channel().id().asShortText(), msg);
            }
            ctx.close();
        }
        finally {
            ReferenceCountUtil.release(msg);
        }
    }
}
