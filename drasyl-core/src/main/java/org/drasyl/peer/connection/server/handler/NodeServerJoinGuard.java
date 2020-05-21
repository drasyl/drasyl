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
package org.drasyl.peer.connection.server.handler;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.peer.connection.handler.SimpleChannelDuplexHandler;
import org.drasyl.peer.connection.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_HANDSHAKE;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_INITIALIZATION;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_FORBIDDEN;

/**
 * Acts as a guard for in- and outbound connections. A channel is only created, when a {@link
 * JoinMessage} was received. Outgoing messages are dropped unless a {@link JoinMessage} was
 * received. Every other incoming message is also dropped unless a {@link JoinMessage} was
 * received.
 * <p>
 * If a {@link JoinMessage} was not received in {@link DrasylNodeConfig#getServerHandshakeTimeout()}
 * the connection will be closed.
 * <p>
 * This handler closes the channel if an exception occurs before a {@link JoinMessage} has been
 * received.
 */
public class NodeServerJoinGuard extends SimpleChannelDuplexHandler<Message<?>, Message<?>> {
    public static final String JOIN_GUARD = "nodeServerJoinGuard";
    private static final Logger LOG = LoggerFactory.getLogger(NodeServerJoinGuard.class);
    private final long timeout;
    protected AtomicBoolean authenticated;
    private ScheduledFuture<?> timeoutFuture;

    public NodeServerJoinGuard(long timeout) {
        this(new AtomicBoolean(false), timeout, null);
    }

    NodeServerJoinGuard(AtomicBoolean authenticated,
                        long timeout,
                        ScheduledFuture<?> timeoutFuture) {
        this.timeoutFuture = timeoutFuture;
        this.authenticated = authenticated;
        this.timeout = timeout;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        // schedule connection close if join did not take place within timeout
        timeoutFuture = ctx.executor().schedule(() -> {
            if (!timeoutFuture.isCancelled() && !authenticated.get()) {
                ctx.writeAndFlush(new ConnectionExceptionMessage(CONNECTION_ERROR_HANDSHAKE)).addListener(ChannelFutureListener.CLOSE);
                LOG.debug("[{}]: Handshake did not take place successfully in {}ms. "
                        + "Connection is closed.", ctx.channel().id().asShortText(), timeout);
            }
        }, timeout, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx,
                                 Message<?> msg,
                                 ChannelPromise promise) {
        if (authenticated.get()) {
            // passthrough all outgoing messages if client is authenticated
            ctx.write(msg, promise);
        }
        else {
            if (msg instanceof UnrestrictedPassableMessage) {
                ctx.write(msg, promise);
            }
            else {
                ReferenceCountUtil.release(msg);
                // is visible to the listening futures
                throw new IllegalStateException("Client is not authenticated. Outbound message was dropped: '" + msg + "'");
            }
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message<?> request) {
        if (authenticated.get()) {
            if (request instanceof JoinMessage) {
                // reject duplicate join requests
                ctx.writeAndFlush(new StatusMessage(STATUS_FORBIDDEN, request.getId()));
                ReferenceCountUtil.release(request);
            }
            else {
                // passthrough all ingoing messages if client is authenticated
                ctx.fireChannelRead(request);
            }
        }
        else if (request instanceof JoinMessage && authenticated.compareAndSet(false, true)) {
            // do join
            timeoutFuture.cancel(true);
            ctx.fireChannelRead(request);
        }
        else {
            // reject all non-join messages if client is not authenticated
            ctx.writeAndFlush(new StatusMessage(STATUS_FORBIDDEN, request.getId()));
            ReferenceCountUtil.release(request);
            LOG.debug("[{}] Client is not authenticated. Inbound message was dropped: '{}'",
                    ctx, request);
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        timeoutFuture.cancel(true);
        ctx.close(promise);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (!authenticated.get()) {
            // close connection if an error occurred before authentication
            ctx.writeAndFlush(new ConnectionExceptionMessage(CONNECTION_ERROR_INITIALIZATION)).addListener(ChannelFutureListener.CLOSE);
        } else {
            super.exceptionCaught(ctx, cause);
        }
    }
}
