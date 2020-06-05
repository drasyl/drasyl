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
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.connection.AbstractNettyConnection;
import org.drasyl.peer.connection.ConnectionsManager;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.message.ResponseMessage;
import org.drasyl.peer.connection.message.StatusMessage;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;

/**
 * This handler performs the client-side part of a three-way handshake to create a session. It
 * automatically requests the server for a new session. The server must confirm this request and
 * then offers the client a session. Then the client has to confirm the offered session again.
 */
public abstract class AbstractThreeWayHandshakeClientHandler<R extends RequestMessage, O extends ResponseMessage<?>> extends AbstractThreeWayHandshakeHandler {
    private final R requestMessage;

    protected AbstractThreeWayHandshakeClientHandler(ConnectionsManager connectionsManager,
                                                     Duration timeout,
                                                     Messenger messenger,
                                                     R requestMessage) {
        super(connectionsManager, timeout, messenger);
        this.requestMessage = requestMessage;
    }

    protected AbstractThreeWayHandshakeClientHandler(ConnectionsManager connectionsManager,
                                                     Duration timeout,
                                                     Messenger messenger,
                                                     CompletableFuture<Void> handshakeFuture,
                                                     AbstractNettyConnection connection,
                                                     ScheduledFuture<?> timeoutFuture,
                                                     R requestMessage) {
        super(connectionsManager, timeout, messenger, handshakeFuture, connection, timeoutFuture);
        this.requestMessage = requestMessage;
    }

    @Override
    protected void doHandshake(ChannelHandlerContext ctx, Message message) {
        try {
            O offerMessage = (O) message;
            ConnectionExceptionMessage.Error error = validateSessionOffer(offerMessage);
            if (error == null) {
                confirmSession(ctx, offerMessage);

                // send confirmation
                ctx.writeAndFlush(new StatusMessage(STATUS_OK, offerMessage.getId()));
            }
            else {
                rejectSession(ctx, error);
            }
        }
        catch (ClassCastException e) {
            processUnexpectedMessageDuringHandshake(ctx, message);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        requestSession(ctx);

        super.channelActive(ctx);
    }

    protected void requestSession(ChannelHandlerContext ctx) {
        if (getLogger().isTraceEnabled()) {
            getLogger().trace("[{}]: Send request message to Super Peer.", ctx.channel().id().asShortText());
        }
        ctx.writeAndFlush(requestMessage);
    }

    /**
     * This method validates the session offered by the server and must return an {@link
     * ConnectionExceptionMessage.Error} in case of error. Otherwise <code>null</code> must be
     * returned.
     *
     * @param offerMessage
     * @return
     */
    protected abstract ConnectionExceptionMessage.Error validateSessionOffer(O offerMessage);

    protected void confirmSession(ChannelHandlerContext ctx, O offerMessage) {
        if (getLogger().isTraceEnabled()) {
            getLogger().trace("[{}]: Create new Connection from Channel {}", ctx.channel().id().asShortText(), ctx.channel().id());
        }

        timeoutFuture.cancel(true);
        connection = createConnection(ctx, offerMessage);
        handshakeFuture.complete(null);
    }

    protected abstract AbstractNettyConnection createConnection(final ChannelHandlerContext ctx,
                                                                O offerMessage);
}
