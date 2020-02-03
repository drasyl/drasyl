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

import org.drasyl.all.handler.SimpleChannelDuplexHandler;
import org.drasyl.all.monitoring.models.WebsocketRequest;
import org.drasyl.all.monitoring.models.WebsocketResponse;
import io.netty.channel.*;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthHandler extends SimpleChannelDuplexHandler<WebsocketRequest, WebsocketResponse> {
    private static final Logger LOG = LoggerFactory.getLogger(AuthHandler.class);
    private final String token;
    private boolean authenticated;

    public AuthHandler(String token) {
        super(false, false);
        this.token = token;
        this.authenticated = false;
    }

    AuthHandler(String token, boolean authenticated) {
        super(false, false);
        this.token = token;
        this.authenticated = authenticated;
    }

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx, WebsocketResponse msg) throws Exception {
        if (authenticated) {
            ctx.write(msg);
        } else {
            ReferenceCountUtil.release(msg);
            // is visible to the listening futures
            throw new IllegalStateException("Client is not authenticated. Outbound message was dropped.");
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebsocketRequest websocketRequest) throws Exception {
        if (authenticated) {
            ctx.fireChannelRead(websocketRequest);
        } else if (websocketRequest.getToken().equals(token)) {
            authenticated = true;
            ctx.fireChannelRead(websocketRequest);
        } else {
            ReferenceCountUtil.release(websocketRequest);
            LOG.debug("[{}] Client is not authenticated. Inbound message was dropped.",
                    ctx);
        }
    }
}
