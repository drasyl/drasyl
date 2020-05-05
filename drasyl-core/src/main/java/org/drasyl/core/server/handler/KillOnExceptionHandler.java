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
package org.drasyl.core.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.core.common.messages.NodeServerException;
import org.drasyl.core.server.NodeServer;

/**
 * This handler closes the channel if an exception occurs during initialization stage.
 */
public class KillOnExceptionHandler extends ChannelInboundHandlerAdapter {
    public static final String KILL_SWITCH = "killSwitch";
    private final NodeServer server;

    public KillOnExceptionHandler(NodeServer server) {
        this.server = server;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.writeAndFlush(new NodeServerException(
                "Exception occurred during initialization stage. The connection will shut down."));
        ctx.close();
    }
}
