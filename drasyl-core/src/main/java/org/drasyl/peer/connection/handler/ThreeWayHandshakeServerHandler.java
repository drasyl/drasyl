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
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ErrorMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.message.ResponseMessage;
import org.drasyl.peer.connection.message.SuccessMessage;
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
                                             final int networkId,
                                             final Identity identity) {
        super(timeout, pipeline, networkId, identity);
    }

    @SuppressWarnings({ "java:S107" })
    protected ThreeWayHandshakeServerHandler(final Duration timeout,
                                             final Pipeline pipeline,
                                             final CompletableFuture<Void> handshakeFuture,
                                             final ScheduledFuture<?> timeoutFuture,
                                             final R requestMessage,
                                             final O offerMessage,
                                             final int networkId,
                                             final Identity identity) {
        super(timeout, pipeline, handshakeFuture, timeoutFuture, networkId, identity);
        this.requestMessage = requestMessage;
        this.offerMessage = offerMessage;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doHandshake(final ChannelHandlerContext ctx, final Message message) {
        try {
            if (message instanceof RequestMessage && this.requestMessage == null) {
                this.requestMessage = (R) message;
                final ErrorMessage.Error error = validateSessionRequest(requestMessage);
                if (error == null) {
                    offerMessage = offerSession(ctx, requestMessage);
                    ctx.writeAndFlush(offerMessage);
                }
                else {
                    rejectSession(ctx, error.getDescription());
                    ctx.writeAndFlush(new ErrorMessage(networkId, identity.getPublicKey(), identity.getProofOfWork(), requestMessage.getSender(), error, requestMessage.getId())).addListener(ChannelFutureListener.CLOSE);
                }
            }
            else if (message instanceof SuccessMessage && offerMessage.getId().equals(((SuccessMessage) message).getCorrespondingId())) {
                confirmSession(ctx);
            }
            else if (message instanceof ErrorMessage && offerMessage.getId().equals(((ErrorMessage) message).getCorrespondingId())) {
                final ErrorMessage errorMessage = (ErrorMessage) message;

                final String errorDescription = errorMessage.getError().getDescription();
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

    protected abstract ErrorMessage.Error validateSessionRequest(R requestMessage);

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