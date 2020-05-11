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
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.core.common.handler.SimpleChannelDuplexHandler;
import org.drasyl.core.common.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Acts as a guard for in- and outbound connections. A session is only created, when a {@link JoinMessage}
 * was received. Outgoing messages are dropped unless a {@link JoinMessage} was received. Every other
 * incoming message is also dropped unless a {@link JoinMessage} was received.
 * <p>
 * If a {@link JoinMessage} was not received in {@link org.drasyl.core.node.DrasylNodeConfig#getServerHandshakeTimeout()}
 * the connection will be closed.
 */
public class JoinHandler extends SimpleChannelDuplexHandler<Message, Message> {
    private static final Logger LOG = LoggerFactory.getLogger(JoinHandler.class);
    public static final String JOIN_GUARD = "joinGuard";
    protected volatile AtomicBoolean authenticated;
    private ScheduledFuture<?> timeoutFuture;
    private final long timeout;

    public JoinHandler(long timeout) {
        this(new AtomicBoolean(false), timeout, null);
    }

    JoinHandler(AtomicBoolean authenticated, long timeout, ScheduledFuture<?> timeoutFuture) {
        this.timeoutFuture = timeoutFuture;
        this.authenticated = authenticated;
        this.timeout = timeout;
    }

    /*
     * Adds a runnable to the channel executor to emit a channel close event, when timeout is reached.
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        timeoutFuture = ctx.executor().schedule(() -> {
            if (!timeoutFuture.isCancelled() && !authenticated.get()) {
                ctx.writeAndFlush(new NodeServerExceptionMessage("Handshake did not take place successfully in " + timeout + " ms. " +
                        "Connection is closed."));
                ctx.close();
                LOG.debug("{} Handshake did not take place successfully in {} ms. "
                        + "Connection is closed.", ctx.channel().id(), timeout);
            }
        }, timeout, TimeUnit.MILLISECONDS);

        ctx.fireChannelActive();
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        timeoutFuture.cancel(true);
        ctx.close(promise);
    }

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx, Message msg) throws Exception {
        if (authenticated.get()) {
            ctx.write(msg);
        }
        else {
            if (msg instanceof UnrestrictedPassableMessage) {
                ctx.write(msg);
            }
            else {
                ReferenceCountUtil.release(msg);
                // is visible to the listening futures
                throw new IllegalStateException("Client is not authenticated. Outbound message was dropped: '" + msg + "'");
            }
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message request) throws Exception {
        if (authenticated.get()) {
            if (request instanceof JoinMessage) {
                ctx.writeAndFlush(new ResponseMessage<>(new NodeServerExceptionMessage("This client has already an open "
                        + "session with this node server. No need to authenticate twice."), request.getId()));
                ReferenceCountUtil.release(request);
            }
            else {
                ctx.fireChannelRead(request);
            }
        }
        else if (request instanceof JoinMessage && authenticated.compareAndSet(false, true)) {
            timeoutFuture.cancel(true);
            ctx.fireChannelRead(request);
        }
        else {
            ctx.writeAndFlush(new ResponseMessage<>(StatusMessage.FORBIDDEN, request.getId()));
            ReferenceCountUtil.release(request);
            LOG.debug("[{}] Client is not authenticated. Inbound message was dropped: '{}'",
                    ctx, request);
        }
    }
}
