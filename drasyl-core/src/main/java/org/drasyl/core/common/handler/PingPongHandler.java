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

import org.drasyl.core.common.messages.RelayException;
import org.drasyl.core.common.messages.Message;
import org.drasyl.core.common.messages.Ping;
import org.drasyl.core.common.messages.Pong;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;

/**
 * This handler answers automatically to {@link Ping}. When a {@link IdleStateHandler} is registered, it's
 * also ask periodically for a {@link Pong} from the peer.
 */
public class PingPongHandler extends SimpleChannelInboundHandler<Message> {
    protected final short retries;
    protected short counter;

    PingPongHandler(short retries, short counter) {
        this.retries = retries;
        this.counter = counter;
    }

    private PingPongHandler(short retries) {
        this(retries, (short) 0);
    }

    /**
     * PingPongHandler with {@code 3} retries, until channel is closed.
     */
    public PingPongHandler() {
        this(3);
    }

    /**
     * PingPongHandler with {@code retries} retries, until channel is closed.
     */
    public PingPongHandler(int retries) {
        this((short) Math.max(1, Math.min(retries, Short.MAX_VALUE)));
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.READER_IDLE) {
                if (counter > retries) {
                    ctx.writeAndFlush(new RelayException(
                            "Max retries for ping/pong requests reached. Connection will be closed."));
                    ctx.close();
                } else {
                    ctx.writeAndFlush(new Ping());
                }
                counter++;
            }
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        if (msg instanceof Ping) {
            ctx.writeAndFlush(new Pong());
            ReferenceCountUtil.release(msg);
        } else if (msg instanceof Pong) {
            counter = 0;
        } else {
            ctx.fireChannelRead(msg);
        }
    }
}
