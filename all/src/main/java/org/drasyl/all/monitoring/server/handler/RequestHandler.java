/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.all.monitoring.server.handler;

import org.drasyl.all.monitoring.Aggregator;
import org.drasyl.all.monitoring.models.WebsocketRequest;
import org.drasyl.all.monitoring.models.WebsocketResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class RequestHandler extends SimpleChannelInboundHandler<WebsocketRequest> {
    private final Aggregator aggregator;

    public RequestHandler(Aggregator aggregator) {
        this.aggregator = aggregator;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebsocketRequest websocketRequest) throws Exception {
        ctx.channel().writeAndFlush(new WebsocketResponse(aggregator.getSystemStatus()));
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelRegistered();
    }
}
