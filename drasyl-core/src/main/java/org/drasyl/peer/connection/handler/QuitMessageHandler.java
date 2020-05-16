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

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;

/**
 * Fires a channel wide close event, when a {@link QuitMessage} received.
 */
@Sharable
public class QuitMessageHandler extends SimpleChannelInboundHandler<QuitMessage> {
    public static final QuitMessageHandler INSTANCE = new QuitMessageHandler();
    public static final String QUIT_MESSAGE_HANDLER = "quitMessageHandler";
    private static final Logger LOG = LoggerFactory.getLogger(QuitMessageHandler.class);

    private QuitMessageHandler() {
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, QuitMessage msg) throws Exception {
        try {
            LOG.debug("[{}]: received {}. Close channel", ctx.channel().id().asShortText(), msg);
            ctx.writeAndFlush(new StatusMessage(STATUS_OK, msg.getId())).addListener(ChannelFutureListener.CLOSE);
        }
        finally {
            ReferenceCountUtil.release(msg);
        }
    }
}
