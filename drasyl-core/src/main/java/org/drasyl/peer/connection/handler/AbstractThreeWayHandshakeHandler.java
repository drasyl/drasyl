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
import org.drasyl.messenger.Messenger;
import org.drasyl.messenger.MessengerException;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.RelayableMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_HANDSHAKE_TIMEOUT;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_INITIALIZATION;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_FORBIDDEN;

public abstract class AbstractThreeWayHandshakeHandler extends SimpleChannelDuplexHandler<Message, Message> {
    protected final Duration timeout;
    protected final CompletableFuture<Void> handshakeFuture;
    protected final Messenger messenger;
    protected ScheduledFuture<?> timeoutFuture;

    protected AbstractThreeWayHandshakeHandler(Duration timeout, Messenger messenger) {
        this(timeout, messenger, new CompletableFuture<>(), null);
    }

    protected AbstractThreeWayHandshakeHandler(Duration timeout,
                                               Messenger messenger,
                                               CompletableFuture<Void> handshakeFuture,
                                               ScheduledFuture<?> timeoutFuture) {
        super(true, false, false);
        this.timeout = timeout;
        this.messenger = messenger;
        this.handshakeFuture = handshakeFuture;
        this.timeoutFuture = timeoutFuture;
    }

    protected void processUnexpectedMessageDuringHandshake(ChannelHandlerContext ctx,
                                                           Message message) {
        if (getLogger().isTraceEnabled()) {
            getLogger().trace("[{}] Handshake is not completed. Inbound message was rejected: '{}'", ctx.channel().id().asShortText(), message);
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
            else if (msg instanceof QuitMessage) {
                quitSession(ctx, (QuitMessage) msg);
            }
            else {
                processMessageAfterHandshake(ctx, msg);
            }
        }).addListener(future -> {
            Throwable cause = future.cause();
            if (cause != null) {
                exceptionCaught(ctx, cause);
            }
        });
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(true);
        }
        super.close(ctx, promise);
    }

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx,
                                 Message msg, ChannelPromise promise) {
        if (handshakeFuture.isDone() && !handshakeFuture.isCompletedExceptionally()) {
            ctx.write(msg, promise);
        }
        else {
            IllegalStateException exception = new IllegalStateException("Handshake is not done yet. Outbound message was dropped: '" + msg + "'");
            ReferenceCountUtil.release(msg);
            promise.setFailure(exception);
            throw exception;
        }
    }

    protected abstract void doHandshake(ChannelHandlerContext ctx, Message message);

    private void quitSession(ChannelHandlerContext ctx, QuitMessage quitMessage) {
        if (getLogger().isTraceEnabled()) {
            getLogger().trace("[{}]: received {}. Close channel for reason '{}'", ctx.channel().id().asShortText(), QuitMessage.class.getSimpleName(), quitMessage.getReason());
        }

        ctx.close();
    }

    @SuppressWarnings({ "java:S1172" })
    protected void processMessageAfterHandshake(ChannelHandlerContext ctx, Message message) {
        if (message instanceof RelayableMessage) {
            RelayableMessage relayableMessage = (RelayableMessage) message;
            try {
                messenger.send(relayableMessage);
            }
            catch (MessengerException e) {
                getLogger().trace("Unable to send Message {}: {}", relayableMessage, e.getMessage());
            }
        }
        else {
            getLogger().debug("Could not process the message {}", message);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        startTimeoutGuard(ctx);

        super.channelActive(ctx);
    }

    protected void startTimeoutGuard(ChannelHandlerContext ctx) {
        if (timeoutFuture == null) {
            // schedule connection error if handshake did not take place within timeout
            timeoutFuture = ctx.executor().schedule(() -> {
                if (!timeoutFuture.isCancelled()) {
                    rejectSession(ctx, CONNECTION_ERROR_HANDSHAKE_TIMEOUT);
                }
            }, timeout.toMillis(), MILLISECONDS);
        }
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
            if (getLogger().isWarnEnabled()) {
                getLogger().warn("[{}]: Exception during handshake occurred: ", ctx.channel().id().asShortText(), cause);
            }
            // close connection if an error occurred before handshake
            ctx.writeAndFlush(new ConnectionExceptionMessage(CONNECTION_ERROR_INITIALIZATION)).addListener(ChannelFutureListener.CLOSE);
        }
        else {
            ctx.fireExceptionCaught(cause);
        }
    }
}
