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
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ErrorMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.RelayableMessage;
import org.drasyl.pipeline.Pipeline;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.peer.connection.message.ErrorMessage.Error.ERROR_INITIALIZATION;
import static org.drasyl.peer.connection.message.ErrorMessage.Error.ERROR_UNEXPECTED_MESSAGE;

abstract class AbstractThreeWayHandshakeHandler extends SimpleChannelDuplexHandler<Message, Message> {
    protected final Duration timeout;
    protected final CompletableFuture<Void> handshakeFuture;
    protected final Pipeline pipeline;
    protected final Identity identity;
    protected ScheduledFuture<?> timeoutFuture;

    protected AbstractThreeWayHandshakeHandler(final Duration timeout,
                                               final Pipeline pipeline,
                                               final Identity identity) {
        this(timeout, pipeline, new CompletableFuture<>(), null, identity);
    }

    protected AbstractThreeWayHandshakeHandler(final Duration timeout,
                                               final Pipeline pipeline,
                                               final CompletableFuture<Void> handshakeFuture,
                                               final ScheduledFuture<?> timeoutFuture,
                                               final Identity identity) {
        super(true, false, false);
        this.timeout = timeout;
        this.pipeline = pipeline;
        this.handshakeFuture = handshakeFuture;
        this.timeoutFuture = timeoutFuture;
        this.identity = identity;
    }

    protected void processUnexpectedMessageDuringHandshake(final ChannelHandlerContext ctx,
                                                           final Message message) {
        if (getLogger().isTraceEnabled()) {
            getLogger().trace("[{}] Handshake is not completed. Inbound message was rejected: '{}'", ctx.channel().id().asShortText(), message);
        }
        // reject all non-request messages if handshake is not done
        ctx.writeAndFlush(new ErrorMessage(identity.getPublicKey(), identity.getProofOfWork(), message.getSender(), ERROR_UNEXPECTED_MESSAGE, message.getId()));
    }

    protected abstract Logger getLogger();

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final Message msg) {
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
            final Throwable cause = future.cause();
            if (cause != null) {
                exceptionCaught(ctx, cause);
            }
        });
    }

    @Override
    public void close(final ChannelHandlerContext ctx,
                      final ChannelPromise promise) throws Exception {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(true);
        }
        super.close(ctx, promise);
    }

    @Override
    protected void channelWrite0(final ChannelHandlerContext ctx,
                                 final Message msg, final ChannelPromise promise) {
        if (handshakeFuture.isDone() && !handshakeFuture.isCompletedExceptionally()) {
            ctx.write(msg, promise);
        }
        else {
            final IllegalStateException exception = new IllegalStateException("Handshake is not done yet. Outbound message was dropped: '" + msg + "'");
            ReferenceCountUtil.release(msg);
            promise.setFailure(exception);
            throw exception;
        }
    }

    protected abstract void doHandshake(ChannelHandlerContext ctx, Message message);

    private void quitSession(final ChannelHandlerContext ctx, final QuitMessage quitMessage) {
        if (getLogger().isTraceEnabled()) {
            getLogger().trace("[{}]: received {}. Close channel for reason '{}'", ctx.channel().id().asShortText(), QuitMessage.class.getSimpleName(), quitMessage.getReason());
        }

        ctx.close();
    }

    @SuppressWarnings({ "java:S1172" })
    protected void processMessageAfterHandshake(final ChannelHandlerContext ctx,
                                                final Message message) {
        if (message instanceof RelayableMessage) {
            final RelayableMessage relayableMessage = (RelayableMessage) message;
            pipeline.processOutbound(relayableMessage.getRecipient(), relayableMessage).whenComplete((done, e) -> {
                if (e != null) {
                    getLogger().trace("Unable to send Message {}: {}", relayableMessage, e.getMessage());
                }
            });
        }
        else {
            getLogger().debug("Could not process the message {}", message);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        startTimeoutGuard(ctx);

        super.channelActive(ctx);
    }

    protected void startTimeoutGuard(final ChannelHandlerContext ctx) {
        if (timeoutFuture == null) {
            // schedule connection error if handshake did not take place within timeout
            timeoutFuture = ctx.executor().schedule(() -> {
                if (!timeoutFuture.isCancelled()) {
                    rejectSession(ctx, "Handshake did not take place within timeout.");
                    ctx.close();
                }
            }, timeout.toMillis(), MILLISECONDS);
        }
    }

    protected void rejectSession(final ChannelHandlerContext ctx,
                                 final String error) {
        if (getLogger().isTraceEnabled()) {
            getLogger().trace("[{}]: {}", ctx.channel().id().asShortText(), error);
        }
        timeoutFuture.cancel(true);
        handshakeFuture.completeExceptionally(new Exception(error));
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        if (!handshakeFuture.isDone()) {
            if (getLogger().isWarnEnabled()) {
                getLogger().info("[{}]: Exception during handshake occurred: {}", ctx.channel().id().asShortText(), cause.getMessage());
            }
            // close connection if an error occurred before handshake
            ctx.writeAndFlush(new ErrorMessage(identity.getPublicKey(), identity.getProofOfWork(), ERROR_INITIALIZATION)).addListener(ChannelFutureListener.CLOSE);
        }
        else {
            ctx.fireExceptionCaught(cause);
        }
    }
}