/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.core.common.handler;

import org.drasyl.core.common.messages.Leave;
import org.drasyl.core.common.messages.Response;
import org.drasyl.core.common.messages.Status;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fires a channel wide close event, when a {@link Leave} received.
 */
@Sharable
public class LeaveHandler extends SimpleChannelInboundHandler<Leave> {
    private static final Logger LOG = LoggerFactory.getLogger(LeaveHandler.class);
    public static final LeaveHandler INSTANCE = new LeaveHandler();

    private LeaveHandler() {
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Leave msg) throws Exception {
        ctx.writeAndFlush(new Response(Status.OK, msg.getMessageID()));
        ReferenceCountUtil.release(msg);
        ctx.channel().close();
        LOG.debug("{} received LeaveMessage. Fire channel close.", ctx.channel().id());
    }
}
