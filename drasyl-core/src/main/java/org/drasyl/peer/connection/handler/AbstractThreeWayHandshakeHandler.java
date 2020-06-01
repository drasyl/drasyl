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
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.peer.connection.AbstractNettyConnection;
import org.drasyl.peer.connection.ConnectionsManager;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.StatusMessage;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_HANDSHAKE_TIMEOUT;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_INITIALIZATION;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_FORBIDDEN;

public abstract class AbstractThreeWayHandshakeHandler extends SimpleChannelDuplexHandler<Message, Message> {
    protected final ConnectionsManager connectionsManager;
    protected final Duration timeout;
    protected final CompletableFuture<Void> handshakeFuture;
    protected AbstractNettyConnection connection;
    protected ScheduledFuture<?> timeoutFuture;

    protected AbstractThreeWayHandshakeHandler(ConnectionsManager connectionsManager,
                                               Duration timeout) {
        this(connectionsManager, timeout, new CompletableFuture<>(), null, null);
    }

    protected AbstractThreeWayHandshakeHandler(ConnectionsManager connectionsManager,
                                               Duration timeout,
                                               CompletableFuture<Void> handshakeFuture,
                                               AbstractNettyConnection connection,
                                               ScheduledFuture<?> timeoutFuture) {
        this.connectionsManager = connectionsManager;
        this.timeout = timeout;
        this.handshakeFuture = handshakeFuture;
        this.connection = connection;
        this.timeoutFuture = timeoutFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        ctx.channel().closeFuture().addListener(future -> {
            if (connection != null && !connection.isClosed().isDone()) {
                connectionsManager.removeClosingConnection(connection);
            }
        });
    }

    protected void processUnexpectedMessageDuringHandshake(ChannelHandlerContext ctx,
                                                           Message message) {
        if (getLogger().isTraceEnabled()) {
            getLogger().trace("[{}] Handshake is not completed. Inbound message was rejected: '{}'", ctx, message);
        }
        // reject all non-request messages if handshake is not done
        ctx.writeAndFlush(new StatusMessage(STATUS_FORBIDDEN, message.getId()));
    }

    protected abstract Logger getLogger();

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Message msg) {
        ctx.executor().submit(() -> {
            if (!handshakeFuture.isDone()) {
                doHandshake(ctx, msg);
            }
            else {
                processMessageAfterHandshake(connection, msg);
            }
        });
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(true);
        }
        ctx.close(promise);
    }

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx,
                                 Message msg, ChannelPromise promise) {
        if (handshakeFuture.isDone() && !handshakeFuture.isCompletedExceptionally()) {
            ctx.write(msg, promise);
        }
        else {
            ReferenceCountUtil.release(msg);
            throw new IllegalStateException("Handshake is not done yet. Outbound message was dropped: '" + msg + "'");
        }
    }

    protected abstract void doHandshake(ChannelHandlerContext ctx, Message message);

    protected abstract void processMessageAfterHandshake(AbstractNettyConnection connection,
                                                         Message msg);

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        startTimeoutGuard(ctx);

        super.channelActive(ctx);
    }

    protected void startTimeoutGuard(ChannelHandlerContext ctx) {
        // schedule connection error if handshake did not take place within timeout
        timeoutFuture = ctx.executor().schedule(() -> {
            if (!timeoutFuture.isCancelled()) {
                rejectSession(ctx, CONNECTION_ERROR_HANDSHAKE_TIMEOUT);
            }
        }, timeout.toMillis(), MILLISECONDS);
    }

    protected void rejectSession(ChannelHandlerContext ctx,
                                 ConnectionExceptionMessage.Error error) {
        String errorDescription = error.getDescription();
        if (getLogger().isTraceEnabled()) {
            getLogger().trace("[{}]: {}", ctx.channel().id().asShortText(), errorDescription);
        }
        timeoutFuture.cancel(true);
        handshakeFuture.completeExceptionally(new Exception(errorDescription));
        ctx.writeAndFlush(new ConnectionExceptionMessage(error)).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!handshakeFuture.isDone()) {
            getLogger().warn("Exception during handshake occured: ", cause);
            // close connection if an error occurred before handshake
            ctx.writeAndFlush(new ConnectionExceptionMessage(CONNECTION_ERROR_INITIALIZATION)).addListener(ChannelFutureListener.CLOSE);
        }
    }

    public CompletableFuture<Void> handshakeFuture() {
        return handshakeFuture;
    }
}
