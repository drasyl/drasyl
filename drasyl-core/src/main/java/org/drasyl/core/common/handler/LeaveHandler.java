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

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.core.common.message.LeaveMessage;
import org.drasyl.core.common.message.StatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.drasyl.core.common.message.StatusMessage.Code.STATUS_OK;

/**
 * Fires a channel wide close event, when a {@link LeaveMessage} received.
 */
@Sharable
public class LeaveHandler extends SimpleChannelInboundHandler<LeaveMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(LeaveHandler.class);
    public static final LeaveHandler INSTANCE = new LeaveHandler();
    public static final String LEAVE_HANDLER = "leaveHandler";

    private LeaveHandler() {
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, LeaveMessage msg) throws Exception {
        try {
            ctx.writeAndFlush(new StatusMessage(STATUS_OK, msg.getId())).addListener(ChannelFutureListener.CLOSE);
            LOG.debug("{} received LeaveMessage. Fire channel close.", ctx.channel().id());
        }
        finally {
            ReferenceCountUtil.release(msg);
        }
    }
}
