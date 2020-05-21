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
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.PingMessage;
import org.drasyl.peer.connection.message.PongMessage;

import java.util.concurrent.atomic.AtomicInteger;

import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_PING_PONG;

/**
 * This handler answers automatically to {@link PingMessage}. When a {@link IdleStateHandler} is
 * registered, it's also ask periodically for a {@link PongMessage} from the peer.
 */
public class PingPongHandler extends SimpleChannelInboundHandler<Message<?>> {
    public static final String PING_PONG_HANDLER = "pingPongHandler";
    protected final short retries;
    protected final AtomicInteger counter;

    /**
     * PingPongHandler with {@code 3} retries, until channel is closed.
     */
    public PingPongHandler() {
        this((short) 3);
    }

    /**
     * PingPongHandler with {@code retries} retries, until channel is closed.
     */
    public PingPongHandler(short retries) {
        this(retries, new AtomicInteger(0));
    }

    PingPongHandler(short retries, AtomicInteger counter) {
        this.retries = retries;
        this.counter = counter;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.READER_IDLE) {
                if (counter.getAndIncrement() > retries) {
                    ctx.writeAndFlush(new ConnectionExceptionMessage(CONNECTION_ERROR_PING_PONG)).addListener(ChannelFutureListener.CLOSE);
                }
                else {
                    ctx.writeAndFlush(new PingMessage());
                }
            }
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        if (msg instanceof PingMessage) {
            ctx.writeAndFlush(new PongMessage(msg.getId()));
            ReferenceCountUtil.release(msg);
        }
        else if (msg instanceof PongMessage) {
            counter.set(0);
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }
}
