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
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.message.ResponseMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.drasyl.pipeline.Pipeline;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;

/**
 * This handler performs the client-side part of a three-way handshake to create a session. It
 * automatically requests the server for a new session. The server must confirm this request and
 * then offers the client a session. Then the client has to confirm the offered session again.
 */
@SuppressWarnings({ "java:S110" })
public abstract class ThreeWayHandshakeClientHandler<R extends RequestMessage, O extends ResponseMessage<?>> extends AbstractThreeWayHandshakeHandler {
    public static final AttributeKey<CompressedPublicKey> ATTRIBUTE_PUBLIC_KEY = AttributeKey.valueOf("publicKey");
    private final Identity identity;
    private final R requestMessage;

    protected ThreeWayHandshakeClientHandler(final Identity identity,
                                             final Duration timeout,
                                             final Pipeline pipeline,
                                             final R requestMessage) {
        super(timeout, pipeline, identity);
        this.identity = identity;
        this.requestMessage = requestMessage;
    }

    protected ThreeWayHandshakeClientHandler(final Identity identity,
                                             final Duration timeout,
                                             final Pipeline pipeline,
                                             final CompletableFuture<Void> handshakeFuture,
                                             final ScheduledFuture<?> timeoutFuture,
                                             final R requestMessage) {
        super(timeout, pipeline, handshakeFuture, timeoutFuture, identity);
        this.identity = identity;
        this.requestMessage = requestMessage;
    }

    @Override
    protected void doHandshake(final ChannelHandlerContext ctx, final Message message) {
        if (message instanceof ResponseMessage && ((ResponseMessage<?>) message).getCorrespondingId().equals(requestMessage.getId())) {
            if (message instanceof StatusMessage && ((StatusMessage) message).getCode() != STATUS_OK) {
                requestFailed(ctx, ((StatusMessage) message).getCode());
            }
            else {
                try {
                    @SuppressWarnings("unchecked") final O offerMessage = (O) message;
                    final ConnectionExceptionMessage.Error error = validateSessionOffer(offerMessage);
                    if (error == null) {
                        confirmSession(ctx, offerMessage);

                        // send confirmation
                        ctx.writeAndFlush(new StatusMessage(identity.getPublicKey(), identity.getProofOfWork(), STATUS_OK, offerMessage.getId()));
                    }
                    else {
                        rejectSession(ctx, error);
                    }
                }
                catch (final ClassCastException e) {
                    processUnexpectedMessageDuringHandshake(ctx, message);
                }
            }
        }
        else {
            processUnexpectedMessageDuringHandshake(ctx, message);
        }
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);

        startTimeoutGuard(ctx);
        requestSession(ctx);
    }

    protected void requestSession(final ChannelHandlerContext ctx) {
        if (getLogger().isTraceEnabled()) {
            getLogger().trace("[{}]: Send request message to Super Peer.", ctx.channel().id().asShortText());
        }
        ctx.writeAndFlush(requestMessage);
    }

    private void requestFailed(final ChannelHandlerContext ctx, final StatusMessage.Code code) {
        if (getLogger().isTraceEnabled()) {
            getLogger().trace("[{}]: Session request has been rejected: {}", ctx.channel().id().asShortText(), code);
        }

        timeoutFuture.cancel(true);
        handshakeFuture.completeExceptionally(new Exception(code.toString()));
    }

    /**
     * This method validates the session offered by the server and must return an {@link
     * ConnectionExceptionMessage.Error} in case of error. Otherwise <code>null</code> must be
     * returned.
     *
     * @param offerMessage the message that should be validated
     * @return {@link ConnectionExceptionMessage.Error} in case of error, otherwise
     * <code>null</code>
     */
    protected abstract ConnectionExceptionMessage.Error validateSessionOffer(O offerMessage);

    protected void confirmSession(final ChannelHandlerContext ctx, final O offerMessage) {
        if (getLogger().isTraceEnabled()) {
            getLogger().trace("[{}]: Create new Connection from Channel {}", ctx.channel().id().asShortText(), ctx.channel().id());
        }

        timeoutFuture.cancel(true);
        createConnection(ctx, offerMessage);
        handshakeFuture.complete(null);
    }

    protected abstract void createConnection(ChannelHandlerContext ctx,
                                             O offerMessage);
}