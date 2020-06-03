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
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.peer.connection.AbstractNettyConnection;
import org.drasyl.peer.connection.ConnectionsManager;
import org.drasyl.peer.connection.message.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_HANDSHAKE_REJECTED;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;

/**
 * This handler performs the server-side part of a three-way handshake to create a session. It waits
 * for a request message for a new session from the client. The request is then confirmed by sending
 * an offer message to the client. It then waits for the client to confirm the offer.
 */
public abstract class AbstractThreeWayHandshakeServerHandler<R extends RequestMessage, O extends ResponseMessage<?>> extends AbstractThreeWayHandshakeHandler {
    private R requestMessage;

    protected AbstractThreeWayHandshakeServerHandler(ConnectionsManager connectionsManager,
                                                     Duration timeout) {
        super(connectionsManager, timeout);
    }

    protected AbstractThreeWayHandshakeServerHandler(ConnectionsManager connectionsManager,
                                                     Duration timeout,
                                                     CompletableFuture<Void> handshakeFuture,
                                                     AbstractNettyConnection connection,
                                                     ScheduledFuture<?> timeoutFuture,
                                                     R requestMessage) {
        super(connectionsManager, timeout, handshakeFuture, connection, timeoutFuture);
        this.requestMessage = requestMessage;
    }

    @Override
    protected void doHandshake(ChannelHandlerContext ctx, Message message) {
        try {
            if (message instanceof RequestMessage && this.requestMessage == null) {
                this.requestMessage = (R) message;
                ConnectionExceptionMessage.Error error = validateSessionRequest(requestMessage);
                if (error == null) {
                    O offerMessage = offerSession(ctx, requestMessage);
                    ctx.writeAndFlush(offerMessage);
                }
                else {
                    rejectSession(ctx, error);
                }
            }
            else if (message instanceof StatusMessage) {
                StatusMessage confirmMessage = (StatusMessage) message;

                if (confirmMessage.getCode() == STATUS_OK) {
                    confirmSession(ctx);
                }
                else {
                    rejectSession(ctx, CONNECTION_ERROR_HANDSHAKE_REJECTED);
                }
            }
            else {
                processUnexpectedMessageDuringHandshake(ctx, message);
            }
        }
        catch (ClassCastException e) {
            processUnexpectedMessageDuringHandshake(ctx, message);
        }
    }

    protected abstract ConnectionExceptionMessage.Error validateSessionRequest(R requestMessage);

    protected abstract O offerSession(ChannelHandlerContext ctx, R requestMessage);

    private void confirmSession(ChannelHandlerContext ctx) {
        if (getLogger().isTraceEnabled()) {
            getLogger().trace("[{}]: Create new Connection from Channel {}", ctx.channel().id().asShortText(), ctx.channel().id());
        }

        timeoutFuture.cancel(true);
        connection = createConnection(ctx, requestMessage);
        handshakeFuture.complete(null);
    }

    protected abstract AbstractNettyConnection createConnection(final ChannelHandlerContext ctx,
                                                                R requestMessage);
}
