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
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.handler.SimpleChannelDuplexHandler;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.StatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.*;
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
public class NodeServerJoinGuard extends SimpleChannelDuplexHandler<Message, Message> {
    public static final String JOIN_GUARD = "nodeServerJoinGuard";
    private static final Logger LOG = LoggerFactory.getLogger(NodeServerJoinGuard.class);
    private final CompressedPublicKey publicKey;
    private final Duration timeout;
    private ScheduledFuture<?> timeoutFuture;

    public NodeServerJoinGuard(CompressedPublicKey publicKey, Duration timeout) {
        this(publicKey, timeout, null);
    }

    NodeServerJoinGuard(CompressedPublicKey publicKey, Duration timeout,
                        ScheduledFuture<?> timeoutFuture) {
        this.publicKey = publicKey;
        this.timeoutFuture = timeoutFuture;
        this.timeout = timeout;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        // schedule connection close if handshake did not take place within timeout
        timeoutFuture = ctx.executor().schedule(() -> {
            if (!timeoutFuture.isCancelled()) {
                ctx.writeAndFlush(new ConnectionExceptionMessage(CONNECTION_ERROR_HANDSHAKE)).addListener(ChannelFutureListener.CLOSE);
                LOG.debug("[{}]: Handshake did not take place successfully in {}ms. "
                        + "Connection is closed.", ctx.channel().id().asShortText(), timeout.toMillis());
            }
        }, timeout.toMillis(), MILLISECONDS);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // close connection if an error occurred before handshake
        ctx.writeAndFlush(new ConnectionExceptionMessage(CONNECTION_ERROR_INITIALIZATION)).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message request) {
        if (request instanceof JoinMessage) {
            // do handshake
            JoinMessage joinMessage = (JoinMessage) request;
            CompressedPublicKey clientPublicKey = joinMessage.getPublicKey();

            if (publicKey.equals(clientPublicKey)) {
                LOG.warn("Client has sent same public key. You can't use yourself as a Client. This would mean an endless loop. This could indicate a configuration error. Close connection.");
                ctx.writeAndFlush(new ConnectionExceptionMessage(CONNECTION_ERROR_SAME_PUBLIC_KEY)).addListener(ChannelFutureListener.CLOSE);
                ReferenceCountUtil.release(request);
            }
            else {
                timeoutFuture.cancel(true);
                ctx.fireChannelRead(request);
                ctx.pipeline().remove(JOIN_GUARD);
            }
        }
        else {
            // reject all non-join messages if handshake is not done
            ctx.writeAndFlush(new StatusMessage(STATUS_FORBIDDEN, request.getId()));
            ReferenceCountUtil.release(request);
            LOG.debug("[{}] Client is not authenticated. Inbound message was dropped: '{}'", ctx, request);
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        timeoutFuture.cancel(true);
        ctx.close(promise);
    }

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx,
                                 Message msg,
                                 ChannelPromise promise) {
        if (msg instanceof JoinMessage) {
            ctx.write(msg, promise);
        }
        else {
            // reject all non-welcome messages if handshake is not done
            ctx.writeAndFlush(new StatusMessage(STATUS_FORBIDDEN, msg.getId()));
            ReferenceCountUtil.release(msg);
            LOG.debug("[{}] Client is not authenticated. Outbound message was dropped: '{}'", ctx, msg);
        }
    }
}
