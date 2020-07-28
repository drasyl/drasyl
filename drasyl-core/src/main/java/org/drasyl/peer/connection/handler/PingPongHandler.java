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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.PingMessage;
import org.drasyl.peer.connection.message.PongMessage;

import java.util.concurrent.atomic.AtomicInteger;

import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_PING_PONG;

/**
 * This handler acts as a health check for a connection. It periodically sends {@link PingMessage}s,
 * which must be answered with a {@link PongMessage}. When a configured threshold of messages is not
 * answered, the connection is considered unhealthy and is closed.
 */
public class PingPongHandler extends SimpleChannelInboundHandler<Message> {
    public static final String PING_PONG_HANDLER = "pingPongHandler";
    protected final short maxRetries;
    protected final AtomicInteger retries;

    /**
     * PingPongHandler with {@code 3} retries, until channel is closed.
     */
    public PingPongHandler() {
        this((short) 3);
    }

    /**
     * PingPongHandler with {@code retries} retries, until channel is closed.
     */
    public PingPongHandler(short maxRetries) {
        this(maxRetries, new AtomicInteger(0));
    }

    PingPongHandler(short maxRetries, AtomicInteger retries) {
        this.maxRetries = maxRetries;
        this.retries = retries;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);

        // only send pings if channel is idle
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.READER_IDLE) {
                if (retries.getAndIncrement() > maxRetries) {
                    // threshold reached, mark connection as unhealthy and close connection
                    ctx.writeAndFlush(new ConnectionExceptionMessage(CONNECTION_ERROR_PING_PONG)).addListener(ChannelFutureListener.CLOSE);
                }
                else {
                    // send (next) ping
                    ctx.writeAndFlush(new PingMessage());
                }
            }
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        if (msg instanceof PingMessage) {
            // reply to received ping with pong message
            ctx.writeAndFlush(new PongMessage(msg.getId()));
        }
        else if (msg instanceof PongMessage) {
            // pong received, reset retries counter
            retries.set(0);
        }
        else {
            // passthroughs all other messages
            ctx.fireChannelRead(msg);
        }
    }
}
