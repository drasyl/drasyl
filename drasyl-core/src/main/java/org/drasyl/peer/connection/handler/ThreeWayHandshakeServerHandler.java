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

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ExceptionMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.message.ResponseMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.drasyl.pipeline.Pipeline;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * This handler performs the server-side part of a three-way handshake to create a session. It waits
 * for a request message for a new session from the client. The request is then confirmed by sending
 * an offer message to the client. It then waits for the client to confirm the offer.
 */
@SuppressWarnings({ "java:S110" })
public abstract class ThreeWayHandshakeServerHandler<R extends RequestMessage, O extends ResponseMessage<?>> extends AbstractThreeWayHandshakeHandler {
    private R requestMessage;
    private O offerMessage;

    protected ThreeWayHandshakeServerHandler(final Duration timeout,
                                             final Pipeline pipeline,
                                             final Identity identity) {
        super(timeout, pipeline, identity);
    }

    protected ThreeWayHandshakeServerHandler(final Duration timeout,
                                             final Pipeline pipeline,
                                             final CompletableFuture<Void> handshakeFuture,
                                             final ScheduledFuture<?> timeoutFuture,
                                             final R requestMessage,
                                             final O offerMessage,
                                             final Identity identity) {
        super(timeout, pipeline, handshakeFuture, timeoutFuture, identity);
        this.requestMessage = requestMessage;
        this.offerMessage = offerMessage;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doHandshake(final ChannelHandlerContext ctx, final Message message) {
        try {
            if (message instanceof RequestMessage && this.requestMessage == null) {
                this.requestMessage = (R) message;
                final ExceptionMessage.Error error = validateSessionRequest(requestMessage);
                if (error == null) {
                    offerMessage = offerSession(ctx, requestMessage);
                    ctx.writeAndFlush(offerMessage);
                }
                else {
                    rejectSession(ctx, error);
                }
            }
            else if (message instanceof StatusMessage && offerMessage.getId().equals(((StatusMessage) message).getCorrespondingId())) {
                confirmSession(ctx);
            }
            else if (message instanceof ExceptionMessage && offerMessage.getId().equals(((ExceptionMessage) message).getCorrespondingId())) {
                final ExceptionMessage exceptionMessage = (ExceptionMessage) message;

                final String errorDescription = exceptionMessage.getError().getDescription();
                if (getLogger().isTraceEnabled()) {
                    getLogger().trace("[{}]: {}", ctx.channel().id().asShortText(), errorDescription);
                }
                timeoutFuture.cancel(true);
                handshakeFuture.completeExceptionally(new Exception(errorDescription));
            }
            else {
                processUnexpectedMessageDuringHandshake(ctx, message);
            }
        }
        catch (final ClassCastException e) {
            processUnexpectedMessageDuringHandshake(ctx, message);
        }
    }

    protected abstract ExceptionMessage.Error validateSessionRequest(R requestMessage);

    protected abstract O offerSession(ChannelHandlerContext ctx, R requestMessage);

    private void confirmSession(final ChannelHandlerContext ctx) {
        if (getLogger().isTraceEnabled()) {
            getLogger().trace("[{}]: Create new Connection from Channel {}", ctx.channel().id().asShortText(), ctx.channel().id());
        }

        timeoutFuture.cancel(true);
        createConnection(ctx, requestMessage);
        handshakeFuture.complete(null);
    }

    protected abstract void createConnection(final ChannelHandlerContext ctx,
                                             R requestMessage);
}